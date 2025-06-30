package nextflow.co2footprint


import groovy.util.logging.Slf4j
import nextflow.co2footprint.DataContainers.DataMatrix
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.CIValueComputer
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.trace.TraceHelper
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
class CO2FootprintConfig {

    // Configuration parameters (can be set in Nextflow config)
    private String  timestamp = TraceHelper.launchTimestampFmt()
    private String  traceFile = "co2footprint_trace_${timestamp}.txt"
    private String  summaryFile = "co2footprint_summary_${timestamp}.txt"
    private String  reportFile = "co2footprint_report_${timestamp}.html"
    private String  location = null
    private def     ci = null                       // CI: carbon intensity
    private def     ciMarket = null                 // Market based CI
    private String  emApiKey = null                   // API key for electricityMaps
    private Double  pue = null                      // PUE: power usage effectiveness efficiency, coefficient of the data centre
    private Double  powerdrawMem = 0.3725           // Power draw of memory [W per GB]
    private Boolean ignoreCpuModel = false
    private Double  powerdrawCpuDefault = null
    private String  customCpuTdpFile = null
    private String  machineType = null              // Type of computer on which the workflow is run ['local', 'compute cluster', '']

    // Supported machine types
    private final List<String> supportedMachineTypes = ['local', 'compute cluster', 'cloud']

    // Getter methods for config values
    String getTimestamp() { timestamp }
    String getTraceFile() { traceFile }
    String getSummaryFile() { summaryFile }
    String getReportFile() { reportFile }
    String getLocation() { location }

    /**
     * Returns the carbon intensity value.
     * If set as a closure (for real-time API), invokes it to get the current value.
     */
    Double getCi() {
        (ci instanceof Closure) ? (ci as Closure<Double>)() : ci
    }

    /**
     * Returns the personal energy mix carbon intensity value.
     * If set as a closure (in case user defined a function for it in the config), invokes it to get the current value.
     */
    Double getCiMarket() {
        (ciMarket instanceof Closure) ? (ciMarket as Closure<Double>)() : ciMarket
    }
    Double getPue() { pue }
    Boolean getIgnoreCpuModel() { ignoreCpuModel }
    Double getPowerdrawCpuDefault() { powerdrawCpuDefault }
    Double getPowerdrawMem() { powerdrawMem }
    String getCustomCpuTdpFile() { customCpuTdpFile }
    String getMachineType()  { machineType }

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
        // Ensure configMap is not null
        configMap ?= [:]

        // Assign values from map to config
        configMap.each { name, value ->
            if (this.hasProperty(name)) {
                this.setProperty(name, value)
            } else {
                // Log warning and skip the key
                log.warn("Skipping unknown configuration key: '${name}'")
            }
        }

        // Determine the carbon intensity (CI) value
        if (ci == null) {

            CIValueComputer ciValueComputer = new CIValueComputer(emApiKey, location, ciData)
            // ci is either set to a Closure (in case the electricity maps API is used) or to a Double (in the other cases)
            // The closure is invoked each time the CO2 emissions are calculated (for each task) to make a new API call to update the real time ci value.
            ci = ciValueComputer.computeCI()
        }

        // Sets machineType and pue based on the executor if machineType is not already set
        if (this.machineType == null) {
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
        pue ?= switch (machineType) {
            case 'local' -> 1.0
            case 'compute cluster' -> 1.67
            case 'cloud' -> 1.56  // source: (https://datacenter.uptimeinstitute.com/rs/711-RIA-145/images/2024.GlobalDataCenterSurvey.Report.pdf)
            default -> 1.0 // Fallback PUE (assigned if machineType is null)
        }

        // Set fallback CPU model based on machine type
        if (machineType) {
            if (supportedMachineTypes.contains(machineType)) {
                cpuData.fallbackModel = "default $machineType" as String
            }
            else {
                final String message = "machineType '${machineType}' is not supported." +
                        "Please chose one of ${supportedMachineTypes}."
                log.error(message)
                throw new IllegalArgumentException(message)
            }
        }

        // Set default CPU power draw if given
        if (powerdrawCpuDefault) {
            cpuData.set(powerdrawCpuDefault, cpuData.fallbackModel, 'tdp (W)')
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
        // Read the CSV file as a DataMatrix - set RowIndex to 'executor'
        DataMatrix machineTypeMatrix = DataMatrix.fromCsv(Paths.get(this.class.getResource('/executor_machine_pue_mapping.csv').toURI()), ',', 0, null, 'executor')
        // Check if matrix contains the required columns
        machineTypeMatrix.checkRequiredColumns(['machineType', 'pue'])
        if (machineTypeMatrix.rowIndex.containsKey(executor)) {
            this.machineType = machineTypeMatrix.get(executor, 'machineType') as String
            this.pue ?= machineTypeMatrix.get(executor, 'pue') as Double // assign pue only if not already set
        }
        else {
            log.warn(
                    "Executor '${executor}' is not mapped. `machineType` set to null." +
                    " To eliminate this warning you can set `machineType` in the config to one of ${supportedMachineTypes}.")
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
