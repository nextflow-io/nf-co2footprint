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
    /**
     * Constructs a CO2Record representing the resource usage and emissions for a single task.
     *
     * @param store        Complete store map
     */
    CO2Record(Map<String, Object> store) {
        // Overload the store of the parent to ensure inherited methods can access the stored data
        super.store << store
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
            String name=null, Double energy=null, Double co2e=null, Double co2eMarket=null, Double ci=null,
            Double cpuUsage=null, Long memory=null, Double time=null,  Integer cpus=null, Double powerdrawCPU=null,
            String cpu_model=null, Double rawEnergyProcessor, Double rawEnergyMemory
    ) {
        Map<String, Object> store = new LinkedHashMap<>([
            'name':                     name,
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
        // Overload the store of the parent to ensure inherited methods can access the stored data
        super.store << store
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
        return switch (key) {
            case 'energy' ->  Converter.scaleUnits(value as Double, 'k', 'Wh', '').toMap()
            case 'co2e' ->  Converter.scaleUnits(value as Double, '', 'g', '').toMap()
            case 'co2eMarket' ->  Converter.scaleUnits(value as Double, 'm', 'g', '').toMap()
            case 'time' ->  Converter.scaleTime(value as Double, 'h', 'ms').toMap()
            case 'ci' -> Converter.scaleUnits(value as Double, '', 'gCO₂e/kWh', '').toMap()
            case 'powerdrawCPU' ->  Converter.scaleUnits(value as Double, '', 'W', '').toMap()
            case 'cpuUsage' ->  new Quantity(value as Double, '%', '').toMap()
            case 'memory' ->  Converter.scaleUnits(value as Double, 'G', 'B', '').toMap()
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
            case 'rawEnergyProcessor' ->  Converter.toReadableUnits(value as Double, 'k', 'Wh')
            case 'rawEnergyMemory' ->  Converter.toReadableUnits(value as Double, 'k', 'Wh')
            default -> value as String
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
        return switch (key) {
            case 'ci' -> Calculator.weightedAverage([thisValue, newValue], [store['energy'], record.store['energy']])
            case 'cpuUsage' -> Calculator.weightedAverage([thisValue, newValue], [store['time'], record.store['time']])
            case 'memory' -> Calculator.max(thisValue, newValue)
            case 'cpus' -> Calculator.max(thisValue, newValue)
            case 'powerdrawCPU' -> Calculator.weightedAverage([thisValue, newValue], [store['energy'], record.store['energy']])
            case 'cpu_model' -> thisValue instanceof Set ? thisValue.add(newValue) : [thisValue, newValue] as Set
            case 'name' -> thisValue instanceof Set ? thisValue.add(newValue) : [thisValue, newValue] as Set
            default -> Calculator.add(thisValue, newValue)
        }
    }

    CO2Record plus(CO2Record record) {
        Map<String, Object> store = record.getStore().collectEntries { String key, Object value ->
            [key, plus(key, record)]
        }
        return new CO2Record(store)
    }
}
