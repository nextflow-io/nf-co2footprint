package nextflow.co2footprint.Records

import nextflow.co2footprint.utils.Converter

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

    // Energy used (Wh)
    private final Double energy
    // CO2 equivalent emissions (g)
    private final Double co2e
    // Personal energy mix CO2 equivalent emissions (g)
    private final Double co2eMarket
    // Time spent on task (ms)
    private final Double time
    // Carbon intensity (gCO₂eq/kWh)
    private final Double ci
    // Number of CPU cores used
    private final Integer cpus
    // Power draw of CPU (W)
    private final Double powerdrawCPU
    // CPU usage (%)
    private final Double cpuUsage
    // Memory used (bytes)
    private final Long memory
    // Name of Task
    private final String name
    // CPU model name
    private final String cpu_model

    // Properties of entries for JSON rendering
    final public static Map<String,String> FIELDS = [
            energy:                     'num',
            co2e:                       'num',
            co2eMarket:                 'num',
            time:                       'num',
            ci:                         'num',
            cpus:                       'num',
            powerdrawCPU:               'num',
            cpuUsage:                   'num',
            memory:                     'num',
            name:                       'str',
            cpu_model:                  'str'
    ]

    /**
    * Constructs a CO2Record representing the resource usage and emissions for a single task.
    *
    * @param energy        Total energy consumed by the task (Wh)
    * @param co2e          CO₂ equivalent emissions (g) based on location-based carbon intensity
    * @param co2eMarket    CO₂ equivalent emissions (g) based on market-based (personal energy mix) carbon intensity
    * @param time          Time spent on the task (ms)
    * @param ci            Location-based carbon intensity used for calculation (gCO₂eq/kWh)
    * @param cpus          Number of CPU cores used
    * @param powerdrawCPU  Power draw (TDP) of the CPU (W)
    * @param cpuUsage      CPU usage percentage during the task (%)
    * @param memory        Memory used by the task (bytes)
    * @param name          Name of the task
    * @param cpu_model     CPU model name
    */
    CO2Record(
            Double energy=null, Double co2e=null, Double co2eMarket=null, Double time=null,
            Double ci=null, Integer cpus=null, Double powerdrawCPU=null,
            Double cpuUsage=null, Long memory=null, String name=null, String cpu_model=null
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
        Map<String, Object> store = new LinkedHashMap<>([
                'energy':                   energy,
                'co2e':                     co2e,
                'co2eMarket':               co2eMarket,
                'time':                     time,
                'ci':                       ci,
                'cpus':                     cpus,
                'powerdrawCPU':             powerdrawCPU,
                'cpuUsage':                 cpuUsage,
                'memory':                   memory,
                'name':                     name,
                'cpu_model':                cpu_model
        ])
        // Overload the store of the parent to ensure inherited methods can access the stored data
        super.store << store
    }

    // Getters for properties with readable formats
    Double getEnergyConsumption() { energy }
    String getEnergyConsumptionReadable() { Converter.toReadableUnits(energy, 'm', 'Wh') }

    Double getCO2e() { co2e }
    String getCO2eReadable() { Converter.toReadableUnits(co2e, 'm', 'g') }

    Double getCO2eMarket() { co2eMarket }
    String getCO2eMarketReadable() { Converter.toReadableUnits(co2eMarket, 'm', 'g') }

    Double getTime() { time }
    String getTimeReadable() { Converter.toReadableTimeUnits(time, 'h', 'ms', 's', 0.0d) }

    String getCIReadable() { Converter.toReadableUnits(ci, '', 'gCO₂e/kWh') }

    Integer getCPUs() { cpus }
    String getCPUsReadable() { cpus as String }

    Double getPowerdrawCPU() { powerdrawCPU }
    String getPowerdrawCPUReadable() { Converter.toReadableUnits(powerdrawCPU, '', 'W', '') }

    Double getCPUUsage() { cpuUsage }
    String getCPUUsageReadable() { Converter.toReadableUnits(cpuUsage, '', '%', '') }

    Long getMemory() { memory }
    String getMemoryReadable() { Converter.toReadableUnits(memory, '', 'B') }

    String getName() { name }
    String getNameReadable() { name }

    String getCPUModel() { cpu_model }
    String getCPUModelReadable() { cpu_model }

    /**
     * Get the Entries in a readable format for the summary
     * @return List of readable Entries
     */
    List<String> getReadableEntries() {
        return [
                this.getNameReadable(), this.getEnergyConsumptionReadable(), this.getCO2eReadable(), this.getCO2eMarketReadable(),
                this.getCIReadable(), this.getCPUUsageReadable(), this.getMemoryReadable(), this.getTimeReadable(), this.getCPUsReadable(),
                this.getPowerdrawCPUReadable(), this.getCPUModelReadable()
        ]
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
