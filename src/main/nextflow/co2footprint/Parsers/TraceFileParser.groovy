package nextflow.co2footprint.Parsers

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Metrics.Bytes
import nextflow.co2footprint.Metrics.Duration
import nextflow.co2footprint.Metrics.Metric
import nextflow.trace.TraceRecord

import java.nio.file.Path
import java.text.SimpleDateFormat

/**
 * A parser for Nextflow execution trace files. If the format is human-readable, some values may be rounded.
 */
@Slf4j
class TraceFileParser {
    /**
     * Parse the content of a Nextflow trace file in its raw and readable format.
     *
     * @param tracePath Path to the trace file
     * @param delimiter Delimiter of the trace file, default: \t
     * @return A list of all task-specific trace records inferred from the trace file
     */
    static List<TraceRecord> parseExecutionTraceFile(Path tracePath, String delimiter='\t') {
        SimpleDateFormat dateParser = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss.SSS')
        List<String> lines = tracePath.text.readLines()
        List<String> headers = lines.remove(0).split(delimiter)

        validateExecutionTraceFile(headers)

        List<TraceRecord> traceRecords = []
        lines.each { String line ->
            TraceRecord traceRecord = new TraceRecord()
            List<String> entries = line.split(delimiter)
            headers.eachWithIndex { String key, Integer i ->
                Object value = entries[i]

                // Perform readable and raw value parsing
                if (value == TraceRecord.NA) {
                    value = null
                }
                else {
                    value = switch (TraceRecord.FIELDS.get(key)) {
                        case 'mem' -> if (value.endsWith('B')) {
                            List<String> split = value.split(' ')
                            Metric<BigDecimal> bytes = Bytes.of(split[0].toDouble(), split[1].dropRight(1)).scale('')
                            bytes.value.toLong()
                        } else {
                            value.toLong()
                        }
                        case 'date' -> {
                            value.matches('\\d+') ? value.toLong() : dateParser.parse(value).toInstant().toEpochMilli()
                        }
                        case 'time' -> {
                            if (value.matches('\\d+')) {
                                value.toLong()
                            } else {
                                Long time = 0
                                value.split(' ').each { String timePart ->
                                    String numeric = timePart.find('\\d+[.]?\\d*')
                                    Metric<BigDecimal> timeStep = Duration.of(numeric.toDouble(), timePart.replace(numeric, '')).scale('ms')
                                    time += timeStep.value.toLong()
                                }
                                time
                            }
                        }
                        case 'perc' -> value.replace('%', '').toDouble()
                        case 'num' -> value.toDouble()
                        default -> value
                    }
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

    static boolean validateExecutionTraceFile(List<String> headers) {
        List<String> taskHeaders = ['task_id', 'status', 'name', 'realtime']
        taskHeaders.each { String header ->
            if(!headers.contains(header)){
                log.warn("Task-associated header ${header} missing in parsed trace file. Please provide values to ${taskHeaders}.")
                return false
            }
        }

        List<String> cpuHeaders = ['cpus', '%cpu', 'cpu_model']
        cpuHeaders.each { String header ->
            if(!headers.contains(header)){
                log.warn("CPU header ${header} missing in parsed trace file. Please provide values to ${cpuHeaders}.")
                return false
            }
        }
        if( !(headers.contains('memory') || headers.contains('peak_rss')) ){
            log.warn("Please provide `memory` or `peak_rss` in the parsed trace file.")
            return false
        }
        return true
    }
}
