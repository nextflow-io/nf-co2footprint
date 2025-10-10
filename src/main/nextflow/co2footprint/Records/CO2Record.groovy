package nextflow.co2footprint.Records

import groovy.transform.CompileStatic
import nextflow.co2footprint.Metrics.Calculator
import nextflow.co2footprint.Metrics.Converter

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Metrics.Metric
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
    final Set<String> co2Keys = Set.of('energy', 'co2e', 'co2eMarket', 'ci', 'cpuUsage', 'memory', 'time', 'cpus', 'powerdrawCPU', 'cpu_model')
    final Set<String> traceKeys

    CO2Record (Map<String, Object> store) {
        this.traceKeys = store.keySet().findAll({ String key -> key !in co2Keys })
        super.store.putAll(store)
    }

    /**
    * Constructs a CO2Record representing the resource usage and emissions for a single task.
    *
    * @param energy             Total energy consumed by the task (kWh)
    * @param co2e               CO₂ equivalent emissions (g) based on location-based carbon intensity
    * @param co2eMarket         CO₂ equivalent emissions (g) based on market-based (personal energy mix) carbon intensity
    * @param time               Time spent on the task (ms)
    * @param ci                 Location-based carbon intensity used for calculation (gCO₂eq/kWh)
    * @param cpus               Number of CPU cores used
    * @param powerdrawCPU       Power draw (TDP) of the CPU (W)
    * @param cpuUsage           CPU usage percentage during the task (%)
    * @param memory             Memory used by the task (bytes)
    * @param name               Name of the task
    * @param cpu_model          CPU model name
    * @param rawEnergyProcessor Processor-specific energy consumed by the task (kWh)
    * @param rawEnergyMemory    Memory-specific energy consumed by the task (kWh)
    */
    CO2Record(
        TraceRecord traceRecord=null, Double energy=null, Double co2e=null, Double co2eMarket=null, Double ci=null,
        Double cpuUsage=null, Long memory=null, Double time=null,  Integer cpus=null, Double powerdrawCPU=null,
        String cpu_model=null, Double rawEnergyProcessor, Double rawEnergyMemory
    ) {
        // Add trace Record values
        taskId << traceRecord.taskId
        traceKeys = traceRecord.store.keySet()
        super.store.putAll(traceRecord.store)

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

        // Add additional values to TraceRecord's store + overwrite duplicate values
        super.store.putAll(store)
    }

    @Override
    String getFmtStr(String name, String converter = null) {
        return name in co2Keys ? store[name] as String : super.getFmtStr(name, converter)
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
    Map<String, ? extends Object> getRaw(String key, Object value=store[key]) {
         Map<String, ? extends Object> rawValue = switch (key) {
             case 'energy' -> Converter.scaleUnits(value as Double, 'k', 'Wh', '').toMap()
             case 'co2e' -> Converter.scaleUnits(value as Double, '', 'g', '').toMap()
             case 'co2eMarket' -> Converter.scaleUnits(value as Double, 'm', 'g', '').toMap()
             case 'time' -> Converter.scaleTime(value as Double, 'h', 'ms').toMap()
             case 'ci' -> Converter.scaleUnits(value as Double, '', 'gCO₂e/kWh', '').toMap()
             case 'powerdrawCPU' -> Converter.scaleUnits(value as Double, '', 'W', '').toMap()
             case 'cpuUsage' -> new Quantity(value as Double, '%', '').toMap()
             case 'memory' -> Converter.scaleUnits(value as Double, 'G', 'B', '').toMap()
             default -> null
         }
         if (rawValue) { return rawValue }
         return switch (FIELDS.get(key)) {
            case 'date' -> new Quantity(value as Number, '1970-01-01T00:00:00Z -> x', 'ms').toMap()
            case 'perc' -> new Quantity(value as Number, '%', '').toMap()
            case 'mem' -> new Quantity(value as Number, '', 'B').toMap()
            case 'num' -> new Quantity(value as Number, '', '').toMap()
            default -> new Metric(value).toMap()
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
    String getReadable(String key, Object value=store[key]) {
        if (value == null) { return NA }
        return switch (key) {
            case 'energy' ->  Converter.toReadableUnits(value as Double, 'k', 'Wh')
            case 'co2e' ->  Converter.toReadableUnits(value as Double, '', 'g')
            case 'co2eMarket' ->  Converter.toReadableUnits(value as Double, 'm', 'g')
            case 'time' ->  Converter.toReadableTimeUnits(value as Double, 'h', 'ms', 's', 0.0d)
            case 'ci' -> Converter.toReadableUnits(value as Double, '', 'gCO₂e/kWh')
            case 'powerdrawCPU' ->  Converter.toReadableUnits(value as Double, '', 'W')
            case 'cpuUsage' ->  Converter.toReadableUnits(value as Double, '', '%', '')
            case 'memory' ->  Converter.toReadableUnits(value as Double, 'G', 'B')
            case 'realtime' -> Converter.toReadableTimeUnits(value as Double)
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

            case 'rawEnergyProcessor' ->  Converter.toReadableUnits(value as Double, 'k', 'Wh')
            case 'rawEnergyMemory' ->  Converter.toReadableUnits(value as Double, 'k', 'Wh')
            default -> getFmtStr(key)
        }
    }

    /**
     * Get the Entries in a readable format for the summary
     *
     * @param order List of keys that defines both which entries are included
     *              and the order in which they appear (defaults to all keys in the order of this.store).
     * @return List of readable Entries
     */
    List<String> getReadableEntries(List<String> order=store.keySet() as List) {
        return order.collect { String key -> getReadable(key) }
    }

    // TODO: Docstrings
    Object plus(String key, CO2Record record) {
        Object newValue = record.store[key]
        Object thisValue = this.store[key]
        if (key in ['ci', 'powerdrawCPU']) {
            return Calculator.weightedAverage([thisValue, newValue], [store['energy'], record.store['energy']])
        }
        else if (key == 'cpuUsage') {
            return Calculator.weightedAverage([thisValue, newValue], [store['time'], record.store['time']])
        }
        else if (key in ['memory', 'cpus']) {
            return Calculator.max(thisValue, newValue)
        }
        else if ((key in ['cpu_model', 'name', 'status']) || (FIELDS.get(key) in ['str', 'date'])) {
            return thisValue instanceof Set ? thisValue.add(newValue) : [thisValue, newValue] as Set
        }
        else {
            return Calculator.add(thisValue, newValue)
        }
    }

    CO2Record plus(CO2Record record) {
        Map<String, Object> store = record.store.collectEntries { String key, Object value ->
            [key, plus(key, record)]
        }
        return new CO2Record(store)
    }

    Map<String, Map<String, Object>> toRawReadableMap() {
        Map<String, Map<String, Object>> rrMap = FIELDS.collectEntries { String key, String type ->
            [key, [raw: [value: null, type: type], readable: NA]]
        }
        rrMap.putAll(store.collectEntries { String key, Object val ->
            [key, [raw: getRaw(key, val), readable: getReadable(key, val)]]
        })
        return rrMap
    }
}
