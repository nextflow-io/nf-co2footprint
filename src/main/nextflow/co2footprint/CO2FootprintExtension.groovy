package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Metrics.Quantity
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import nextflow.trace.TraceRecord

import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * A PluginExtensionPoint wo interact with core functionalities of the plugin.
 */
class CO2FootprintExtension extends PluginExtensionPoint {
    /**
     * Instance of an observer to simulate a run with a parsed trace file.
     */
    CO2FootprintObserver observer

    /**
     * Initializes the Extension point of the plugin with the session to create an observer.
     */
    @Override
    void init(Session session){
        CO2FootprintFactory factory = new CO2FootprintFactory()
        observer = factory.create(session)[0] as CO2FootprintObserver
    }

    /**
     * Parse the content of a Nextflow trace file in its raw and readable format.
     *
     * @param tracePath Path to the trace file
     * @param delimiter Delimiter of the trace file, default: \t
     * @return A list of all task-specific trace records inferred from the trace file
     */
    @Function
    static List<TraceRecord> parseTraceFile(Path tracePath, String delimiter='\t') {
        SimpleDateFormat dateParser = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss.SSS')
        List<String> lines = tracePath.text.readLines()
        List<String> headers = lines.remove(0).split(delimiter)

        List<TraceRecord> traceRecords = []
        lines.each { String line ->
            TraceRecord traceRecord = new TraceRecord()
            List<String> entries = line.split(delimiter)
            headers.eachWithIndex { String key, Integer i ->
                Object value = entries[i]

                // Perform readable and raw value parsing
                value = switch(TraceRecord.FIELDS.get(key)) {
                    case 'mem' -> if (value.endsWith('B')) {
                        List<String> split = value.split(' ')
                        Quantity quantity = Converter.scaleUnits(split[0].toDouble(), split[1].dropRight(1), 'B', '')
                        quantity.value.toLong()
                    }
                    case 'date' -> { value.matches('\\d+') ? value : dateParser.parse(value).toInstant().toEpochMilli() }
                    case 'time' -> {
                        if(value.matches('\\d+')) { value }
                        else {
                            String numeric = value.find('\\d+[.]?\\d*')
                            Quantity quantity = Converter.scaleTime(numeric.toDouble(), value.replace(numeric, ''), 'ms')
                            quantity.value.toLong()
                        }
                    }
                    case 'perc' -> value.replace('%', '').toDouble()
                    case 'num' -> value.toDouble()
                    default -> value
                }

                // Include the entry into the trace record
                traceRecord.put(key, value)
            }

            // Add additional entries
            traceRecord.put('process', traceRecord.get('name'))

            // Add to collection of trace records
            traceRecords.add(traceRecord)
        }

        return traceRecords
    }

    /**
     * Calculate the CO2 footprint of all tasks in a trace file.
     *
     * @param tracePath Path to the trace file
     * @param renderFiles Whether the output files are saved. Only returns {@link CO2Record}s if disabled. Default: true
     * @param carbonIntensity The carbon intensity [g/kWh] that should be used for the carbon estimation
     * @param timeCIs A map of times linked to CI values. Can be used to infer the CI during the run which produced the trace file
     * @return
     */
    @Function
    List<CO2Record> calculateCO2(
            Path tracePath, Boolean renderFiles=true,
            Double carbonIntensity=null, Map<LocalDateTime, Number> timeCIs=null
    ){
        // Parse the trace file
        List<TraceRecord> traceRecords = parseTraceFile(tracePath)

        // Determine CI
        if (carbonIntensity) {
            observer.config.set('ci', carbonIntensity)
        }
        if (timeCIs) {
            observer.timeCiRecordCollector = new CiRecordCollector(observer.config, timeCIs as ConcurrentHashMap)
        }

        // Collect CO2Records from traces & optionally write the corresponding files
        List<CO2Record> co2Records = []
        traceRecords.each { TraceRecord traceRecord ->
            observer.aggregator = new CO2RecordAggregator()
            if (renderFiles) { observer.traceFile.create() }
            observer.startRecord(traceRecord)
            co2Records.add(observer.aggregateRecords(traceRecord))
        }

        if (renderFiles) { observer.renderFiles() }

        return co2Records
    }
}
