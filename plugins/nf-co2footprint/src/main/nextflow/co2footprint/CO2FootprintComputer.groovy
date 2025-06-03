package nextflow.co2footprint

import nextflow.co2footprint.utils.HelperFunctions
import groovy.util.logging.Slf4j
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

@Slf4j
/**
 * Class for computation of energy usage, CO2 emission and equivalences
 */
class CO2FootprintComputer {

    private final TDPDataMatrix tdpDataMatrix
    private final CO2FootprintConfig config

    /**
     * Instance for computation of energy usage, CO2 emission and equivalences
     * @param tdpDataMatrix Thermal design power Data Matrix
     * @param ciDataMatrix  Carbon intensity Data Matrix
     * @param config Co2FootprintConfig configuration of the plugin
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

        if (requestedMemory == null) {
            Long availableMemory = HelperFunctions.getAvailableSystemMemory(taskID)
            log.warn("Requested memory is null for task ${taskID}. Setting to available memory (${availableMemory/(1024**3)} GB).")
            requestedMemory = availableMemory
        } else if (requiredMemory != null && requiredMemory > requestedMemory) {
            Long availableMemory = HelperFunctions.getAvailableSystemMemory(taskID)
            log.warn(
                "The required memory (${requiredMemory/(1024**3)} GB) for the task exceeds the requested memory (${requestedMemory/(1024**3)} GB). " +
                "Setting requested to maximum available memory (${availableMemory/(1024**3)} GB)."
            )
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

        energy = energy * 1000000       // Conversion to [mWh]
        co2e = co2e * 1000              // Conversion to [mg] CO2e

        return new CO2Record(
                energy,
                co2e,
                runtime_h,
                ci,
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
