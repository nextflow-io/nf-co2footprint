package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Records.CO2Record

import java.time.Duration

// TODO: Docstrings
class RecordTree {
    final name
    CO2Record value
    final Map attributes

    RecordTree parent
    final List<RecordTree> children

    RecordTree(Object name, Map attributes = [:], CO2Record value = null, RecordTree parent = null, List children = []) {
        this.name = name
        this.attributes = attributes
        this.value = value

        this.parent = parent
        this.children = children
    }

    RecordTree addChild(RecordTree child) {
        child.parent = this
        children.add(child)
        return child
    }


    RecordTree getChild(Object name) {
        return children.find( { RecordTree child -> child.name == name })
    }

    /*
     * Summarizes the content of a Node by adding the sum of the children as a Map representation to its attributes.
     * This methods works recursively by calling the summarize method with the assumption that the bottom layer is additive.
     *
     * Example:
     * X( Y1(a = 1, b = 1), Y2(a = 1, b = 2) ).summarize()
     * => X( a = 2, b = 3; Y1(...), Y2(...) )
     */
    RecordTree summarize() {
        List<RecordTree> addableChildren = children.findAll( { RecordTree child -> child.value?.respondsTo('plus') } )

        if (!addableChildren) {
            addableChildren = children.collect({ RecordTree child -> child.summarize() })
        }

        value = addableChildren.collect( { RecordTree child -> child.value }).sum() as CO2Record
        return this
    }

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

    // Collect metrics by process
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
            // Convert time to rounded minutes
            else if (key == 'time') {
                values.add(
                    Duration.ofMillis(val as Integer)
                            .toMinutes().toBigDecimal()
                            .setScale(1).stripTrailingZeros().toPlainString()
                )
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

    Set<String> collectKeys() {
        Set<String> keys = children ?
            children.collect({ RecordTree child -> child.collectKeys()}).sum() as Set<String> :
            value.store.keySet()
        return keys
    }

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

    Map<String, Object> toMap() {
        Map<String, Map<String, Object>> traceMap = value ? value.toRawReadableMap() : [:]
        return [
            name: name,
            attributes: attributes,
            values: traceMap,
            children: children.collect({ RecordTree child -> child.toMap() }),
        ]
    }
}
