package nextflow.co2footprint

import nextflow.co2footprint.Logging.Markers
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.utils.HelperFunctions
import nextflow.co2footprint.utils.Converter
import groovy.util.logging.Slf4j
import nextflow.exception.MissingValueException
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

/**
 * Class for computation of energy usage, CO2 emission, and equivalence metrics.
 */
@Slf4j
class CO2FootprintComputer {

    // Holds CPU TDP data for different processors
    private final TDPDataMatrix tdpDataMatrix
    // Holds configuration parameters for CO₂ calculations
    private final CO2FootprintConfig config

    /**
     * Constructor for CO2FootprintComputer.
     *
     * @param tdpDataMatrix  Data matrix with CPU TDP (Thermal Design Power) values.
     * @param config         Configuration object with plugin and calculation settings.
     */
    CO2FootprintComputer(TDPDataMatrix tdpDataMatrix, CO2FootprintConfig config) {
        this.tdpDataMatrix = tdpDataMatrix
        this.config = config
    }


    /**
    * Computes the CO2 emissions and energy usage for a given Nextflow task.
    *
    * Calculation formula (from Green Algorithms, https://doi.org/10.1002/advs.202100707):
    *   CO2e = t * (n_c * P_c * u_c + n_m * P_m) * PUE * CI * 0.001
    *     where:
    *       t   = runtime in hours
    *       n_c = number of CPU cores
    *       P_c = power draw per core (W)
    *       u_c = CPU usage (fraction)
    *       n_m = memory used (GB)
    *       P_m = power draw per GB memory (W)
    *       PUE = power usage effectiveness (datacenter efficiency)
    *       CI  = carbon intensity (gCO2e/kWh)
    *   The result is converted to mWh (energy) and mgCO2e (emissions).
    *
    * Memory assignment logic:
    *   - Uses requested memory if available.
    *   - If requested memory is null, uses available system memory.
    *   - If peak memory (RSS) exceeds requested, uses available system memory.
    *
    * @param taskID  The Nextflow TaskId for this task.
    * @param trace   The TraceRecord containing task resource usage.
    * @return        CO2Record with energy consumption, CO2 emissions, and task/resource details.
    */
    CO2Record computeTaskCO2footprint(TaskId taskID, TraceRecord trace) {

        /**
         * CPU model information
         */
        final String cpuModel = config.value('ignoreCpuModel') ? 'default' : trace.get('cpu_model') as String

        /**
         * Realtime of computation
         */
        final BigDecimal runtime_h = (HelperFunctions.getTraceOrDefault(trace, taskID, 'realtime', 0, 'missing-realtime') as BigDecimal) / (1000 * 60 * 60) // [h]

        /**
         * Factors of core power usage
         */
        final Integer numberOfCores = HelperFunctions.getTraceOrDefault(trace, taskID, 'cpus', 1, 'missing-cpus') as Integer // [#]
        final BigDecimal powerdrawPerCore = tdpDataMatrix.matchModel(cpuModel).getCoreTDP()            // [W/core]

        // uc: core usage factor (between 0 and 1)
        BigDecimal cpuUsage = HelperFunctions.getTraceOrDefault(trace, taskID, '%cpu', numberOfCores * 100, 'missing-%cpu') as BigDecimal
        
        if ( cpuUsage == 0.0 ) {
            log.warn(
                Markers.unique,
                "The reported CPU usage is 0.0 for task ${taskID}.",
                'zero-cpu-usage-warning'
            )
        }

        final BigDecimal coreUsage = cpuUsage / (100.0 * numberOfCores)

        /**
         * Factors of memory power usage
         */
        final Long requestedMemory = trace.get('memory') as Long        // [bytes]
        final Long maxRequiredMemory = trace.get('peak_rss') as Long    // [bytes]

        // Assign the final memory value
        final BigDecimal memory
        if (requestedMemory == null) {
            if (maxRequiredMemory == null) {
                String message = "No requested memory and maximum consumed memory found for task ${taskID}."
                log.error(message)
                throw new MissingValueException(message)
            } else {
                memory = Converter.scaleUnits(maxRequiredMemory, '', 'B', 'G').value
                log.warn(
                    Markers.unique,
                    "Requested memory is null for task ${taskID}. Using maximum consumed memory/`peak_rss` (${memory} GB) for CO₂e footprint computation.",
                    'memory-is-null-warning'
                )
            }
        } else {
            memory = Converter.scaleUnits(requestedMemory, '', 'B', 'G',).value
        }

        final BigDecimal powerdrawMem  = config.value('powerdrawMem') // [W per GB]

        /**
         * Energy-related factors
         */
        final BigDecimal pue = config.value('pue')    // PUE: power usage effectiveness of datacenter [ratio] (>= 1.0)

        // CI: carbon intensity [gCO2e kWh−1]
        final BigDecimal ci = config.value('ci')

        // Personal energy mix based carbon intensity
        final Double ciMarket = config.value('ciMarket')

        /**
         * Calculate energy consumption [kWh]
         */
        BigDecimal energy = pue * (
                runtime_h * (
                        numberOfCores * powerdrawPerCore * coreUsage +
                        memory * powerdrawMem
                ) * 0.001
        )

        /*
         * Resulting CO2 emission
         */
        BigDecimal co2e = (energy * ci) // Emissions in CO2 equivalents [g] CO2e
        BigDecimal co2eMarket = ciMarket ? (energy * ciMarket) : null

        energy = energy * 1000000       // Conversion to [mWh]
        co2e = co2e * 1000              // Conversion to [mg] CO2e

        return new CO2Record(
                energy,
                co2e,
                co2eMarket,
                runtime_h,
                ci,
                numberOfCores as Integer,
                powerdrawPerCore,
                cpuUsage,
                memory as Long,
                trace.get('name') as String,
                config.value('ignoreCpuModel') ? 'Custom value' : cpuModel
        )
    }

    /**
     * The following values were taken from the Green Algorithms publication (https://doi.org/10.1002/advs.202100707):
     * The estimated emission of the average passenger car is 175 gCO2e/Km in Europe and 251 gCO2/Km in the US
     * The estimated emission of flying on a jet aircraft in economy class is between 139 and 244 gCO2e/Km
     * The estimated sequestered CO2 of a mature tree is ~1 Kg per month (917 g)
     * A reference flight Paris to London spends 50000 gCO2
     * @param totalCO2 Total CO2 equivalents that were emitted
     * @return CO2EquivalencesRecord with estimations for sensible comparisons
     */
    static CO2EquivalencesRecord computeCO2footprintEquivalences(Double totalCO2) {
        final BigDecimal gCO2 = totalCO2 as BigDecimal / 1000 as BigDecimal // [gCO2]

        final BigDecimal carKilometers = gCO2 / 175
        final BigDecimal treeMonths = gCO2 / 917
        final BigDecimal planePercent = gCO2 * 100 / 50000

        return new CO2EquivalencesRecord(
                carKilometers,
                treeMonths,
                planePercent
        )
    }
}
