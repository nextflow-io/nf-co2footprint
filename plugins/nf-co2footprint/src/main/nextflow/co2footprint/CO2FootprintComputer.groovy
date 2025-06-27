package nextflow.co2footprint

import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.utils.HelperFunctions
import groovy.util.logging.Slf4j
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
        final String cpuModel = config.getIgnoreCpuModel() ? 'default' : trace.get('cpu_model') as String

        /**
         * Realtime of computation
         */
        final BigDecimal runtime_h = (HelperFunctions.getTraceOrDefault(trace, taskID, 'realtime', 0) as BigDecimal) / (1000*60*60) // [h]

        /**
         * Factors of core power usage
         */
        final Integer numberOfCores = HelperFunctions.getTraceOrDefault(trace, taskID, 'cpus', 1) as Integer           // [#]
        final BigDecimal powerdrawPerCore = tdpDataMatrix.matchModel(cpuModel).getCoreTDP()            // [W/core]

        // uc: core usage factor (between 0 and 1)
        BigDecimal cpuUsage = HelperFunctions.getTraceOrDefault(trace, taskID, '%cpu', numberOfCores * 100) as BigDecimal

        if ( cpuUsage == 0.0 ) {
            log.warn("The reported CPU usage is 0.0 for task ${taskID}.")
        }

        final BigDecimal coreUsage = cpuUsage / (100.0 * numberOfCores)

        /**
         * Factors of memory power usage
         */
        Long requestedMemory = trace.get('memory') as Long        // [bytes]
        final Long requiredMemory = trace.get('peak_rss') as Long // [bytes]

        // Check if requested memory is missing
        if (requestedMemory == null) {
            // If missing, get the available system memory
            Long availableMemory = HelperFunctions.getAvailableSystemMemory(taskID)
            // Warn that requested memory was null and fallback is used
            log.warn("Requested memory is null for task ${taskID}. Setting to available memory (${availableMemory/(1024**3)} GB).")
            // Use available system memory as the requested memory
            requestedMemory = availableMemory
        }
        // If peak memory usage (requiredMemory) is known and exceeds the requested memory
        else if (requiredMemory != null && requiredMemory > requestedMemory) {
            // Get the available system memory
            Long availableMemory = HelperFunctions.getAvailableSystemMemory(taskID)
            // Warn that required memory exceeded requested, so fallback is used
            log.warn(
                "The required memory (${requiredMemory/(1024**3)} GB) for the task exceeds the requested memory (${requestedMemory/(1024**3)} GB). " +
                "Setting requested to maximum available memory (${availableMemory/(1024**3)} GB)."
            )
            // Use available system memory as the requested memory
            requestedMemory = availableMemory
        }

        final BigDecimal memory = requestedMemory / 1024**3 // conversion to [GB]

        final BigDecimal powerdrawMem  = config.getPowerdrawMem() // [W per GB]

        /**
         * Energy-related factors
         */
        final BigDecimal pue = config.getPue()    // PUE: power usage effectiveness of datacenter [ratio] (>= 1.0)

        // CI: carbon intensity [gCO2e kWh−1]
        final BigDecimal ci = config.getCi()

        // Personal energy mix based carbon intensity
        final Double personalEnergyMixCi = config.getPersonalEnergyMixCi()

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
        BigDecimal personalEnergyMixco2e = personalEnergyMixCi ? (energy * personalEnergyMixCi) : null

        energy = energy * 1000000       // Conversion to [mWh]
        co2e = co2e * 1000              // Conversion to [mg] CO2e

        return new CO2Record(
                energy,
                co2e,
                personalEnergyMixco2e,
                runtime_h,
                ci,
                personalEnergyMixCi,
                numberOfCores as Integer,
                powerdrawPerCore,
                cpuUsage,
                memory as Long,
                trace.get('name') as String,
                config.getIgnoreCpuModel() ? 'Custom value' : cpuModel
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
        final BigDecimal gCO2 = totalCO2 as BigDecimal / 1000 as BigDecimal       // Conversion to [g] CO2

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
