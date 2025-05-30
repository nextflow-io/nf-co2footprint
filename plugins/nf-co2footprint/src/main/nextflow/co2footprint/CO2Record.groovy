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

    CO2Record(Double energy, Double co2e, Double time, Double ci, Integer cpus, Double powerdrawCPU, Double cpuUsage, Long memory, String name, String cpu_model) {
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

    List<String> getReadableEntries() {
        return [
                this.getNameReadable(), this.getEnergyConsumptionReadable(), this.getCO2eReadable(),
                this.getTimeReadable(), this.getCIReadable(), this.getCPUsReadable(), this.getPowerdrawCPUReadable(),
                this.getCPUModelReadable(), this.getCPUUsageReadable(), this.getMemoryReadable()
        ]
    }

    //@PackageScope
    Map<String,Object> store

    @Override
    String toString() {
        "${this.class.simpleName} ${store}"
    }

    @Override
    CharSequence renderJson(StringBuilder result, List<String> fields, List<String> formats) {
        final String QUOTE = '"'
        final String NA = '-'
        if( result == null ) result = new StringBuilder()
        result.deleteCharAt(result.length() - 1) // remove the last character "}"
        result << ','
        for( int i=0; i<fields.size(); i++ ) {
            final String name = fields[i]
            if ( name == 'name' ) continue // skip the name field (it's already in the key)
            if ( i ) result << ','
            final String format = i<formats?.size() ? formats[i] : null
            final String value = StringEscapeUtils.escapeJavaScript(getFmtStr(name, format) ?: NA)
            result << QUOTE << name << QUOTE << ":" << QUOTE << value << QUOTE
        }
        result << "}"
        return result
    }

    /**
     * Get a trace field value and apply a conversion rule to it
     *
     * @param name The field name e.g. task_id, status, etc.
     * @param converter A converter string
     * @return A string value formatted according the specified converter
     */
    @Override
    String getFmtStr( String name, String converter = null ) {
        assert name
        final val = store.get(name)

        String sType=null
        String sFormat=null
        if( converter ) {
            int p = converter.indexOf(':')
            if( p == -1 ) {
                sType = converter
            }
            else {
                sType = converter.substring(0,p)
                sFormat = converter.substring(p+1)
            }
        }

        final String type = sType ?: FIELDS.get(name)
        if( !type )
            throw new IllegalArgumentException("Not a valid trace field name: '$name'")


        final Closure<String> formatter = FORMATTER.get(type)
        if( !formatter )
            throw new IllegalArgumentException("Not a valid trace formatter for field: '$name' with type: '$type'")

        try {
            return formatter.call(val, sFormat)
        }
        catch( Throwable ignore ) {
            log.debug("Not a valid trace value -- field: '$name'; value: '$val'; format: '$sFormat'")
            return null
        }
    }
}
