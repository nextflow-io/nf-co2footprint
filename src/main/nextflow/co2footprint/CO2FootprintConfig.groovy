package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Config.ReportFileConfig
import nextflow.co2footprint.Config.SummaryFileConfig
import nextflow.co2footprint.Config.TraceFileConfig
import nextflow.co2footprint.DataContainers.AWSRegionsDataMatrix
import nextflow.co2footprint.DataContainers.MachineTypeDataMatrix
import nextflow.config.spec.ScopeName
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ConfigOption
import nextflow.script.dsl.Description
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CiRecord
import nextflow.trace.TraceHelper

import java.nio.file.Path

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
@ScopeName('co2footprint')
@Description('The `co2footprint` scope allows you to configure the `nf-co2footprint` plugin.')
@Slf4j
class CO2FootprintConfig implements ConfigScope {
    // Constants
    private final List<String> supportedMachineTypes = ['local', 'compute cluster', 'cloud']
    private final MachineTypeDataMatrix machineTypeDataMatrix = MachineTypeDataMatrix.fromCsv(
            Path.of(this.getClass().getResource('/executor_machine_pue_mapping.csv').toURI())
    )
    private final String timestamp = TraceHelper.launchTimestampFmt()
    private final String executor

    @ConfigOption(types=[Map])
    @Description('Configuration for the trace file.')
    final TraceFileConfig trace

    @ConfigOption(types=[Map])
    @Description('Configuration for the summary file.')
    final SummaryFileConfig summary

    @ConfigOption(types=[Map])
    @Description('Configuration for the report file.')
    final ReportFileConfig report

    @ConfigOption(types=[GString])
    @Description('Location GeoCode from Electricity maps.')
    final String location

    @ConfigOption(types=[Double, BigDecimal])
    @Description('Location-based carbon intensity (CI).')
    final CiRecord ci

    @ConfigOption(types=[Double])
    @Description('Market-based carbon intensity (CI).')
    final BigDecimal ciMarket

    @ConfigOption(types=[GString])
    @Description('Electricity-maps API token.')
    final String emApiKey

    @ConfigOption(types=[Double])
    @Description('Power usage effectiveness (PUE) of the data centre.')
    BigDecimal pue

    @ConfigOption(types=[Double])
    @Description('Power draw of memory [W per GB].')
    final BigDecimal powerdrawMem

    @ConfigOption
    @Description('Turns off pattern matching of CPU names.')
    final Boolean ignoreCpuModel

    @ConfigOption(types=[Double])
    @Description('Default powerdraw of the CPU.')
    final BigDecimal powerdrawCpuDefault

    @ConfigOption(types=[String, GString])
    @Description('Path to a custom CPU TDP file.')
    final Path customCpuTdpFile

    @ConfigOption(types=[GString])
    @Description('Type of computer on which the workflow is run [\'local\', \'compute cluster\', \'\'].')
    String machineType

    @ConfigOption
    @Description('Polynomial coefficients for CPU power model (highest degree first).')
    final List<Number> cpuPowerModel

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

        // File parameters (sub-scopes)
        trace = new TraceFileConfig(configMap.remove('trace') as Map ?: [:], timestamp)
        summary = new SummaryFileConfig(configMap.remove('summary') as Map ?: [:], timestamp)
        report = new ReportFileConfig(configMap.remove('report') as Map ?: [:], timestamp)

        // Location
        location = configMap.remove('location') as String ?: getLocationFromAWSRegion()

        // API key
        emApiKey = configMap.remove('emApiKey') as String

        // Carbon intensities
        ci = new CiRecord(configMap.remove('ci') as BigDecimal, ciData, location, emApiKey)
        ciMarket = configMap.remove('ciMarket') as BigDecimal

        // Power model
        cpuPowerModel = configMap.remove('cpuPowerModel') as List<BigDecimal>
        if (cpuPowerModel != null) {
            Integer degree = cpuPowerModel.size() - 1
            List<String> terms = []
            cpuPowerModel.eachWithIndex { Number c, Integer i -> terms.add("${c}*x^${degree - i}") }
            log.info("Using custom CPU power model: f(x) = " + terms.join(" + "))
        }

        // Powerdraw factors
        // Custom TPD file
        if (configMap.containsKey('customCpuTdpFile')) {
            customCpuTdpFile = Path.of(configMap.remove('customCpuTdpFile') as String)
            cpuData.update( TDPDataMatrix.fromCsv(Path.of(customCpuTdpFile as String)) )
        } else {
            customCpuTdpFile = null
        }

        // Ignore CPU model
        ignoreCpuModel = configMap.remove('ignoreCpuModel') as boolean

        // Default CPU powerdraw
        if (configMap.containsKey('powerdrawCpuDefault')) {
            powerdrawCpuDefault = configMap.remove('powerdrawCpuDefault') as BigDecimal
            cpuData.set(powerdrawCpuDefault, cpuData.fallbackModel, 'tdp (W)')
        } else {
            powerdrawCpuDefault = null
        }

