package nextflow.co2footprint.Parsers

import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Metrics.Quantity
import nextflow.trace.TraceRecord

import java.nio.file.Path
import java.text.SimpleDateFormat

/**
 * A parser for Nextflow execution trace files. If the format is human-readable, some values may be rounded.
 */
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
                            Quantity quantity = Converter.scaleUnits(split[0].toDouble(), split[1].dropRight(1), 'B', '')
                            quantity.value.toLong()
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
                                    Quantity timeStep = Converter.scaleTime(numeric.toDouble(), timePart.replace(numeric, ''), 'ms')
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

}
