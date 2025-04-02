package nextflow.co2footprint

import com.sun.management.OperatingSystemMXBean
import groovy.util.logging.Slf4j
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

import java.lang.management.ManagementFactory

@Slf4j
/**
 * Class for computation of energy usage, CO2 emission and equivalences
 */
class CO2FootprintComputation {

    private final TDPDataMatrix tdpDataMatrix
    private final CIDataMatrix ciDataMatrix
    private final CO2FootprintConfig config

    /**
     * Instance for computation of energy usage, CO2 emission and equivalences
     * @param tdpDataMatrix Thermal design power Data Matrix
     * @param ciDataMatrix  Carbon intensity Data Matrix
     */
    CO2FootprintComputation (TDPDataMatrix tdpDataMatrix, CIDataMatrix ciDataMatrix, CO2FootprintConfig config) {
        this.tdpDataMatrix = tdpDataMatrix
        this.ciDataMatrix = ciDataMatrix
        this.config = config
    }

    /**
     * Core function to compute CO2 emissions for each task:
     * $C = t * (nc * Pc * uc + nm * Pm) * PUE * CI * 0.001$
     * as in https://doi.org/10.1002/advs.202100707
     * PSF: pragmatic scaling factor -> not used here since we aim at the CO2e of one pipeline run
     * Factor 0.001 needed to convert Pc and Pm from W to kW
     * @param taskID
     * @param trace
     * @param config
     * @return
     */
    CO2Record computeTaskCO2footprint(TaskId taskID, TraceRecord trace) {

        /**
         * Detect operating system
         */
        OperatingSystemMXBean OS = { (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean() }()

        /**
         * CPU model information
         */
        String cpuModel = config.getIgnoreCpuModel() ? trace.get('cpu_model') as String : 'default'
        TDPDataMatrix modelDataMatrix = tdpDataMatrix.matchModel(cpuModel)

        /**
         * Realtime of computation
         */
        BigDecimal runtime_ms = trace.get('realtime') as Double         // [ms]
        BigDecimal runtime_h = runtime_ms/(1000*60*60)                  // [h]

        /**
         * Factors of core power usage
         */
        BigDecimal numberOfCores = trace.get('cpus') as Integer         // [#]
        BigDecimal powerdrawPerCore = modelDataMatrix.getCoreTDP()      // [W/core]

        // uc: core usage factor (between 0 and 1)
        BigDecimal cpuUsage = trace.get('%cpu') as Double
        if ( cpuUsage == null ) {  // TODO: why is value null, because task was finished so fast that it was not captured? Or are there other reasons?
            log.warn(
                    'The reported CPU usage is null for at least one task.' +
                    'Assuming 100% usage for each requested CPU!'
            )
            cpuUsage = numberOfCores * 100  // Assuming requested cpus were used with 100%
        }

        if ( cpuUsage == 0.0 ) {
            log.warn("The reported CPU usage is 0.0 for task ${taskID}.")
        }
        BigDecimal coreUsage = cpuUsage / (100.0 * numberOfCores)

        /**
         * Factors of memory power usage
         */
        BigDecimal availableMemory = OS.getTotalMemorySize() as Long    // [bytes]
        BigDecimal requestedMemory = trace.get('memory') as Long        // [bytes]
        BigDecimal requiredMemory = trace.get('peak_rss') as Long       // [bytes]
        if ( requestedMemory == null || requiredMemory > requestedMemory) {
            log.warn(
                    "The required memory (${requiredMemory/(1024**3)} GB) for the task" +
                    " exceeds the requested memory (${requestedMemory/(1024**3)} GB)." +
                    "Setting requested to maximum available memory (${availableMemory/(1024**3)} GB)."
            )
            requestedMemory = availableMemory
        }

        BigDecimal memory = requestedMemory / 1024**3       // conversion to [GB]

        BigDecimal powerdrawMem  = config.getPowerdrawMem() // [W per GB]

        /**
         * Energy-related factors
         */
        BigDecimal pue = config.getPue()    // PUE: power usage effectiveness of datacenter [ratio] (>= 1.0)
        BigDecimal ci  = config.getCi()     // CI: carbon intensity [gCO2e kWh−1]

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
     */
    CO2EquivalencesRecord computeCO2footprintEquivalences(Double totalCO2) {
        BigDecimal gCO2 = totalCO2 as BigDecimal / 1000 as BigDecimal       // Conversion to [g] CO2
        String location = config.getLocation()

        BigDecimal carKilometers = gCO2 / 175
        if (location && (location != 'US' || !location.startsWith('US-'))) {
            carKilometers = gCO2 / 251
        }
        BigDecimal treeMonths = gCO2 / 917
        BigDecimal planePercent = gCO2 * 100 / 50000

        return new CO2EquivalencesRecord(
                carKilometers,
                treeMonths,
                planePercent
        )
    }
}
