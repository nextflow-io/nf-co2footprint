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
    // Constants
    private String  timestamp = TraceHelper.launchTimestampFmt()
    private final List<String> supportedMachineTypes = ['local', 'compute cluster', 'cloud']

    // Configuration parameters (can be set in Nextflow config)
    private void defineParameters() {
        // Name, description, default value or function, return type, additional allowed types
        defineParameter(
                'traceFile', 'Path to the trace file',
                "co2footprint_trace_${timestamp}.txt", String, Set.of(GString, Path)
        )
        defineParameter(
                'summaryFile', 'Path to the summary file',
                "co2footprint_summary_${timestamp}.txt", String, Set.of(GString, Path)
        )
        defineParameter(
                'reportFile', 'Path to the report file',
                "co2footprint_report_${timestamp}.html", String, Set.of(GString, Path)
        )
        defineParameter(
                'location', 'Location GeoCode from Electricity maps',
                null, String, Set.of(GString)
        )
        defineParameter(
                'ci', 'Location-based carbon intensity (CI)',
                null, Double, Set.of(Closure<Double>, BigDecimal)
        )
        defineParameter(
                'ciMarket', 'market-based carbon intensity (CI)',
                null, Double, Set.of(Closure<Double>, BigDecimal)
        )
        defineParameter(
                'emApiKey', 'Electricity-maps API token',
                null, String, Set.of(GString)
        )
        defineParameter(
                'pue', 'Power usage effectiveness (PUE) of the data centre',
                null, Double, Set.of(BigDecimal)
        )
        defineParameter(
                'powerdrawMem', 'Power draw of memory [W per GB]',
                0.3725, Double, Set.of(BigDecimal)
        )
        defineParameter(
                'ignoreCpuModel', 'Turns off pattern matching of CPU names',
                false, Boolean
        )
        defineParameter(
                'powerdrawCpuDefault', 'Default powerdraw of the CPU',
                null, Double, Set.of(BigDecimal)
        )
        defineParameter(
                'customCpuTdpFile', 'Path to a custom CPU TDP file',
                null, String, Set.of(GString, Path)
        )
        defineParameter(
                'machineType', 'Type of computer on which the workflow is run [\'local\', \'compute cluster\', \'\']',
                null, String, Set.of(GString)
        )
    }

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
        // Define the possible parameters of the configuration
        defineParameters()

        // Initialize defaults
        setDefaults()

        // Ensure configMap is not null
        configMap ?= [:]

        // Assign values from map to config
        configMap.each { name, value ->
            if (this.containsKey(name)) {
                this.get(name).set(value)
            } else if (name != 'params') {
                log.debug("Skipping unknown configuration key: '${name}'")
            } 
        }

        // Determine the carbon intensity (CI) value
        if (value('ci') == null) {

            CIValueComputer ciValueComputer = new CIValueComputer(value('emApiKey'), value('location'), ciData)
            // ci is either set to a Closure (in case the electricity maps API is used) or to a Double (in the other cases)
            // The closure is invoked each time the CO2 emissions are calculated (for each task) to make a new API call to update the real time ci value.
            set('ci', ciValueComputer.computeCI())
        }

        // Sets machineType and pue based on the executor if machineType is not already set
        if (value('machineType') == null) {
            setMachineTypeAndPueFromExecutor(processMap?.get('executor') as String)
        }

        if (value('machineType') == 'cloud') {
            log.warn(
                    'Cloud instances are not yet fully supported. ' +
                    'We are working on the seamless integration of major cloud providers. ' +
                    'In the meantime we recommend following the instructions at ' +
                    'https://nextflow-io.github.io/nf-co2footprint/usage/configuration/#cloud-computations' +
                    'to fully integrate your cloud instances into the plugin.'
            )
        }

        // Assign PUE if not already given
        fill( 'pue',
            switch (value('machineType')) {
                case 'local' -> 1.0
                case 'compute cluster' -> 1.67
                case 'cloud' -> 1.56  // source: (https://datacenter.uptimeinstitute.com/rs/711-RIA-145/images/2024.GlobalDataCenterSurvey.Report.pdf)
                default -> 1.0 // Fallback PUE (assigned if machineType is null)
            }
        )

        // Set fallback CPU model based on machine type
        if (value('machineType')) {
            if (supportedMachineTypes.contains(value('machineType'))) {
                cpuData.fallbackModel = "default ${value('machineType')}" as String
            }
            else {
                final String message = "machineType '${value('machineType')}' is not supported." +
                        "Please chose one of ${supportedMachineTypes}."
                log.error(message)
                throw new IllegalArgumentException(message)
            }
        }

        // Set default CPU power draw if given
        if (value('powerdrawCpuDefault')) {
            cpuData.set(value('powerdrawCpuDefault'), cpuData.fallbackModel, 'tdp (W)')
        }

        // Throw error if both customCpuTdpFile and ignoreCpuModel are set
        if (value('customCpuTdpFile') && value('ignoreCpuModel')) {
            log.warn("Both 'customCpuTdpFile' and 'ignoreCpuModel=true' are set. Note: When 'ignoreCpuModel' is true, the custom TDP file will be ignored.")
        }

        // Use custom CPU TDP file if provided
        if (value('customCpuTdpFile')) {
            cpuData.update(
                    TDPDataMatrix.fromCsv(Paths.get(value('customCpuTdpFile') as String))
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
                    Paths.get(this.getClass().getResource('/executor_machine_pue_mapping.csv').toURI()),
                    ',', 0, null, 'executor'
            )
            // Check if matrix contains the required columns
            machineTypeMatrix.checkRequiredColumns(['machineType', 'pue'])
            if (machineTypeMatrix.rowIndex.containsKey(executor)) {
                set('machineType', machineTypeMatrix.get(executor, 'machineType') as String)
                fill('pue', machineTypeMatrix.get(executor, 'pue') as Double) // assign pue only if not already set
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
        Set<String> inputFileOptions = Set.of('customCpuTdpFile')
        return getValueMap(inputFileOptions).sort() as SortedMap
    }

    /**
     * Collects output file options for reporting.
     *
     * @return SortedMap of output file options
     */
    SortedMap<String, Object> collectOutputFileOptions() {
        Set<String> outputFileOptions = Set.of('traceFile', 'summaryFile', 'reportFile')
        return getValueMap(outputFileOptions).sort() as SortedMap
    }

    /**
     * Collects CO₂ calculation options for reporting.
     *
     * @return SortedMap of calculation options
     */
    SortedMap<String, Object> collectCO2CalcOptions() {
        return [
                location: value('location'),
                ci: (get('ci') instanceof Closure) ? 'dynamic' : value('ci'),
                ciMarket: (get('ciMarket') instanceof Closure) ? 'dynamic' : value('ciMarket'),
                pue: value('pue'),
                powerdrawMem: value('powerdrawMem'),
                powerdrawCpuDefault: value('powerdrawCpuDefault'),
                ignoreCpuModel: value('ignoreCpuModel'),
                machineType: value('machineType')
        ].sort() as SortedMap
    }
}

