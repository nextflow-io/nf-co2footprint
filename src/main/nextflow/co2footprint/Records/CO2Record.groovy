package nextflow.co2footprint.Records

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.co2footprint.Metrics.*
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
     static {
         FIELDS.putAll(
                 [
                         energy_consumption:        'mem',
                         CO2e:                      'num',
                         CO2e_market:               'num',
                         carbon_intensity:          'num',
                         carbon_intensity_market:   'num',
                         '%cpu':                    'perc',
                         realtime:                  'time',
                         cpus:                      'num',
                         memory:                    'mem',
                         pue:                       'num',
                         powerdraw_cpu:             'num',
                         powerdraw_memory:          'num',
                         cpu_power_model:           'str',
                         cpu_model:                 'str',
                         raw_energy_processor:      'num',
                         raw_energy_memory:         'num',
                 ]
         )
     }

    // Stores keys that are related to the CO2 calculation
    final static List<String> emissionMetrics = [
            'task_id', 'status', 'name', 'energy_consumption', 'CO2e', 'CO2e_market', 'carbon_intensity', 'carbon_intensity_market',
            '%cpu', 'memory', 'realtime', 'cpus', 'pue', 'powerdraw_cpu', 'powerdraw_memory', 'cpu_power_model', 'cpu_model', 'raw_energy_processor', 'raw_energy_memory'
    ]

    // Stores non-CO₂ keys from the trace record and store them as traceKeys
    final List<String> traceKeys

    // Store for additional metrics
    final Map<String, Object> additionalMetrics = [:]

    // Map representation of class
    Map<String, Map<String, Object>> representationMap = FIELDS.keySet().collectEntries { String key ->
        [key, getEntryRepresentation(key, null)]
    }

    /**
     * Create a CO2Record from the given store map.
     *
     * @param store Map with all objects to be stores in this CO2Record
     */
    CO2Record(Map<String, Object> store) {
        traceKeys = store.keySet().findAll({ String key -> key !in emissionMetrics }) as List<String>
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
    * @param ciMarket      Market-based carbon intensity used for calculation (gCO₂eq/kWh)
    * @param cpuUsage      CPU usage percentage during the task (%)
    * @param memory        Memory used by the task (bytes)
    * @param time          Time spent on the task (ms)
    * @param cpus          Number of CPU cores used
    * @param pue           Power Usage Effectiveness of the data center where the task was executed
    * @param powerdrawCPU  Power draw (TDP) of the CPU (W)
    * @param powerdrawMem  Power draw per GB of memory (W/GB)
    * @param cpuPowerModel Coefficients of the polynomial model to calculate CPU power draw based on usage, if provided in config (W/core)
    * @param cpu_model     CPU model name
    * @param rawEnergyProcessor Processor-specific energy consumed by the task (kWh)
    * @param rawEnergyMemory    Memory-specific energy consumed by the task (kWh)
    */
    CO2Record(
        TraceRecord traceRecord, BigDecimal energy, BigDecimal co2e, BigDecimal co2eMarket, BigDecimal ci, BigDecimal ciMarket,
        BigDecimal cpuUsage, Long memory, BigDecimal time,  Integer cpus, BigDecimal pue, BigDecimal powerdrawCPU, BigDecimal powerdrawMem,
        String cpuPowerModel, String cpu_model, BigDecimal rawEnergyProcessor, BigDecimal rawEnergyMemory
    ) {
        // Add trace Record values
        traceKeys = traceRecord.store.keySet() as List<String>
        putAll(traceRecord.store)

        // Define CO2-specific storage
        Map<String, Object> store = new LinkedHashMap<>([
            'energy_consumption':        energy,
            'CO2e':                     co2e,
            'CO2e_market':              co2eMarket,
            'carbon_intensity':         ci,
            'carbon_intensity_market':  ciMarket,
            '%cpu':                     cpuUsage,
            'memory':                   memory,
            'realtime':                 Duration.of(time, 'h').scale('ms').value,
            'cpus':                     cpus,
            'pue':                      pue,
            'powerdraw_cpu':            powerdrawCPU,
            'powerdraw_memory':         powerdrawMem,
            'cpu_power_model':          cpuPowerModel,
            'cpu_model':                cpu_model,
            'raw_energy_processor':     rawEnergyProcessor,
            'raw_energy_memory':        rawEnergyMemory,
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
        if (key in ['carbon_intensity', 'powerdraw_cpu', 'carbon_intensity_market', 'powerdraw_memory']) {
            return Calculator.weightedAverage([thisValue, newValue], [store['energy_consumption'], record.store['energy_consumption']])
        }

        // Weighted average by time for CPU usage
        else if (key in ['%cpu', '%mem', 'vmem', 'rss', 'cpus', 'pue']) {
            return Calculator.weightedAverage([thisValue, newValue], [store['realtime'], record.store['realtime']])
        }

        // For memory and CPU count and completion time, keep the maximum
        else if (key in ['memory', 'cpus', 'complete', 'attempt', 'peak_vmem', 'peak_rss']) {
            return Calculator.max(thisValue, newValue)
        }

        // For submission and start time keep minimum
        else if(key in ['submit', 'start']) {
            return Calculator.min(thisValue, newValue)
        }

        // For string/date-like fields, store all unique values in a Set
        else if ((key in ['cpu_model', 'status', 'name', 'cpu_power_model']) || (FIELDS.get(key) in ['str'])) {
            return thisValue == newValue ? thisValue : null
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
     * @param key   The entry key (e.g. "energy", "CO2e", "time", "%cpu")
     * @param value Optional value to convert; defaults to the stored value for the key
     * @return      The raw metric with all information as a map
     */
    Map<String, ? extends Object> toRaw(String key, Object value=store[key]) {
         Map<String, ? extends Object> rawValue = switch (key) {
             case 'energy_consumption' -> Quantity.of(value, 'k', 'Wh').scale('').toMap()
             case 'CO2e' -> Quantity.of(value, '', 'g').toMap()
             case 'CO2e_market' -> Quantity.of(value, '', 'g').toMap()
             case 'realtime' -> Duration.of(value, 'ms').scale('ms').toMap()
             case 'carbon_intensity' -> Quantity.of(value, '', 'gCO₂e/kWh').toMap()
             case 'carbon_intensity_market' -> Quantity.of(value, '', 'gCO₂e/kWh').toMap()
             case 'powerdraw_cpu' -> Quantity.of(value, '', 'W').toMap()
             case 'powerdraw_memory' -> Quantity.of(value, '', 'W').toMap()
             case '%cpu' -> Percentage.of(value).toMap()
             case 'memory' -> Bytes.of(value, 'G').scale('').toMap()
             case 'raw_energy_processor' -> Quantity.of(value, 'k', 'Wh').scale('').toMap()
             case 'raw_energy_memory' -> Quantity.of(value, 'k', 'Wh').scale('').toMap()
             default -> null
         }
         if (rawValue != null) { return rawValue }
        String type = FIELDS.get(key)
         return switch (type) {
            case 'date' -> Duration.of(value, 'ms', 'DateTime', 'Unix time').toMap()
            case 'time' -> Duration.of(value).toMap()
            case 'perc' -> Percentage.of(value).toMap()
            case 'mem' -> Bytes.of(value).toMap()
            case 'num' -> Quantity.of(value).toMap()
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
     * @param key   The entry key (e.g. "energy", "CO2e", "time", "%cpu")
     * @param value Optional value to convert; defaults to the stored value for the key
     * @return      A human-readable string, or null if no conversion is possible
     */
    String toReadable(String key, Object value=store[key]) {
        if (value == null) { return NA }
        return switch (key) {
            case 'energy_consumption' ->  new Quantity(value, 'k', 'Wh').toReadable()
            case 'CO2e' ->  new Quantity(value, '', 'g').toReadable()
            case 'CO2e_market' ->  new Quantity(value, '', 'g').toReadable()
            case 'realtime' ->  new Duration(value, 'ms').toReadable( 'ms', 'years')
            case 'carbon_intensity' -> new Quantity(value, '', 'gCO₂e/kWh').toReadable()
            case 'carbon_intensity_market' -> new Quantity(value, '', 'gCO₂e/kWh').toReadable()
            case 'powerdraw_cpu' ->  new Quantity(value, '', 'W').toReadable()
            case 'powerdraw_memory' ->  new Quantity(value, '', 'W').toReadable()
            case 'pue' ->  new Quantity(value).toReadable()
            case '%cpu' ->  new Percentage(value).toReadable()
            case 'memory' ->  new Bytes(value, 'G', 'B').toReadable()
            case 'raw_energy_processor' ->  new Quantity(value, 'k', 'Wh').toReadable()
            case 'raw_energy_memory' ->  new Quantity(value, 'k', 'Wh').toReadable()
            default -> getFmtStr(key)
        }
    }

    /**
     * Converts a CO₂ record entry into a String that is to be included into the report.
     * Returns `null`, if it does not differ from normal readable instance.
     *
     * @param key   The entry key (e.g. "energy", "CO2e", "realtime", "%cpu")
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
    List<String> getReadableEntries(List<String> order=emissionMetrics as List) {
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
