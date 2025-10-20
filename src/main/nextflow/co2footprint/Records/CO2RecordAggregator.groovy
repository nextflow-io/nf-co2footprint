package nextflow.co2footprint.Records

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.trace.TraceRecord


/**
 * Compute summary statistics of the accumulated CO2Records of several tasks
 */
@Slf4j
@CompileStatic
class CO2RecordAggregator {
    // Transformation method to the desired metric
    private final Map<String, Closure<Double>> metricExtractionFunctions

    // Stores lists of trace/CO2 record maps for each process name
    private final Map<String, List<Map<String, TraceRecord>>> processCO2Records = [:]

    /**
     * Constructs a CO2RecordAggregator with optional custom metric extraction functions.
     *
     * @param metricExtractionFunctions
     *        Optional map of metric names to closures for extracting metric values.
     *        If not provided, defaults for common CO₂ and energy metrics are used.
     */
    CO2RecordAggregator( Map<String, Closure<Double>> metricExtractionFunctions=null )  {
        this.metricExtractionFunctions ?= metricExtractionFunctions ?: [
                co2e: { TraceRecord traceRecord, CO2Record co2Record -> co2Record.co2e },
                energy: { TraceRecord traceRecord, CO2Record co2Record -> co2Record.energy },
                co2e_non_cached: { TraceRecord traceRecord, CO2Record co2Record ->
                    traceRecord.getStore()['status'] != 'CACHED' ? co2Record.co2e : null
                },
                energy_non_cached: { TraceRecord traceRecord, CO2Record co2Record ->
                    traceRecord.getStore()['status'] != 'CACHED' ? co2Record.energy : null
                },
                co2e_market: {
                    TraceRecord traceRecord, CO2Record co2Record -> co2Record.co2eMarket
                },
                energy_market: {
                    TraceRecord traceRecord, CO2Record co2Record -> co2Record.energy
                },
        ]
    }

    /**
     * Adds a CO2Record to the list of records for the given process.
     *
     * @param traceRecord The TraceRecord to add
     * @param co2record The CO2Record to add
     */
    void add( TraceRecord traceRecord, CO2Record co2Record) {
        String processName = traceRecord.getSimpleName()
        Map<String, TraceRecord> record = [ traceRecord: traceRecord, co2Record: co2Record ] as Map<String, TraceRecord>

        if(processCO2Records.containsKey(processName)) {
            processCO2Records[processName].add(record)
        }
        else {
            processCO2Records[processName] = [record]
        }
    }

    /**
     * Class to store quantiles
     */
    class QuantileItem{
        private final Map<String, TraceRecord> record
        private final Number value

        Map<String, TraceRecord> getRecord() { record }
        Number getValue() { value }

        QuantileItem(Map<String, TraceRecord> record, Number value){
            this.record = record
            this.value = value
        }
    }

    /**
     * Calculate the q-th quantile
     *
     * @param items A list of mapped trace & CO2 records
     * @param q The q-th quantile. It must be a number between 0 & 1
     * @return A QuantileItem with the base record and the computed quantile value.
     */
    QuantileItem getQuantile(
            List<Map<String, TraceRecord>> sortedRecords, double q, Closure<Double> transformFunction={ return it as Double }
    ) {
        assert sortedRecords, 'Argument items cannot be empty'
        assert q>=0 && q<=1, 'Quantile must be between 0 and 1'

        final double pos = q * (sortedRecords.size() - 1)
        final int lower = Math.floor(pos) as int
        final int upper = Math.ceil(pos) as int

        final Double value
        if (lower == upper) {
            value = transformFunction(sortedRecords[lower].get('traceRecord'), sortedRecords[lower].get('co2Record'))
        } else {
            // linear interpolation
            value = transformFunction(sortedRecords[lower].get('traceRecord'), sortedRecords[lower].get('co2Record')) * (upper - pos) +
                    transformFunction(sortedRecords[upper].get('traceRecord'), sortedRecords[upper].get('co2Record')) * (pos - lower)
        }

        return new QuantileItem(sortedRecords[lower], value)
    }

