package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Records.CO2Record

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

    RecordTree collectAttributes(Map<String, Closure> attributeTransformers) {
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
