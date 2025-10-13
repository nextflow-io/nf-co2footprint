package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Records.CO2Record

/**
 * A tree structure for CO2Records for collecting workflow results. The tree nodes are linked with a parent-children
 * relationship.
 */
class RecordTree {
    final name
    final Map attributes
    CO2Record value

    RecordTree parent
    final List<RecordTree> children

    /**
     * Create a new RecordTree node.
     *
     * @param name Name of the node
     * @param attributes Attributes of the node that are not included in its value, e.g. Level
     * @param value Value of the node
     * @param parent Parent of the node
     * @param children Children Nodes
     */
    RecordTree(Object name, Map attributes = [:], CO2Record value = null, RecordTree parent = null, List children = []) {
        this.name = name
        this.attributes = attributes
        this.value = value

        this.parent = parent
        this.children = children
    }

    /**
     * Add a child to this RecordTree node.
     *
     * @param child RecordTree node that is to be added
     * @return The new interlinked child node
     */
    RecordTree addChild(RecordTree child) {
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
    RecordTree getChild(Object name) {
        return children.find( { RecordTree child -> child.name == name })
    }

    /**
     * Summarizes the content of a Node by adding the sum of the children as a Map representation to its attributes.
     * This methods works recursively by calling the summarize method with the assumption that the bottom layer is additive.
     *
     * Example:
     * X( Y1(a = 1, b = 1), Y2(a = 1, b = 2) ).summarize()
     * => X( a = 2, b = 3; Y1(...), Y2(...) )
     *
     * @return This RecordTree node with summary values
     */
    RecordTree summarize() {
        List<RecordTree> addableChildren = children.findAll( { RecordTree child -> child.value?.respondsTo('plus') } )

        if (!addableChildren) {
            addableChildren = children.collect({ RecordTree child -> child.summarize() })
        }

        value = addableChildren.collect( { RecordTree child -> child.value }).sum() as CO2Record
        return this
    }

    /**
     * Recursively collect attributes that describe the given level.
     *
     * @param attributeTransformers Transformer functions with keys from which the given attributes are inferred.
     * @return The changed RecordTree with collected attributes
     */
    RecordTree collectAttributes(
        Map<String, Closure> attributeTransformers = [
             co2e: { CO2Record record -> record.store.co2e },
             energy: { CO2Record record -> record.store.energy },
             co2e_non_cached: { CO2Record record -> record.store.status != 'CACHED' ? record.store.co2e : null },
             energy_non_cached: { CO2Record record -> record.store.status != 'CACHED' ? record.store.energy : null },
             co2e_market: { CO2Record record -> record.store.co2eMarket },
             energy_market: { CO2Record record -> record.store.energy },
        ]
    ) {
        attributeTransformers.each{ String name, Closure transformer ->
            List<Object> childAttributes = children.collect{ RecordTree child ->
                child.collectAttributes(attributeTransformers)
                child.attributes.get(name)
            }
            Object attribute = childAttributes ? childAttributes.sum() : transformer(value)
            attributes.put(name , attribute)
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
        if (!children && value?.store?.containsKey(croppedKey)) {
            Object val = value.store[croppedKey]
            if (suffix == '_non_cached' && (value?.store?.status == 'CACHED')) {
                // do nothing
            }
            else {
                values.add(val)
            }
        }
        else {
            children.forEach{ RecordTree child -> values.addAll(child.collectValues(key)) }
        }

        return values
    }

    /**
     * Collect all keys that are included in the store of this RecordTree's children.
     *
     * @return All keys that are found in the leaves, collected in a Set
     */
    Set<String> collectKeys() {
        Set<String> keys = children ?
            children.collect({ RecordTree child -> child.collectKeys()}).sum() as Set<String> :
            value.store.keySet()
        return keys
    }

    /**
     * Collect a specified list of values from a (sub)level of this RecordTree.
     *
     * @param level Target level, e.g. 'process'
     * @param valueKeys List of value names that are to be extracted
     * @return A Map with the desired values, collected a the specified level
     */
    Map<String, Map<String, Object>> collectByLevel(String level, List<String> valueKeys=null){
        valueKeys ?= collectKeys().toList()

        // Start value collection at specified level
        if (attributes?.level === level) {
            Map<String, Object> values = [:]
            valueKeys.forEach { String valueKey ->
                List<Object> vals = collectValues(valueKey)
                if (vals) { values[valueKey] = vals }
            }

            return values ? [(name as String): values] as Map : [:]
        }

        // Recursion into deeper levels without collecting values
        Map<String, Map<String, Object>> levelValues = [:]
        children?.forEach{ RecordTree child ->
            levelValues = levelValues + child.collectByLevel(level, valueKeys)
        }

        return levelValues
    }

    /**
     * Convert this record tree to a map.
     *
     * @param onlyCO2parameters Whether all parameters should be included, or only the ones that are nf-co2 plugin-specific
     * @return A map representation of this Record Tree
     */
    Map<String, Object> toMap(boolean onlyCO2parameters=false) {
        Map<String, Map<String, Object>> traceMap = value ? value.toRawReadableMap(onlyCO2parameters) : [:]
        return [
            name: name,
            attributes: attributes,
            values: traceMap,
            children: children.collect({ RecordTree child -> child.toMap(onlyCO2parameters) }),
        ]
    }

    /**
     * Represent this RecordTree node as a String.
     *
     * @return String representation of this node
     */
    String toString() {
        return [name: name, attributes: attributes, values: value, children: children]
    }
}