    /**
     * Computes summary statistics for a list of CO2Record objects based on a given metric.
     *
     * @param items A list of mapped trace & CO2 records
     * @param metricExtractionFunction A closure that extracts a numeric value from each CO2Record.
     * @return
     *      A {@link Map} holding the summary containing the following stats:
     *      - min: minimal value
     *      - q1: first quartile
     *      - q2: second quartile ie. median
     *      - q3: third quartile
     *      - max: maximum value
     *      - mean: average value
     *      - minLabel: label fot the task reporting the min value
     *      - q1Label: label fot the task reporting the q1 value
     *      - q2Label: label fot the task reporting the q2 value
     *      - q3Label: label fot the task reporting the q3 value
     *      - maxLabel: label fot the task reporting the max value
     */
    Map<String,?> computeStat(List<Map<String, TraceRecord>> records, Closure<Double> metricExtractionFunction) {

        final Map<String, ?> result = new LinkedHashMap<String,?>(12)

        // Remove all records that are null (and thus marked to be sorted out)
        List<Map<String, TraceRecord>> records2 = records.findAll { Map<String, TraceRecord> record ->
            metricExtractionFunction(record['traceRecord'], record['co2Record']) != null
        }

        if (records2) {
            final List<Map<String, TraceRecord>> sortedRecords = records2.sort(
                    { Map<String, TraceRecord> record -> metricExtractionFunction(record['traceRecord'], record['co2Record']) }
            )

            /*
                Unlike the Nextflow Report, we are not rounding the results nor discarding entries with all zeros.
                This decision is taken to avoid the loss of information in the report.
                Plots will show values of 0, making the representation of all processes consistent.
                This class reports all values in milli-unit to increase precision.
                Values are converted to the required units by CO2FootprintReportTemplate.js
            */

            QuantileItem quantileItem

            // List all values
            List<Double> allValues = sortedRecords.collect { Map<String, TraceRecord> record ->
                metricExtractionFunction.call(record['traceRecord'], record['co2Record'])
            }
            result.put('all', allValues)

            // Add total
            Double total = allValues.sum() as Double
            result.put('total', total as double)

            // Add mean
            result.put('mean', (total / sortedRecords.size()) as double)

            // Add quantiles
            ['min': 0d, 'q1': .25d, 'q2': .50d, 'q3': .75d, 'max': 1d].each { String key, double q ->
                quantileItem = getQuantile(sortedRecords, q, metricExtractionFunction)
                result.put("${key}Label" as String, (quantileItem.getRecord().get('co2Record') as CO2Record).getName())
                result.put(key, quantileItem.value)
            }
        }

        return result
    }

    /**
     * Computes summary statistics for each metric defined in the metricExtractionFunctions map.
     *
     * @param items A list of mapped trace & CO2 records
     * @return A map where each key is a metric name and each value is a map of summary statistics
     *         as returned by {@link #computeStat}.
     */
    Map<String, Map<String, ?>> computeStats(List<Map<String, TraceRecord>> records) {
        return this.metricExtractionFunctions.collectEntries {
            String metricName, Closure<Double> metricExtractionFunction ->
                [metricName, computeStat(records, metricExtractionFunction)]
        }
    }

    /**
     * Computes metric statistics for each individual process.
     *
     * For every process in processCO2Records, this method calculates summary stats (min, quartiles,
     * mean, max, etc.) for each defined metric, and returns them along with the process name.
     *
     * @return A list of maps, each containing:
     *         - 'process': the name of the process
     *         - one entry per metric (e.g., 'energy', 'emissions'), each with its corresponding stats map
     *         The format looks like this: [ [process: processName1:, metricName1: [entryKey1: value, ...],...],...]
     */
    Map<String, Map<String, Map<String, ?>>> computeProcessStats() {
        return this.processCO2Records.collectEntries {
            String processName, List<Map<String, TraceRecord>> records ->
                [processName, computeStats(records)]
        }
    }
}
