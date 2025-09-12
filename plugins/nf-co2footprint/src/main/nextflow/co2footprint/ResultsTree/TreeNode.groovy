package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Metrics.Metric
import nextflow.co2footprint.Metrics.Quantity
import nextflow.trace.TraceRecord

class TreeNode extends Node {
    TreeNode(TreeNode parent, Object name, Map attributes = [:]) {
        super(parent, name, attributes)
    }

    // TODO: Extend Node summary
    void summarize() {
        List<Object> addableChildren = children().findAll( { it.respondsTo('add') } )

        Object summary
        if (addableChildren) {
            summary = addableChildren.sum()
        }
        else {
            summary = children().collect({ summarize() }).sum()
        }
        // TODO: MEH...
        attributes << summary.toMap()
        return summary
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
        if (record.respondsTo('getReadable', [String, Object])) {
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
        if (record.respondsTo('getRaw', [String, Object])) {
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

    static TreeNode toNode(TraceRecord record) {
        Map attributes = [:]
        if (record.respondsTo('add', [TraceRecord])) {
            attributes.put('summarize', { String key, TraceRecord newRecord -> record + newRecord})
        }

        // TODO: dont add values as Nodes, but leave TraceRecords as bottom layer, which is summable
        // TODO: make toMap() method that returns the [readable: x, raw: x] representation of each value
        TreeNode recordNode = new TreeNode(null, record.class.name, attributes)

        record.store.each { String key, Object value ->
            recordNode.appendNode(key, [readable: getReadable(key, record, value), raw: getRaw(key, record, value)], value)
        }
        return recordNode
    }
}
