package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Config.BaseConfig
import nextflow.co2footprint.DataContainers.DataMatrix
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CiRecord
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
 *     trace = {
 *       enabled: true,
 *       file: "co2footprint_trace.txt"
 *     }
 *     summary = {
 *       enabled: true,
 *       file: "co2footprint_summary.txt"
 *     }
 *     report = {
 *       enabled: true,
 *       file: "co2footprint_report.txt
 *     }
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

    /**
     * Defines the parameters of the configuration.
     */
    private void defineParameters() {
        // Name, description, default value or function, return type, additional allowed types
        defineParameter(
                'trace', 'Trace file config',
                new CO2FootprintSubConfig('trace', [:] as LinkedHashMap), CO2FootprintSubConfig
        )
        defineParameter(
                'summary', 'Summary file config',
                new CO2FootprintSubConfig('summary', [:] as LinkedHashMap), CO2FootprintSubConfig
        )
        defineParameter(
                'report', 'Report file config',
                new CO2FootprintSubConfig('report', [:] as LinkedHashMap), CO2FootprintSubConfig
        )
        defineParameter(
                'location', 'Location GeoCode from Electricity maps',
                null, String, Set.of(GString)
        )
        defineParameter(
                'ci', 'Location-based carbon intensity (CI)',
                null, Double, Set.of(CiRecord, BigDecimal)
        )
        defineParameter(
                'ciMarket', 'Market-based carbon intensity (CI)',
                null, Double, Set.of(CiRecord, BigDecimal)
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
        defineParameter(
                'cpuPowerModel', 'Polynomial coefficients for CPU power model (highest degree first)',
                null, List<Number>
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
                if (name == 'trace') {
                    this.get('trace').set(new CO2FootprintSubConfig('trace', value))
                } else if (name == 'summary') {
                    this.get('summary').set(new CO2FootprintSubConfig('summary', value))
                } else if (name == 'report') {
                    this.get('report').set(new CO2FootprintSubConfig('report', value))
                } else {
                    this.get(name).set(value)
                }
            } else if (name != 'params') {
                log.debug("Skipping unknown configuration key: '${name}'")
            } 
        }

        /* ===== Determine the Carbon Intensity (CI) value ===== */
        set('ci', new CiRecord(value('ci') as Number, ciData, value('location') as String, value('emApiKey') as String))

        /* ===== Determine Machine Type and PUE ===== */

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

        /* ===== Determine CPU Power Model Parameters ===== */
        List<Number> coefficients = value('cpuPowerModel')

        // Use custom polynomial CPU power model if given
        if (coefficients != null) {
            Integer degree = coefficients.size() - 1
            List<String> terms = []
            coefficients.eachWithIndex { Number c, Integer i -> terms.add("${c}*x^${degree - i}") }
            log.info("Using custom CPU power model: f(x) = " + terms.join(" + "))
        // Use TDP data if no custom model is given
        } else {
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
     * Checks whether the API is used for CI determination.
     *
     * @return Whether the em API key is set
     */
    boolean usesAPI() {
        return value('emApiKey') != null
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
        return [
            reportFile: this.value('report').value('file'),
            summaryFile: this.value('summary').value('file'),
            traceFile: this.value('trace').value('file')
        ].sort() as SortedMap
    }

    /**
     * Collects CO₂ calculation options for reporting.
     *
     * @return SortedMap of calculation options
     */
    SortedMap<String, Object> collectCO2CalcOptions() {
        Set<String> outputFileOptions = Set.of('location', 'pue', 'powerdrawMem', 'powerdrawCpuDefault', 'ignoreCpuModel', 'machineType', 'ciMarket')
        SortedMap<String, Object> outMap = getValueMap(outputFileOptions).sort() as SortedMap
        outMap.putAll(
            [
                'ci': usesAPI() ? 'dynamic' : value('ci'),
            ]
        )
        return outMap
    }
}
