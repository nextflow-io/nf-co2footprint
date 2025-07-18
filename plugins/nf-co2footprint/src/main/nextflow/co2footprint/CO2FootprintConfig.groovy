package nextflow.co2footprint


import groovy.util.logging.Slf4j
import nextflow.co2footprint.Config.BaseConfig
import nextflow.co2footprint.DataContainers.DataMatrix
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.CIValueComputer
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.trace.TraceHelper

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration class for CO₂ footprint calculations.
 *
 * It extracts values from a configuration map and sets up all relevant parameters,
 * such as output file names, carbon intensity, PUE, memory and CPU power draw, and machine type.
 * Users can customize these values in the Nextflow config file under the `co2footprint` block.
 *
 * Example usage in config:
 * co2footprint {
 *     traceFile = "co2footprint_trace.txt"
 *     summaryFile = "co2footprint_summary.txt"
 *     ci = 300
 *     pue = 1.4
 *     powerdrawMem = 0.67
 * }
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
@Slf4j
class CO2FootprintConfig extends BaseConfig {
    // Helper variables
    private final String timestamp = TraceHelper.launchTimestampFmt()
    private final List<String> supportedMachineTypes = ['local', 'compute cluster', 'cloud']

    void initializeParameters() {
        addParameter(
                ['traceFile', { -> "co2footprint_trace_${timestamp}.txt"}],
                [returnType: String, allowedTypes: [String, Closure<GString>, GString, Path], description: 'trace file']
        )
        addParameter(
                ['summaryFile', { -> "co2footprint_summary_${timestamp}.txt"}],
                [returnType: String, allowedTypes: [String, Closure<GString>, GString, Path], description: 'summary file']
        )
        addParameter(
                ['reportFile', { -> "co2footprint_report_${timestamp}.html"}],
                [returnType: String, allowedTypes: [String, Closure<GString>, GString, Path], description: 'report file']
        )
        addParameter(
                ['location'],
                [returnType: String, description: 'location code']
        )
        addParameter(
                ['ci'],
                [allowedTypes: [Double, BigDecimal, Closure<Double>], description: 'location-based carbon intensity']
        )
        addParameter(
                ['ciMarket'],
                [allowedTypes: [Double, BigDecimal, Closure<Double>], description: 'market-based carbon intensity']
        )
        addParameter(
                ['emApiKey'],
                [returnType: String, description: 'API key for Electricity Maps']
        )
        addParameter(
                ['pue'],
                [returnType: Double, allowedTypes: [Double, BigDecimal], description: 'power usage effectiveness efficiency, coefficient of the data centre']
        )
        addParameter(
                ['powerdrawMem', 0.3725],
                [returnType: Double, allowedTypes: [Double, BigDecimal], description: 'Consumption of power by the memory [W/GB]']
        )
        addParameter(
                ['ignoreCpuModel', false],
                [returnType: Boolean, description: 'Set to use the default TDP']
        )
        addParameter(
                ['powerdrawCpuDefault'],
                [returnType: Double, allowedTypes: [Double, BigDecimal], description: 'Consumption of power by the CPU [W/core]']
        )
        addParameter(
                ['customCpuTdpFile'],
                [returnType: String, description: 'Path to a custom TDP file in CSV format']
        )
        addParameter(
                ['machineType'],
                [returnType: String, description: 'Type of computer on which the workflow is run [\'local\', \'compute cluster\', \'cloud\']']
        )
    }

    // Getter methods for config values
    String getTraceFile() { get('traceFile') }
    String getSummaryFile() { get('summaryFile') }
    String getReportFile() { get('reportFile') }
    String getLocation() { get('location') }
    Double getCi() { evaluate('ci') }
    Double getCiMarket() { evaluate('ciMarket') }
    String getEmApiKey() { get('emApiKey')}
    Double getPue() { get('pue') as Double }
    Boolean getIgnoreCpuModel() { get('ignoreCpuModel') }
    Double getPowerdrawCpuDefault() { get('powerdrawCpuDefault') as Double }
    Double getPowerdrawMem() { get('powerdrawMem') as Double }
    String getCustomCpuTdpFile() { get('customCpuTdpFile') }
    String getMachineType()  { get('machineType') }

