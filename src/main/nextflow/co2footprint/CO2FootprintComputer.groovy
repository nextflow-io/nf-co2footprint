package nextflow.co2footprint

import nextflow.co2footprint.Logging.Markers
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Records.CiRecordCollector

import groovy.util.logging.Slf4j
import nextflow.exception.MissingValueException
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

/**
 * Class for computation of energy usage, CO₂ emission, and equivalence metrics.
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
    * Computes the CO₂ emissions and energy usage for a given Nextflow task.
    *
    * Calculation formula (from Green Algorithms, https://doi.org/10.1002/advs.202100707):
    *   CO₂e = t * (n_c * P_c * u_c + n_m * P_m) * PUE * CI * 0.001
    *     where:
    *   - t   : runtime [h]
    *   - n_c : number of CPU cores
    *   - P_c : per-core power draw [W]
    *   - u_c : average core utilization [0–1]
    *   - n_m : memory usage [GB]
    *   - P_m : per-GB memory power draw [W/GB]
    *   - PUE : power usage effectiveness [ratio ≥ 1.0]
    *   - CI  : carbon intensity [gCO₂e/kWh]
    *
    * Results:
    *   - Energy consumption in Wh
    *   - CO₂ emissions in gCO₂e (location-based and optional market-based)
    *
    * @param taskID  The Nextflow TaskId for this task.
    * @param trace   The TraceRecord containing task resource usage.
    * @param timeCiRecords Collector for carbon intensity records.
    * @return        CO2Record with energy consumption, CO₂ emissions, and task/resource details.
    */
    CO2Record computeTaskCO2footprint(TraceRecord trace, TaskId taskID=trace.taskId, CiRecordCollector timeCiRecords) {

        /* ===== CPU Information ===== */

        final String cpuModel = config.value('ignoreCpuModel') ? 'default' : trace.get('cpu_model') as String

        // Runtime [h]
        final BigDecimal runtime_h = (getTraceOrDefault(trace, taskID, 'realtime', 0, 'missing-realtime') as BigDecimal) / (1000 * 60 * 60)

        // Number of CPU cores
        final Integer numberOfCores = getTraceOrDefault(trace, taskID, 'cpus', 1, 'missing-cpus') as Integer

        // CPU usage: fraction of total requested cores
        BigDecimal cpuUsage = getTraceOrDefault(trace, taskID, '%cpu', numberOfCores * 100, 'missing-%cpu') as BigDecimal
        if ( cpuUsage == 0.0 ) {
            log.warn(
                Markers.unique,
                "The reported CPU usage is 0.0 for task ${taskID}.",
                'zero-cpu-usage-warning'
            )
        }
        final BigDecimal coreUsage = cpuUsage / (100.0 * numberOfCores)

        // Per-core power draw: either custom polynomial model or TDP lookup [W/core]
        final List<Number> cpuPowerModel = config.value('cpuPowerModel')
        final BigDecimal powerdrawPerCore = cpuPowerModel ? getPowerDrawFromModel(cpuPowerModel, coreUsage) : tdpDataMatrix.matchModel(cpuModel).getCoreTDP()

        /* ===== Memory Information ===== */

        final Long requestedMemory = trace.get('memory') as Long        // [bytes]
        final Long maxRequiredMemory = trace.get('peak_rss') as Long    // [bytes]

        // Assign the final memory value
        final BigDecimal memory

        // 1. Use requested memory if available
        if (requestedMemory != null) {
            memory = Converter.scaleUnits(requestedMemory, '', 'B', 'G').value
        }
        // 2. If missing, fall back to peak_rss
        else if (maxRequiredMemory != null) {
            memory = Converter.scaleUnits(maxRequiredMemory, '', 'B', 'G').value
            log.warn(Markers.unique,
                "Requested memory is null for task ${taskID}. Using maximum consumed memory/`peak_rss` (${memory} GB) for CO₂e footprint computation.",
                'memory-is-null-warning')
        }
        // 3. If both missing, throw an error
        else {
            String message = "No requested memory and maximum consumed memory found for task ${taskID}."
            log.error(message)
            throw new MissingValueException(message)
        }

        final BigDecimal powerdrawMem  = config.value('powerdrawMem') // [W per GB]

        /* ===== Data Center Effectiveness and Carbon Intensity ===== */

         // PUE: power usage effectiveness of datacenter [ratio] (>= 1.0)
        final BigDecimal pue = config.value('pue')

        // CI: carbon intensity [gCO₂e kWh−1]
        final BigDecimal ci = timeCiRecords.getCi(trace)

        // Personal energy mix based carbon intensity
        final Double ciMarket = config.value('ciMarket')


        /* ===== Energy & Emission Calculation ===== */

        // Energy consumption [kWh]
        BigDecimal rawEnergyProcessor = runtime_h * numberOfCores * powerdrawPerCore * coreUsage * 0.001
        BigDecimal rawEnergyMemory = runtime_h * memory * powerdrawMem * 0.001
        BigDecimal energy = pue * (rawEnergyProcessor + rawEnergyMemory)

        // Resulting CO₂ emissions
        BigDecimal co2e = (energy * ci) // Emissions in CO2 equivalents [g] CO2e
        BigDecimal co2eMarket = ciMarket ? (energy * ciMarket) : null

        return new CO2Record(
            trace,
            energy,
            co2e,
            co2eMarket,
            ci,
            cpuUsage,
            memory as Long,
            runtime_h,
            numberOfCores as Integer,
            powerdrawPerCore,
            config.value('ignoreCpuModel') ? 'Custom value' : cpuModel,
            rawEnergyProcessor,
            rawEnergyMemory,
        )
    }

    /**
     * The following values were taken from the Green Algorithms publication (https://doi.org/10.1002/advs.202100707):
     * The estimated emission of the average passenger car is 175 gCO₂e/Km in Europe and 251 gCO₂/Km in the US
     * The estimated emission of flying on a jet aircraft in economy class is between 139 and 244 gCO₂e/Km
     * The estimated sequestered CO₂ of a mature tree is ~1 Kg per month (917 g)
     * A reference flight Paris to London spends 50000 gCO₂
     * @param totalCO2 Total CO₂ equivalents that were emitted
     * @return CO2EquivalencesRecord with estimations for sensible comparisons
     */
    static CO2EquivalencesRecord computeCO2footprintEquivalences(Double totalCO2) {
        final BigDecimal carKilometers = totalCO2 / 175
        final BigDecimal treeMonths = totalCO2 / 917
        final BigDecimal planePercent = totalCO2 * 100 / 50000

        return new CO2EquivalencesRecord(
                carKilometers,
                treeMonths,
                planePercent
        )
    }

    /**
     * Retrieves a value from the trace record by key, or returns a default if the value is missing.
     * If the value is `null`, a warning is logged (once per unique dedupKey).
     *
     * @param trace        The TraceRecord containing task metrics.
     * @param taskID       The ID of the current task (used for logging).
     * @param key          The trace field key to retrieve (e.g. 'realtime', '%cpu', 'rss').
     * @param defaultValue The fallback value to return if the key is missing or null.
     * @param dedupKey     A unique identifier to deduplicate warning messages in the logs.
     * @return             The value from the trace if present, otherwise the defaultValue.
     */
    static Object getTraceOrDefault(TraceRecord trace, TaskId taskID, String key, Object defaultValue, String dedupKey) {
        def value = trace.get(key)
        if (value == null) {
            String warnMessage
            if (key == '%cpu') {
                def numCores = (defaultValue instanceof Number) ? defaultValue / 100 : defaultValue
                warnMessage = "Missing trace value '${key}' for task ${taskID}, using default: 100% for all ${numCores} cores."
            } else {
                warnMessage = "Missing trace value '${key}' for task ${taskID}, using default: ${defaultValue}."
            }
            log.warn(
                    Markers.unique,
                    warnMessage,
                    dedupKey
            )
        }
        return value ?: defaultValue
    }

    /**
    * Computes CPU power draw using the configured polynomial model.
    *
    * @param coefficients List of polynomial coefficients (highest degree first), as Double or BigDecimal.
    * @param coreUsage CPU usage in percent (0–100).
    * @return Estimated power draw [W/core], or null if no model configured.
    */
    static BigDecimal getPowerDrawFromModel(List<Number> coefficients, BigDecimal coreUsage) {
        BigDecimal power = 0.0
        Integer degree = coefficients.size() - 1

        coefficients.eachWithIndex { Number c, Integer i ->
            power += (c as BigDecimal) * coreUsage ** (degree - i)
        }

        return power
    }

}
