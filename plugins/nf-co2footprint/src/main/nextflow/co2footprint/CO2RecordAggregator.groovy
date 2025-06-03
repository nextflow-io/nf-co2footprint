package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j


/**
 * Compute summary statistics of the accumulated CO2Records of several tasks
 */
@Slf4j
@CompileStatic
class CO2RecordAggregator {
    // Transformation method to the desired metric
    private final Map<String, Closure<Double>> metricExtractionFunctions

    // Entries
    private final Map<String, List<CO2Record>> processCO2Records = [:]

    CO2RecordAggregator( Map<String, Closure<Double>> metricExtractionFunctions=null )  {
        this.metricExtractionFunctions ?= metricExtractionFunctions ?: [
                co2e: { CO2Record co2record -> co2record.getCO2e() },
                energy: { CO2Record co2record -> co2record.getEnergyConsumption() }
        ]
    }

    /**
     * Adds a CO2Record to the list of records for the given process.
     *
     * @param co2record The CO2Record to add.
     * @param processName The name of the process to associate with the record.
     */
    void add( CO2Record co2record, String processName ) {
        if(processCO2Records.containsKey(processName)) {
            processCO2Records[processName].add(co2record)
        }
        else {
            processCO2Records[processName] = [co2record]
        }
    }

    /**
     * Class to store quantiles
     */
    class QuantileItem{
        private final CO2Record co2Record
        private final Number value

        CO2Record getCO2Record() { co2Record }
        Number getValue() { value }

        QuantileItem(CO2Record co2Record, Number value){
            this.co2Record = co2Record
            this.value = value
        }
    }

    /**
     * Calculate the q-th quantile
     *
     * @param items A list of CO2Records
     * @param q The q-th quantile. It must be a number between 0 & 1
     * @return A QuantileItem with the base record and the computed quantile value.
     */
    QuantileItem getQuantile(
            List<CO2Record> sortedCO2Records, double q, Closure<Double> transformFunction={ return it as Double}
    ) {
        assert sortedCO2Records, 'Argument items cannot be empty'
        assert q>=0 && q<=1, 'Quantile must be between 0 and 1'

        final double pos = q * (sortedCO2Records.size() - 1)
        final int lower = Math.floor(pos) as int
        final int upper = Math.ceil(pos) as int

        final Double value
        if (lower == upper) {
            value = transformFunction(sortedCO2Records[lower])
        } else {
            // linear interpolation
            value = transformFunction(sortedCO2Records[lower]) * (upper - pos) +
                    transformFunction(sortedCO2Records[upper]) * (pos - lower)
        }

        return new QuantileItem(sortedCO2Records[lower], value)
    }

    /**
     * Computes summary statistics for a list of CO2Record objects based on a given metric.
     *
     * @param co2Records A list of CO2Record objects to analyze.
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
    Map<String,?> computeStat(List<CO2Record> co2Records, Closure<Double> metricExtractionFunction) {

        final Map<String, ?> result = new LinkedHashMap<String,?>(12)
        final List<CO2Record> sortedCO2Records = co2Records.sort(
                { CO2Record co2record -> metricExtractionFunction(co2record) }
        )

        /*
            Unlike the Nextflow Report, we are not rounding the results nor discarding entries with all zeros.
            This decision is taken to avoid the loss of information in the report.
            Plots will show values of 0, making the representation of all processes consistent.
            This class reports all values in milli-unit to increase precision.
            Values are converted to the required units by CO2FootprintReportTemplate.js
        */

        QuantileItem quantileItem
        Double total = co2Records.sum {metricExtractionFunction.call(it) } as Double
        result.put('mean', (total / co2Records.size()) as double)
        ['min': 0d, 'q1': .25d, 'q2': .50d, 'q3': .75d, 'max': 1d].each { String key, double q ->
            quantileItem = getQuantile(sortedCO2Records, q, metricExtractionFunction)
            result.put("${key}Label" as String, quantileItem.co2Record.getName())
            result.put(key, quantileItem.value)
        }

        return result
    }

    /**
     * Computes summary statistics for each metric defined in the metricExtractionFunctions map.
     *
     * @param co2Records A list of CO2Record objects.
     * @return A map where each key is a metric name and each value is a map of summary statistics
     *         as returned by {@link #computeStat}.
     */
    Map<String, ?> computeStats(List<CO2Record> co2Records) {
        return this.metricExtractionFunctions.collectEntries {
            String metricName, Closure<Double> metricExtractionFunction ->
                [metricName, computeStat(co2Records, metricExtractionFunction)]
        } as Map<String, ?>
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
    List<Map<String, ?>> computeProcessStats() {
        Map<String, ?> stats
        List<Map<String, ?>> processStats = this.processCO2Records.collect {
            String processName, List<CO2Record> co2Records ->
                stats = [process: processName] as Map<String, ?>
                stats.putAll(computeStats(co2Records))
                return stats
        }
        return processStats
    }
}