        // Powerdraw memory
        powerdrawMem = configMap.containsKey('powerdrawMem') ?  configMap.remove('powerdrawMem') as BigDecimal : 0.3725


        // Executor (Can define Machine type & PUE, if not already given
        executor = processMap?.get('executor') as String
        MachineTypeDataMatrix executorMatrix = null
        if (executor) {
            executorMatrix = machineTypeDataMatrix.matchExecutor(executor)
        } else {
            log.debug('No executor found in config under process.executor.')
        }

        // Machine type (Given -> Executor -> null)
        machineType = configMap.remove('machineType') as String ?:
            executorMatrix?.getMachineType()

        // PUE (Given -> Executor -> MachineType -> 1.0)
        pue = configMap.containsKey('pue') ? configMap.remove('pue') as BigDecimal :
            executorMatrix?.getPUE() ?:
                machineTypeDataMatrix.matchExecutor(machineType)?.getPUE() ?:
                    1.0

        // Custom machine-specific logic
        if (machineType) {
            if (supportedMachineTypes.contains(machineType)) {
                cpuData.fallbackModel = "default ${machineType}" as String
                if (machineType == 'cloud') {
                    log.warn(
                        'Cloud instances are not yet fully supported. ' +
                        'We are working on the seamless integration of major cloud providers. ' +
                        'In the meantime we recommend following the instructions at ' +
                        'https://nextflow-io.github.io/nf-co2footprint/usage/configuration/#cloud-computations' +
                        'to fully integrate your cloud instances into the plugin.'
                    )
                }
            } else {
                final String message = "`machineType?` '${machineType}' is not supported. Please chose one of ${supportedMachineTypes}."
                log.error(message)
                throw new IllegalArgumentException(message)
            }
        } else if (executorMatrix == null) {
            log.warn(
                "Fallback to: `machineType = null`, `pue = 1.0`. " +
                "To eliminate this warning you can set `machineType` in the config to one of ${supportedMachineTypes}."
            )
        }

        // Throw error if both customCpuTdpFile and ignoreCpuModel are set
        if (customCpuTdpFile && ignoreCpuModel) {
            log.warn("Both 'customCpuTdpFile' and 'ignoreCpuModel=true' are set. Note: When 'ignoreCpuModel' is true, the custom TDP file will be ignored.")
        }

        // Check whether all parameters were included successfully
        configMap.remove('params')
        if (!configMap.isEmpty()){
            log.warn("`co2footprint` configuration scope contains unused parameters ${configMap.keySet()}.")
        }
    }

    /**
     * Mapping between AWS regions and Zone IDs is read from
     * `aws_region_zoneID_mapping.csv`, which must have:
     * - Row index: 'Region code'
     * - Column: 'Zone id'
     */
    private String getLocationFromAWSRegion() {
        AWSRegionsDataMatrix awsMatrix = AWSRegionsDataMatrix.fromCsv(
            Path.of(this.getClass().getResource('/aws_region_zoneID_mapping.csv').toURI())
        )

        String region = awsMatrix.fetchRegion()

        if (region) {
            AWSRegionsDataMatrix regionMatrix = awsMatrix.matchRegion(region)
            if (regionMatrix) {
                String zoneId = regionMatrix.getZoneId()
                log.info("AWS region '${region}' detected; Zone ID '${zoneId}' was set.")
                return zoneId
            }
        }
        return null
    }

    /**
     * Checks whether the API is used for CI determination.
     *
     * @return Whether the em API key is set
     */
    boolean usesAPI() {
        return emApiKey != null
    }

    /**
     * Collects input file options for reporting.
     *
     * @return SortedMap of input file options
     */
    SortedMap<String, Object> collectInputFileOptions() {
        return [customCpuTdpFile : customCpuTdpFile].sort() as SortedMap
    }

    /**
     * Collects output file options for reporting.
     *
     * @return SortedMap of output file options
     */
    SortedMap<String, Object> collectOutputFileOptions() {
        return [
            reportFile: report.file,
            summaryFile: summary.file,
            traceFile: trace.file
        ].sort() as SortedMap
    }

    /**
     * Collects CO₂ calculation options for reporting.
     *
     * @return SortedMap of calculation options
     */
    SortedMap<String, Object> collectCO2CalcOptions() {
        return [
            location: location,
            pue: pue,
            powerdrawMem: powerdrawMem,
            powerdrawCpuDefault: powerdrawCpuDefault,
            ignoreCpuModel: ignoreCpuModel,
            machineType: machineType,
            ciMarket: ciMarket,
            'ci': usesAPI() ? 'dynamic' : ci.value,
        ].sort() as SortedMap
    }
}
