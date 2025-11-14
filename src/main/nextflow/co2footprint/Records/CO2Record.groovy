package nextflow.co2footprint.Records

import groovy.transform.CompileStatic
import nextflow.co2footprint.Metrics.Bytes
import nextflow.co2footprint.Metrics.Calculator

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Metrics.Duration
import nextflow.co2footprint.Metrics.Metric
import nextflow.co2footprint.Metrics.Percentage
import nextflow.co2footprint.Metrics.Quantity
import nextflow.trace.TraceRecord

/**
 * Represents a single CO₂ record for a Nextflow task.
 *
 * Stores energy usage, CO₂ emissions, and resource usage for a task,
 * and provides readable and JSON representations for reporting.
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
@Slf4j
@CompileStatic
class CO2Record extends TraceRecord {
    // Stores keys that are related to the CO2 calculation
    final List<String> co2Keys = [
            'name', 'energy', 'co2e', 'co2eMarket', 'ci', 'cpuUsage', 'memory', 'time',
            'cpus', 'powerdrawCPU', 'cpu_model', 'rawEnergyProcessor', 'rawEnergyMemory'
    ]

    // Stores non-CO₂ keys from the trace record and store them as traceKeys
    final List<String> traceKeys

    // Store for additional metrics
    final Map<String, Object> additionalMetrics = [:]

    // Map representation of class
    Map<String, Map<String, Object>> representationMap = FIELDS.keySet().collectEntries { String key -> [key, getEntryRepresentation(key, null)]}

    static {
        FIELDS.putAll(
            [
                    energy:             'mem',
                    co2e:               'num',
                    co2eMarket:         'num',
                    ci:                 'num',
                    cpuUsage:           'perc',
                    time:               'time',
                    cpus:               'num',
                    memory:             'mem',
                    powerdrawCPU:       'num',
                    cpu_model:          'str',
                    rawEnergyProcessor: 'num',
                    rawEnergyMemory:    'num',
            ]
        )
    }

    /**
     * Create a CO2Record from the given store map.
     *
     * @param store Map with all objects to be stores in this CO2Record
     */
    CO2Record(Map<String, Object> store) {
        traceKeys = store.keySet().findAll({ String key -> key !in co2Keys }) as List<String>
        putAll(store)
    }

    /**
    * Constructs a CO2Record representing the resource usage and emissions for a single task.
    *
    * @param traceRecord   The trace record with the corresponding, unchanged values from which the CO2Record is derived
    * @param energy        Total energy consumed by the task (kWh)
    * @param co2e          CO₂ equivalent emissions (g) based on location-based carbon intensity
    * @param co2eMarket    CO₂ equivalent emissions (g) based on market-based (personal energy mix) carbon intensity
    * @param ci            Location-based carbon intensity used for calculation (gCO₂eq/kWh)
    * @param cpuUsage      CPU usage percentage during the task (%)
    * @param memory        Memory used by the task (bytes)
    * @param time          Time spent on the task (ms)
    * @param cpus          Number of CPU cores used
    * @param powerdrawCPU  Power draw (TDP) of the CPU (W)
    * @param cpu_model     CPU model name
    * @param rawEnergyProcessor Processor-specific energy consumed by the task (kWh)
    * @param rawEnergyMemory    Memory-specific energy consumed by the task (kWh)
    */
    CO2Record(
        TraceRecord traceRecord, Double energy, Double co2e, Double co2eMarket, Double ci,
        Double cpuUsage, Long memory, Double time,  Integer cpus, Double powerdrawCPU,
        String cpu_model, Double rawEnergyProcessor, Double rawEnergyMemory
    ) {
        // Add trace Record values
        traceKeys = traceRecord.store.keySet() as List<String>
        putAll(traceRecord.store)

        // Define CO2-specific storage
        Map<String, Object> store = new LinkedHashMap<>([
            'energy':                   energy,
            'co2e':                     co2e,
            'co2eMarket':               co2eMarket,
            'ci':                       ci,
            'cpuUsage':                 cpuUsage,
            'memory':                   memory,
            'time':                     time,
            'cpus':                     cpus,
            'powerdrawCPU':             powerdrawCPU,
            'cpu_model':                cpu_model,
            'rawEnergyProcessor':       rawEnergyProcessor,
            'rawEnergyMemory':          rawEnergyMemory,
        ])

        // Add CO2-specific values to store + overwrite duplicate values
        putAll(store)
    }

    @Override
    void put(String key, Object value) {
        super.put(key, value)

        // Updates the representation map every time the store is changed
        representationMap.put(key, getEntryRepresentation(key, value))
    }

    /**
     * Fetch an entry from the store, and if not present search additional Metrics for an entry.
     *
     * @param key Name of the entry
     * @return Value of the entry
     */
    Object get(String key){
        return (store.containsKey(key)) ? store.get(key) : additionalMetrics.get(key)
    }



    /**
     * Combine the specified element from two CO2Records.
     * The combination logic is dependent on the metric type.
     *
     * @param key Key to the entry
     * @param record Other CO2Record
     * @return Combination of both values
     */
    Object plus(String key, CO2Record record) {
        Object newValue = record.store[key]
        Object thisValue = this.store[key]

        // Weighted average by energy for carbon intensity and CPU power draw
        if (key in ['ci', 'powerdrawCPU']) {
            return Calculator.weightedAverage([thisValue, newValue], [store['energy'], record.store['energy']])
        }

        // Weighted average by time for CPU usage
        else if (key == 'cpuUsage') {
            return Calculator.weightedAverage([thisValue, newValue], [store['time'], record.store['time']])
        }

        // For memory and CPU count, keep the maximum
        else if (key in ['memory', 'cpus']) {
            return Calculator.max(thisValue, newValue)
        }

        // For string/date-like fields, store all unique values in a Set
        else if ((key in ['cpu_model', 'name', 'status']) || (FIELDS.get(key) in ['str', 'date'])) {
            return thisValue instanceof Set ? thisValue.add(newValue) : [thisValue, newValue] as Set
        }

        // For other numeric fields, sum values safely
        else {
            return Calculator.add(thisValue, newValue)
        }
    }

    /**
     * Add all values withing a CO2Record to another.
     *
     * @param record CO2Record which is added to this
     * @return A new CO2Record with combined elements from both
     */
    CO2Record plus(CO2Record record) {
        if (record == null) { return this }
        Map<String, Object> store = record.store.collectEntries { String key, Object value ->
            [key, plus(key, record)]
        }
        return new CO2Record(store)
    }

    /**
     * Converts a CO₂ record entry into a raw unified value.
     *
     * Numerical values are scaled to their base units.
     * If no value is provided, the method falls back to the stored entry for the given key.
     *
     * @param key   The entry key (e.g. "energy", "co2e", "time", "cpuUsage")
     * @param value Optional value to convert; defaults to the stored value for the key
     * @return      The raw metric with all information as a map
     */
    Map<String, ? extends Object> toRaw(String key, Object value=store[key]) {
         Map<String, ? extends Object> rawValue = switch (key) {
             case 'energy' -> new Quantity(value, 'k', 'Wh').scale('').toMap()
             case 'co2e' -> new Quantity(value, '', 'g').toMap()
             case 'co2eMarket' -> new Quantity(value, '', 'g').toMap()
             case 'time' -> new Duration(value, 'h').scale('ms').toMap()
             case 'ci' -> new Quantity(value, '', 'gCO₂e/kWh').toMap()
             case 'powerdrawCPU' -> new Quantity(value, '', 'W').toMap()
             case 'cpuUsage' -> new Percentage(value).toMap()
             case 'memory' -> new Bytes(value, 'G').scale('').toMap()
             case 'rawEnergyProcessor' -> new Quantity(value, 'k', 'Wh').scale('').toMap()
             case 'rawEnergyMemory' -> new Quantity(value, 'k', 'Wh').scale('').toMap()
             default -> null
         }
         if (rawValue != null) { return rawValue }
        String type = FIELDS.get(key)
         return switch (type) {
            case 'date' -> new Duration(value, 'ms', 'DateTime', 'Unix time').toMap()
            case 'time' -> new Duration(value).toMap()
            case 'perc' -> new Percentage(value).toMap()
            case 'mem' -> new Bytes(value).toMap()
            case 'num' -> new Quantity(value).toMap()
            default -> new Metric(value, type).toMap()
        }
    }

     /**
     * Converts a CO₂ record entry into a human-readable string.
     *
     * Numerical values are scaled and formatted with appropriate units
     * (e.g. Wh, g, %, GB). Non-numerical values are returned as strings.
     * If no value is provided, the method falls back to the stored entry for the given key.
     *
     * @param key   The entry key (e.g. "energy", "co2e", "time", "cpuUsage")
     * @param value Optional value to convert; defaults to the stored value for the key
     * @return      A human-readable string, or null if no conversion is possible
     */
    String toReadable(String key, Object value=store[key]) {
        if (value == null) { return NA }
        return switch (key) {
            case 'energy' ->  new Quantity(value, 'k', 'Wh').toReadable()
            case 'co2e' ->  new Quantity(value, '', 'g').toReadable()
            case 'co2eMarket' ->  new Quantity(value, '', 'g').toReadable()
            case 'time' ->  new Duration(value, 'h').toReadable( 'ms', 'years')
            case 'ci' -> new Quantity(value, '', 'gCO₂e/kWh').toReadable()
            case 'powerdrawCPU' ->  new Quantity(value, '', 'W').toReadable()
            case 'cpuUsage' ->  new Percentage(value).toReadable()
            case 'memory' ->  new Bytes(value, 'G', 'B').toReadable()
            case 'realtime' -> new Duration(value, 'ms').toReadable('ms', 'years')
            case 'rawEnergyProcessor' ->  new Quantity(value, 'k', 'Wh').toReadable()
            case 'rawEnergyMemory' ->  new Quantity(value, 'k', 'Wh').toReadable()
            default -> getFmtStr(key)
        }
    }

    /**
     * Converts a CO₂ record entry into a String that is to be included into the report.
     * Returns `null`, if it does not differ from normal readable instance.
     *
     * @param key   The entry key (e.g. "energy", "co2e", "time", "cpuUsage")
     * @param value Optional value to convert; defaults to the stored value for the key
     * @return      A human-readable string, or null if no conversion is possible
     */
    String toReportable(String key, Object value=store[key]) {
        return switch (key) {
            case 'hash' -> {
                value = toReadable(key, value)
                String script = ''
                (value as String).eachLine { String line -> script += "${line.trim()}\n" }
                script = script.dropRight(1)
                "<div class=\"script_block short\"><code>${script}</code></div>"
            }
            case 'status' -> {
                value = toReadable(key, value)
                Map<String, String> colors = [COMPLETED: 'success', CACHED: 'secondary', ABORTED: 'danger', FAILED: 'danger']
                "<span class=\"badge badge-${colors[value]}\">${value}</span>"
            }
            case 'time' -> toReadable(key, value).split(' ')[0]
            case 'realtime' -> toReadable(key, value).split(' ')[0]
            default -> null
        }
    }

    /**
     * Get the Entries in a readable format for the summary
     *
     * @param order List of keys that defines both which entries are included
     *              and the order in which they appear (defaults to all keys in the order of this.store).
     * @return List of readable Entries
     */
    List<String> getReadableEntries(List<String> order=co2Keys as List) {
        return order.collect { String key -> toReadable(key) }
    }

    /**
     * Convert the record into a nested map with raw, readable and in special cases report values for each field.
     * The raw value contains the unit and scope, as well as comments to contextualize the value.
     *
     * @param onlyCO2parameters if true, keep only CO₂-specific keys
     * @return map of store entry → { raw: {value, type}, readable: value, report: value }
     */
    Map<String, Object> getEntryRepresentation(String key, Object value=store[key]) {
        Map<String, Object> entryRepresentation = [raw: toRaw(key, value), readable: toReadable(key, value)]

        String reportValue = toReportable(key, value)
        if (reportValue != null) {  entryRepresentation += [report: reportValue as Object] }

        return entryRepresentation
    }
}
