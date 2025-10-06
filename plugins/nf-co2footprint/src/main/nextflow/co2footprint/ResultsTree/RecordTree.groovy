package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Metrics.Metric
import nextflow.co2footprint.Metrics.Quantity
import nextflow.trace.TraceRecord

class RecordTree {
    protected final name
    protected TraceRecord value
    protected final Map attributes

    protected RecordTree parent
    protected final List<RecordTree> children

    RecordTree(Object name, Map attributes = [:], TraceRecord value = null, RecordTree parent = null, List children = []) {
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

        value = addableChildren.collect( { RecordTree child -> child.value }).sum() as TraceRecord
        return this
    }


    /**
     * Formats a trace entry into a human-readable string.
     * Returns {@link nextflow.trace.TraceRecord#NA} if value is null.
     *
     * @param key   Trace entry key
     * @param value Entry value
     * @param traceRecord Provides fallback formatting
     * @return Human-readable string
     *
     * @example getReadableTraceEntry("realtime", 125.5, tr) → "2m 5s"
     * @example getReadableTraceEntry("memory", 1073741824, tr) → "1 GB"
     * @example getReadableTraceEntry("status", "COMPLETED", tr) → "<span class=\"badge badge-success\">COMPLETED</span>"
     */
    static String getReadable(String key, TraceRecord record, Object value=record.store[key]) {
        // Call upon implemented method first
        if (record.respondsTo('getReadable')) {
            return record.getReadable(key, value)
        }

        // Use TraceRecord value readable reporting scheme
        if (value == null) { return record.NA}
        return switch (key) {
            case 'realtime' -> Converter.toReadableTimeUnits(value as double)
            case 'memory' -> Converter.toReadableUnits(value as double, '', 'B')
            case 'status' -> {
                Map<String, String> colors = [COMPLETED: 'success', CACHED: 'secondary', ABORTED: 'danger', FAILED: 'danger']
                "<span class=\"badge badge-${colors[value]}\">${value}</span>"
            }
            case 'hash' -> {
                String script = ''
                (value as String).eachLine { String line -> script += "${line.trim()}\n" }
                script = script.dropRight(1)
                "<div class=\"script_block short\"><code>${script}</code></div>"
            }
            default -> record.getFmtStr(key)
        }
    }

    /**
     * Formats a trace entry into a raw value.
     * Returns {@link nextflow.trace.TraceRecord#NA} if value is null.
     *
     * @param key   Trace entry key
     * @param value Entry value
     * @param traceRecord Provides fallback formatting
     * @return Raw value as dictionary
     */
    static Map<String, ? extends  Object> getRaw(String key, TraceRecord record, Object value=record.store[key]) {
        // Call upon implemented method first
        if (record.respondsTo('getRaw')) {
            return record.getRaw(key, value)
        }
        return switch (record.FIELDS[key]) {
            case 'date' -> new Quantity(value as Number, '1970-01-01T00:00:00Z -> x', 'ms').toMap()
            case 'time' -> new Quantity(value as Number, '', 'ms').toMap()
            case 'perc' -> new Quantity(value as Number, '%', '').toMap()
            case 'mem' -> new Quantity(value as Number, '', 'B').toMap()
            case 'num' -> new Quantity(value as Number, '', '').toMap()
            default -> new Metric(value).toMap()
        }
    }

    Map<String, Object> toMap() {
        Map<String, Map<String, Object>> traceMap = [:]
        if (value) {
            traceMap = value.store.collectEntries { String key, Object x ->
                [key, [raw: getRaw(key, value, x), readable: getReadable(key, value, x)]]
            }
        }
        return [
                name: name,
                attributes: attributes,
                values: traceMap,
                children: children.collect({ RecordTree child -> child.toMap() }),
        ]
    }
}
