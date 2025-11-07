package nextflow.co2footprint.Records

import nextflow.co2footprint.Metrics.Converter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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

    // Energy used (kWh)
    final Double energy
    // CO2 equivalent emissions (g)
    final Double co2e
    // Personal energy mix CO2 equivalent emissions (g)
    final Double co2eMarket
    // Time spent on task (ms)
    final Double time
    // Carbon intensity (gCO₂eq/kWh)
    final Double ci
    // Number of CPU cores used
    final Integer cpus
    // Power draw of CPU (W)
    final Double powerdrawCPU
    // CPU usage (%)
    final Double cpuUsage
    // Memory used (bytes)
    final Long memory
    // Name of Task
    final String name
    // CPU model name
    final String cpu_model
    // Raw energy used by CPU (kWh)
    final Double rawEnergyProcessor
    // Raw energy used by memory (kWh)
    final Double rawEnergyMemory

    // Properties of entries for JSON rendering
    final public static Map<String,String> FIELDS = [
            energy:             'num',
            co2e:               'num',
            co2eMarket:         'num',
            time:               'num',
            ci:                 'num',
            cpus:               'num',
            powerdrawCPU:       'num',
            cpuUsage:           'num',
            memory:             'num',
            name:               'str',
            cpu_model:          'str',
            rawEnergyProcessor: 'num',
            rawEnergyMemory:    'num',
    ]

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
            Double energy=null, Double co2e=null, Double co2eMarket=null, Double time=null,
            Double ci=null, Integer cpus=null, Double powerdrawCPU=null,
            Double cpuUsage=null, Long memory=null, String name=null, String cpu_model=null,
            Double rawEnergyProcessor, Double rawEnergyMemory
    ) {
        this.energy = energy
        this.co2e = co2e
        this.co2eMarket = co2eMarket
        this.time = time
        this.ci = ci
        this.cpus = cpus
        this.powerdrawCPU = powerdrawCPU
        this.cpuUsage = cpuUsage
        this.memory = memory
        this.name = name
        this.cpu_model = cpu_model
        this.rawEnergyProcessor = rawEnergyProcessor
        this.rawEnergyMemory = rawEnergyMemory
        Map<String, Object> store = new LinkedHashMap<>([
                energy:             energy,
                co2e:               co2e,
                co2eMarket:         co2eMarket,
                time:               time,
                ci:                 ci,
                cpus:               cpus,
                powerdrawCPU:       powerdrawCPU,
                cpuUsage:           cpuUsage,
                memory:             memory,
                name:               name,
                cpu_model:          cpu_model,
                rawEnergyProcessor: rawEnergyProcessor,
                rawEnergyMemory:    rawEnergyMemory,
        ])
        // Overload the store of the parent to ensure inherited methods can access the stored data
        super.store << store
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
            case 'energy' ->  Converter.toReadableUnits(value as double, 'k', 'Wh')
            case 'co2e' ->  Converter.toReadableUnits(value as double, '', 'g')
            case 'co2eMarket' ->  Converter.toReadableUnits(value as double, '', 'g')
            case 'time' ->  Converter.toReadableTimeUnits(value as double, 'h', 'ms', 's', 0.0d)
            case 'ci' -> Converter.toReadableUnits(value as double, '', 'gCO₂e/kWh')
            case 'powerdrawCPU' ->  Converter.toReadableUnits(value as double, '', 'W')
            case 'cpuUsage' ->  Converter.toReadableUnits(value as double, '', '%', '')
            case 'memory' ->  Converter.toReadableUnits(value as double, 'G', 'B')
            case 'rawEnergyProcessor' ->  Converter.toReadableUnits(value as double, 'k', 'Wh')
            case 'rawEnergyMemory' ->  Converter.toReadableUnits(value as double, 'k', 'Wh')
            default -> value as String
        }
    }

    /**
     * Renders the JSON output of a CO2Record.
     *
     * @param stringBuilder A StringBuilder used to elongate the String
     * @return JSON representation of the record
     */
    @Override
    CharSequence renderJson(StringBuilder stringBuilder=new StringBuilder()) {
        return super.renderJson(stringBuilder, FIELDS.keySet() as List, FIELDS.values() as List)
    }
}
