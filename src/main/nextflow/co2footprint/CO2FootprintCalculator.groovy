package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Logging.Markers
import nextflow.co2footprint.Metrics.Bytes
import nextflow.co2footprint.Metrics.Duration
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.exception.MissingValueException
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

/**
 * Class for computation of energy usage, CO₂ emission, and equivalence metrics.
 */
@Slf4j
class CO2FootprintCalculator {

    // Holds CPU TDP data for different processors
    private final TDPDataMatrix tdpDataMatrix
    // Holds configuration parameters for CO₂ calculations
    private final CO2FootprintConfig config

    /**
     * Constructor for CO2FootprintCalculator.
     *
     * @param tdpDataMatrix  Data matrix with CPU TDP (Thermal Design Power) values.
     * @param config         Configuration object with plugin and calculation settings.
     */
    CO2FootprintCalculator(TDPDataMatrix tdpDataMatrix, CO2FootprintConfig config) {
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
    * @param trace   The TraceRecord containing task resource usage.
    * @param timeCiRecords Collector for carbon intensity records.
    * @param isPostRun Whether the computation is performed after the run (true) or during the run (false). Used to determine whether previous values are used if not set explicitly otherwise.
    * @return        CO2Record with energy consumption, CO₂ emissions, and task/resource details.
    */
    CO2Record computeTaskCO2footprint(TraceRecord trace, CiRecordCollector timeCiRecords, boolean isPostRun=false) {

        /* ===== CPU Information ===== */

        final String cpuModel = config.ignoreCpuModel ? 'default' : trace.get('cpu_model') as String

        // Runtime [h]
        final BigDecimal runtime_ms = getTraceOrDefault(trace, trace.taskId, 'realtime', 0, 'missing-realtime') as BigDecimal
        final BigDecimal runtime_h = Duration.of(runtime_ms, 'ms').scale('h').value as BigDecimal

        // Number of CPU cores
        Integer numberOfCores = getTraceOrDefault(trace, trace.taskId, 'cpus', 1, 'missing-cpus') as Integer

        // CPU usage: fraction of total requested cores
        BigDecimal cpuUsage = getTraceOrDefault(trace, trace.taskId, '%cpu', numberOfCores * 100, 'missing-%cpu') as BigDecimal
        if ( cpuUsage == 0.0 ) {
            log.warn(
                Markers.unique,
                "The reported CPU usage is 0.0 for task ${trace.taskId}.",
                'zero-cpu-usage-warning'
            )
        }
        numberOfCores = Math.max( Math.ceil(cpuUsage / 100), numberOfCores) as Integer // Ensure that the number of cores is at least ceil(%cpu/100)
        final BigDecimal coreUsage = cpuUsage / (100.0 * numberOfCores)

        // Per-core power draw: either custom polynomial model or TDP lookup [W/core]
        final Closure<Number> cpuPowerModel = useConfiguredOrPrevious(
                config, ['cpuPowerModel'], config.cpuPowerModel,
                trace, 'cpu_power_model', isPostRun
        )

        // Assigns powerdraw per core in the following order: 1. Custom polynomial model 2. TDP lookup based on CPU model 3. Previous value from trace
        final BigDecimal powerdrawPerCore
        if (isPostRun && !tdpDataMatrix.matchModel(cpuModel, false, false) && trace.containsKey('powerdraw_cpu')) {
            powerdrawPerCore = trace.get('powerdraw_cpu') as BigDecimal
        }
        else {
            powerdrawPerCore = tdpDataMatrix.matchModel(cpuModel).getLogicalCoreTDP()
        }

        /* ===== Memory Information ===== */

        final BigInteger requestedMemory = trace.get('memory') as BigInteger        // [bytes]
        final BigInteger maxRequiredMemory = trace.get('peak_rss') as BigInteger    // [bytes]

        // Assign the final memory value
        final BigDecimal memory

        // 1. Use requested memory if available
        if (requestedMemory != null) {
            memory = new Bytes(requestedMemory, '').scale('G').value
        }
        // 2. If missing, fall back to peak_rss
        else if (maxRequiredMemory != null) {
            memory = new Bytes(maxRequiredMemory, '').scale('G').value
            log.warn(Markers.unique,
                "Requested memory is null for task ${trace.taskId}. Using maximum consumed memory/`peak_rss` (${memory} GB) for CO₂e footprint computation.",
                'memory-is-null-warning')
        }
        // 3. If both missing, throw an error
        else {
            String message = "No requested memory and maximum consumed memory found for task ${trace.taskId}."
            log.error(message)
            throw new MissingValueException(message)
        }

        final BigDecimal powerdrawMem  = useConfiguredOrPrevious(
                config, ['powerdrawMem'], config.powerdrawMem,
                trace, 'powerdraw_memory', isPostRun
        ) // [W per GB]

        /* ===== Data Center Effectiveness and Carbon Intensity ===== */

         // PUE: power usage effectiveness of datacenter [ratio] (>= 1.0)
        final BigDecimal pue = useConfiguredOrPrevious(
                config, ['pue'], config.pue,
                trace, 'pue', isPostRun
        )

        // CI: carbon intensity [gCO₂e kWh−1]
        final BigDecimal ci = useConfiguredOrPrevious(
                config, ['ci', 'emApiKey'], timeCiRecords.getCi(trace),
                trace, 'carbon_intensity', isPostRun
        )

        // Personal energy mix based carbon intensity
        final BigDecimal ciMarket = useConfiguredOrPrevious(
                config, ['ciMarket'], config.ciMarket,
                trace, 'carbon_intensity_market', isPostRun
        )


        /* ===== Energy & Emission Calculation ===== */

        // Energy consumption [kWh]
        BigDecimal rawEnergyProcessor
        if (cpuPowerModel) {
            rawEnergyProcessor = runtime_h * numberOfCores * cpuPowerModel(coreUsage) * 0.001
        }
        else {
            rawEnergyProcessor = runtime_h * numberOfCores * powerdrawPerCore * coreUsage * 0.001
        }
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
            ciMarket,
            cpuUsage,
            memory,
            runtime_ms,
            numberOfCores,
            pue,
            powerdrawPerCore,
            powerdrawMem,
            cpuPowerModel as String,
            config.ignoreCpuModel ? 'Custom value' : cpuModel,
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
    static CO2EquivalencesRecord computeCO2footprintEquivalences(BigDecimal totalCO2) {
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
        return value != null ? value : defaultValue
    }

    /**
     * Resolves a calculation parameter by choosing between the current config and a value
     * stored in a previous run's trace output.
     *
     * In post-run mode, the input trace may be a CO2Record from a prior plugin run that already
     * contains the parameter values used in the original calculation (e.g. PUE, carbon intensity).
     * This method preserves those original values — unless the user explicitly overrides them
     * in the current config.
     *
     * In normal (integrated) mode, always returns the current config value.
     *
     * Example: a prior run used PUE=1.4, stored in the trace. If the user re-runs post-run
     * estimation without setting 'pue' in config, this returns 1.4 from the trace. If the user
     * explicitly sets pue=1.2 in config, this returns 1.2.
     *
     * @param config      Plugin configuration for the current run
     * @param configKeys  Config keys that, if explicitly set by the user, force using the config value
     * @param configValue The value from the current config (used when not in post-run, or when overridden)
     * @param trace       The input trace record, potentially containing values from a previous run
     * @param traceKey    The field name in the trace to look up the previous value
     * @param isPostRun   Whether we are in post-run mode
     * @return The previous run's value if in post-run mode and not overridden, otherwise the config value
     */
    static <T> T useConfiguredOrPrevious(
            CO2FootprintConfig config, List<String> configKeys, T configValue,
            TraceRecord trace, String traceKey, boolean isPostRun
    ){
        if (isPostRun && (!configKeys.collect({ String configKey -> configKey in config.usedKeys }).any()) && trace.containsKey(traceKey)) {
            return trace.get(traceKey) as T
        }
        else {
            return configValue
        }
    }
}