    /**
     * Loads configuration from a map and sets up defaults and fallbacks.
     * Also sets up CPU and CI data sources and assigns machine type and PUE.
     *
     * @param configMap   Map of configuration values (from Nextflow config)
     * @param cpuData     TDPDataMatrix with CPU power draw data
     * @param ciData      CIDataMatrix with carbon intensity data
     * @param processMap  Map with process/executor info
     */
    CO2FootprintConfig(Map<String, Object> configMap, TDPDataMatrix cpuData, CIDataMatrix ciData, Map<String, Object> processMap) {
        // Initialization of the Configuration in order:
        super()                     // - Methods & Constants through super()
        initializeParameters()      // - Parameters
        fill(configMap)             // - Mapping
        setDefaults([], false)  // - Initializing rest with default (function)


        // Determine the carbon intensity (CI) value
        if (ci == null) {
            CIValueComputer ciValueComputer = new CIValueComputer(emApiKey, location, ciData)
            // ci is either set to a Closure (in case the electricity maps API is used) or to a Double (in the other cases)
            // The closure is invoked each time the CO2 emissions are calculated (for each task) to make a new API call to update the real time ci value.
            set('ci', ciValueComputer.computeCI())
        }

        // Sets machineType and pue based on the executor if machineType is not already set
        if (machineType == null) {
            setMachineTypeAndPueFromExecutor(processMap?.get('executor') as String)
        }

        if (machineType == 'cloud') {
            log.warn(
                    'Cloud instances are not yet fully supported. ' +
                    'We are working on the seamless integration of major cloud providers. ' +
                    'In the meantime we recommend following the instructions at ' +
                    'https://nextflow-io.github.io/nf-co2footprint/usage/configuration/#cloud-computations' +
                    'to fully integrate your cloud instances into the plugin.'
            )
        }

        // Assign PUE if not already given
        setEmpty('pue', switch (machineType) {
            case 'local' -> 1.0
            case 'compute cluster' -> 1.67
            case 'cloud' -> 1.56  // source: (https://datacenter.uptimeinstitute.com/rs/711-RIA-145/images/2024.GlobalDataCenterSurvey.Report.pdf)
            default -> 1.0 // Fallback PUE (assigned if machineType is null)
        })

        // Set fallback CPU model based on machine type
        if (machineType) {
            if (supportedMachineTypes.contains(machineType)) {
                cpuData.fallbackModel = "default ${machineType}" as String
            }
            else {
                final String message = "machineType '${machineType}' is not supported. Please chose one of ${supportedMachineTypes}."
                log.error(message)
                throw new IllegalArgumentException(message)
            }
        }

        // Set default CPU power draw if given
        if (powerdrawCpuDefault) {
            cpuData.set(powerdrawCpuDefault, cpuData.fallbackModel, 'tdp (W)')
        }

        // Throw error if both customCpuTdpFile and ignoreCpuModel are set
        if (customCpuTdpFile && ignoreCpuModel) {
            log.warn("Both 'customCpuTdpFile' and 'ignoreCpuModel=true' are set. Note: When 'ignoreCpuModel' is true, the custom TDP file will be ignored.")
        }

        // Use custom CPU TDP file if provided
        if (customCpuTdpFile) {
            cpuData.update(
                    TDPDataMatrix.fromCsv(Paths.get(customCpuTdpFile as String))
            )
        }
    }

    /**
     * Sets the machine type and PUE based on the executor.
     * It reads a CSV file to get the machine type and PUE for the given executor.
     *
     * @param executor The executor name (e.g., 'awsbatch', 'local', etc.)
     */
    private void setMachineTypeAndPueFromExecutor(String executor) {
        if (executor) {
            // Read the CSV file as a DataMatrix - set RowIndex to 'executor'
            DataMatrix machineTypeMatrix = DataMatrix.fromCsv(
                    Paths.get(this.class.getResource(
                            '/executor_machine_pue_mapping.csv').toURI()),
                    ',', 0, null, 'executor'
            )
            // Check if matrix contains the required columns
            machineTypeMatrix.checkRequiredColumns(['machineType', 'pue'])
            if (machineTypeMatrix.rowIndex.containsKey(executor)) {
                set('machineType',  machineTypeMatrix.get(executor, 'machineType') as String)
                setEmpty('pue', machineTypeMatrix.get(executor, 'pue') as Double)
            }
            else {
                log.warn(
                    "Executor '${executor}' is not mapped to a machine type / power usage effectiveness (PUE). " +
                    "=> `machineType` <- null, `pue` <- 1.0. " +
                    "To eliminate this warning you can set `machineType` in the config to one of ${supportedMachineTypes}.")
            }
        }
        else {
            log.debug('No executor found in config under process.executor.')
        }
    }

    /**
     * Collects input file options for reporting.
     *
     * @return SortedMap of input file options
     */
    SortedMap<String, Object> collectInputFileOptions() {
        return [
                "customCpuTdpFile": customCpuTdpFile
        ].sort() as SortedMap
    }

    /**
     * Collects output file options for reporting.
     *
     * @return SortedMap of output file options
     */
    SortedMap<String, Object> collectOutputFileOptions() {
        return [
                "traceFile": traceFile,
                "summaryFile": summaryFile,
                "reportFile": reportFile
        ].sort() as SortedMap
    }

    /**
     * Collects CO₂ calculation options for reporting.
     *
     * @return SortedMap of calculation options
     */
    SortedMap<String, Object> collectCO2CalcOptions() {
        return [
                "location": location,
                "ci": ci,
                "pue": pue,
                "powerdrawMem": powerdrawMem,
                "powerdrawCpuDefault": powerdrawCpuDefault,
                "ignoreCpuModel": ignoreCpuModel,
        ].sort() as SortedMap
    }
}
