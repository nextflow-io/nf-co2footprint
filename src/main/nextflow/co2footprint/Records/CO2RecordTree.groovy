package nextflow.co2footprint.Records
/**
 * A tree structure for CO2Records for collecting workflow results. The tree nodes are linked with a parent-children
 * relationship.
 */
class CO2RecordTree {
    String name
    final Map metaData
    CO2Record co2Record

    CO2RecordTree parent
    final List<CO2RecordTree> children

    /**
     * Create a new RecordTree node in the hierarchical metrics structure.
     *
     * Each node represents one element in the workflow hierarchy (e.g. workflow, process, or task).
     *
     * @param name       identifier of the node (e.g. run name, process name, or task ID)
     * @param metaData   metadata of the node (e.g. level: 'workflow' | 'process' | 'task')
     * @param co2Record      optional CO2Record containing the metrics for this node
     * @param parent     parent node in the hierarchy (null for the root)
     * @param children   list of child nodes (empty list by default)
     */
    CO2RecordTree(Object name, Map metaData = [:], CO2Record co2Record = null, CO2RecordTree parent = null, List children = []) {
        this.name = name
        this.metaData = metaData
        this.co2Record = co2Record

        this.parent = parent
        this.children = children
    }

    /**
     * Add a child to this CO2RecordTree node.
     *
     * @param child CO2RecordTree node that is to be added
     * @return The new interlinked child node
     */
    CO2RecordTree addChild(CO2RecordTree child) {
        child.parent = this
        children.add(child)
        return child
    }

    /**
     * Return the child with a specific name.
     *
     * @param name Name of the child
     * @return Child node with the specified name, or null if nothing was found
     */
    CO2RecordTree getChild(Object name) {
        return children.find( { CO2RecordTree child -> child.name == name })
    }

    /**
     * Summarizes the content of a Node by adding the sum of the children as a Map representation to its metaData.
     * This methods works recursively by calling the summarize method with the assumption that the bottom layer is additive.
     *
     * Example:
     * Parent(
     *   Child1(value = {a:1, b:1}),
     *   Child2(value = {a:1, b:2})
     * ).summarize()
     *
     * => Parent(value = {a:2, b:3}, children = [Child1(...), Child2(...)])
     *
     * @return this RecordTree node with aggregated summary values
     */
    CO2RecordTree summarize() {
        if (children) {
            children.each({ CO2RecordTree child -> child.summarize() })
        }

        if (!co2Record?.respondsTo('plus')) {
            co2Record = children.collect({ CO2RecordTree child -> child.co2Record }).sum() as CO2Record
        }

        return this
    }

    /**
     * Recursively collect additional metrics that describe the given level.
     *
     * @param metricTransformers Transformer functions with keys from which the given metrics are inferred.
     * @return The changed CO2RecordTree with collected additional metrics
     */
    CO2RecordTree collectAdditionalMetrics(
            Map<String, Closure> metricTransformers = [
                    CO2e_non_cached: { CO2Record record -> record.store.status != 'CACHED' ? record.CO2e : null },
                    energy_non_cached: { CO2Record record -> record.store.status != 'CACHED' ? record.energy : null },
                    CO2e_market: { CO2Record record -> record.CO2e_market },
                    energy_market: { CO2Record record -> record.energy },
            ]
    ) {
        metricTransformers.each{ String name, Closure transformer ->
            List<Object> childMetrics = children.collect{ CO2RecordTree child ->
                child.collectAdditionalMetrics(metricTransformers)
                child.co2Record.additionalMetrics.get(name)
            }
            childMetrics.removeAll([null])
            Object attribute = childMetrics ? childMetrics.sum() : transformer(co2Record)
            co2Record.additionalMetrics.put(name , attribute)
        }
        return this
    }

    /**
     * Collect values with a specified key from leave nodes.
     *
     * @param key Key to a leaf store value
     * @return The corresponding values as a list
     */
    List<Object> collectValues(String key) {
        List<Object> values = []

        // Catch special suffixes
        String suffix = '_' + key.split('_').drop(1).join('_')
        String croppedKey = key
        if (suffix == '_non_cached') {
            croppedKey = key.replace(suffix, '')
        }

        // Extract information
        if (!children && co2Record?.store?.containsKey(croppedKey)) {
            Object val = co2Record.store[croppedKey]
            if (suffix == '_non_cached' && (co2Record?.store?.status == 'CACHED')) {
                // do nothing
            }
            else {
                values.add(val)
            }
        }
        else {
            children.forEach{ CO2RecordTree child -> values.addAll(child.collectValues(key)) }
        }

        return values
    }

    /**
     * Collect all keys that are included in the store of this CO2RecordTree's children.
     *
     * @return All keys that are found in the leaves, collected in a Set
     */
    Set<String> collectKeys() {
        Set<String> keys = children ?
                children.collect({ CO2RecordTree child -> child.collectKeys()}).sum() as Set<String> :
                co2Record.store.keySet()
        return keys
    }

    /**
     * Collect a specified list of values from a (sub)level of this CO2RecordTree.
     *
     * @param level Target level, e.g. 'process'
     * @param valueKeys List of value names that are to be extracted
     * @return A Map with the desired values, collected a the specified level
     */
    Map<String, Map<String, Object>> collectByLevel(String level, List<String> valueKeys=null){
        valueKeys ?= collectKeys().toList()

        // Start value collection at specified level
        if (metaData?.level === level) {
            Map<String, Object> values = [:]
            valueKeys.forEach { String valueKey ->
                List<Object> vals = collectValues(valueKey)
                if (vals) { values[valueKey] = vals }
            }

            return values ? [(name as String): values] as Map : [:]
        }

        // Recursion into deeper levels without collecting values
        Map<String, Map<String, Object>> levelValues = [:]
        children?.forEach{ CO2RecordTree child ->
            levelValues = levelValues + child.collectByLevel(level, valueKeys)
        }

        return levelValues
    }

    /**
     * Descent to another level in the tree.
     *
     * @param level Name of the level.
     * @return A list of record trees at the specified level.
     */
    List<CO2RecordTree> descentTo(String level) {
        if (metaData?.level === level) {
            return [this]
        }
        else {
            return children.collect( { CO2RecordTree child -> child.descentTo(level) } ).flatten() as List<CO2RecordTree>
        }
    }

    /**
     * Convert this record tree to a map.
     *
     * @param emissionMetricsOnly Whether all parameters should be included, or only the ones that are nf-co2 plugin-specific
     * @param includeNulls Whether to include parameters that have `null` as a raw value
     * @param includeReportValues Whether to include 'report' values of entries
     * @return A map representation of this Record Tree
     */
    Map<String, Object> toMap(boolean emissionMetricsOnly=false, boolean includeNulls=true, boolean includeReportValues=true) {
        Map<String, Map<String, Object>> recordMap = [:]
        if (co2Record) {
            (emissionMetricsOnly ? co2Record.emissionMetrics : co2Record.keySet()).each { String key ->
                Map<String, Object> value = co2Record.representationMap.get(key)
                if (includeNulls || value['raw']['value'] != null) {
                    if (!includeReportValues) {
                        value.remove('report')
                    }
                    recordMap.put(key, value)
                }
            }
        }
        return [
            name: name,
            metaData: metaData,
            values: recordMap,
            children: children.collect({ CO2RecordTree child -> child.toMap(emissionMetricsOnly, includeNulls, includeReportValues) }),
        ]
    }

    /**
     * Represent this CO2RecordTree node as a String.
     *
     * @return String representation of this node
     */
    String toString() {
        return [name: name, metaData: metaData, record: co2Record, children: children]
    }
}