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
    private final Double personalEnergyMixco2e
    // Time spent on task (ms)
    private final Double time
    // Carbon intensity (gCO₂eq/kWh)
    private final Double ci
    // Personal energy mix carbon intensity (gCO₂eq/kWh)
    private final Double personalEnergyMixCi
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
            personalEnergyMixco2e:      'num',
            time:                       'num',
            ci:                         'num',
            personalEnergyMixCi:        'num',
            cpus:                       'num',
            powerdrawCPU:               'num',
            cpuUsage:                   'num',
            memory:                     'num',
            name:                       'str',
            cpu_model:                  'str'
    ]

    /**
     * Constructs a CO2Record
     *
     * @param energy Energy used
     * @param co2e CO2 equivalent emissions
     * @param time Time spent on task
     * @param cpus Number of CPU cores used
     * @param powerdrawCPU TDP of CPU
     * @param cpuUsage Usage of CPU
     * @param memory Memory used
     * @param name Name of Task
     * @param cpu_model  CPU model name
     */
    CO2Record(
            Double energy=null, Double co2e=null, Double personalEnergyMixco2e=null, Double time=null,
            Double ci=null, Double personalEnergyMixCi=null, Integer cpus=null, Double powerdrawCPU=null,
            Double cpuUsage=null, Long memory=null, String name=null, String cpu_model=null
    ) {
        this.energy = energy
        this.co2e = co2e
        this.personalEnergyMixco2e = personalEnergyMixco2e
        this.time = time
        this.ci = ci
        this.personalEnergyMixCi = personalEnergyMixCi
        this.cpus = cpus
        this.powerdrawCPU = powerdrawCPU
        this.cpuUsage = cpuUsage
        this.memory = memory
        this.name = name
        this.cpu_model = cpu_model
        Map<String, Object> store = new LinkedHashMap<>([
                'energy':                   energy,
                'co2e':                     co2e,
                'personalEnergyMixco2e':    personalEnergyMixco2e,
                'time':                     time,
                'ci':                       ci,
                'personalEnergyMixCi':      personalEnergyMixCi,
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
    String getEnergyConsumptionReadable() { Converter.toReadableUnits(energy,'m', 'Wh') }

    Double getCO2e() { co2e }
    String getCO2eReadable() { Converter.toReadableUnits(co2e,'m', 'g') }

    Double getPersonalEnergyMixCO2e() { personalEnergyMixco2e }
    String getPersonalEnergyMixCO2eReadable() {
        personalEnergyMixco2e ? Converter.toReadableUnits(personalEnergyMixco2e,'m', 'g') : null
    }

    Double getTime() { time }
    String getTimeReadable() { Converter.toReadableTimeUnits(time, 'ms', 'ms', 'days', 0.0d) }

    String getCIReadable() { Converter.toReadableUnits(ci, '', 'gCO₂eq/kWh') }

    String getPersonalEnergyMixCIReadable() {
        personalEnergyMixCi ? Converter.toReadableUnits(personalEnergyMixCi, '', 'gCO₂eq/kWh') : null
    }

    Integer getCPUs() { cpus }
    String getCPUsReadable() { cpus as String }

    Double getPowerdrawCPU() { powerdrawCPU }
    String getPowerdrawCPUReadable() { powerdrawCPU as String  }

    Double getCPUUsage() { cpuUsage }
    String getCPUUsageReadable() { cpuUsage as String  }

    Long getMemory() { memory }
    String getMemoryReadable() { Converter.toReadableByteUnits(memory) }

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
                this.getNameReadable(), this.getEnergyConsumptionReadable(), this.getCO2eReadable(), this.getPersonalEnergyMixCO2eReadable(),
                this.getTimeReadable(), this.getCIReadable(), this.getPersonalEnergyMixCIReadable(), this.getCPUsReadable(),
                this.getPowerdrawCPUReadable(), this.getCPUModelReadable(), this.getCPUUsageReadable(), this.getMemoryReadable()
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
