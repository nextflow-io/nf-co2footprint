package nextflow.co2footprint

import nextflow.co2footprint.utils.Converter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.trace.TraceRecord
import groovy.json.StringEscapeUtils


/**
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
@Slf4j
@CompileStatic
class CO2Record extends TraceRecord {

    // Entries
    private final Double energy
    private final Double co2e
    private final Double time
    private final Double ci
    private final Integer cpus
    private final Double powerdrawCPU
    private final Double cpuUsage
    private final Long memory
    private final String name
    private final String cpu_model

    // Stored entries
    Map<String,Object> store

    // Properties of entries
    final public static Map<String,String> FIELDS = [
            energy:         'num',
            co2e:           'num',
            time:           'num',
            ci:             'num',
            cpus:           'num',
            powerdrawCPU:   'num',
            cpuUsage:       'num',
            memory:         'num',
            name:           'str',
            cpu_model:      'str'
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
            Double energy=0d, Double co2e=0d, Double time=0d, Double ci, Integer cpus=0, Double powerdrawCPU=0d,
            Double cpuUsage=0d, Long memory=0d, String name=0d, String cpu_model=0d
    ) {
        this.energy = energy
        this.co2e = co2e
        this.time = time
        this.ci = ci
        this.cpus = cpus
        this.powerdrawCPU = powerdrawCPU
        this.cpuUsage = cpuUsage
        this.memory = memory
        this.name = name
        this.cpu_model = cpu_model
        this.store = new LinkedHashMap<>([
                'energy':           energy,
                'co2e':             co2e,
                'time':             time,
                'ci':               ci,
                'cpus':             cpus,
                'powerdrawCPU':     powerdrawCPU,
                'cpuUsage':         cpuUsage,
                'memory':           memory,
                'name':             name,
                'cpu_model':        cpu_model
        ])
    }

    Double getEnergyConsumption() { energy }
    String getEnergyConsumptionReadable() { Converter.toReadableUnits(energy,'m', 'Wh') }

    Double getCO2e() { co2e }
    String getCO2eReadable() { Converter.toReadableUnits(co2e,'m', 'g') }

    Double getTime() { time }
    String getTimeReadable() { Converter.toReadableTimeUnits(time, 'ms', 'ms', 'days', 0.0d) }

    String getCIReadable() { Converter.toReadableUnits(ci, '', 'gCO₂eq/kWh') }

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
                this.getNameReadable(), this.getEnergyConsumptionReadable(), this.getCO2eReadable(),
                this.getTimeReadable(), this.getCIReadable(), this.getCPUsReadable(), this.getPowerdrawCPUReadable(),
                this.getCPUModelReadable(), this.getCPUUsageReadable(), this.getMemoryReadable()
        ]
    }
}
