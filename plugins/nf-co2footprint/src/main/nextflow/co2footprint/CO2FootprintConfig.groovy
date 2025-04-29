package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.trace.TraceHelper

import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * This class allows model an specific configuration, extracting values from a map and converting
 *
 * In this plugin, the user can configure the output file names of the CO2 footprint calculations
 *
 * co2footprint {
 *     traceFile = "co2footprint_trace.txt"
 *     summaryFile = "co2footprint_summary.txt"
 *     ci = 300
 *     pue = 1.4
 *     powerdrawMem = 0.67
 * }
 *
 *
 * We annotate this class as @PackageScope to restrict the access of their methods only to class in the
 * same package
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 *
 */
@Slf4j
class CO2FootprintConfig {

    // .config parameters
    private String  timestamp = TraceHelper.launchTimestampFmt()
    private String  traceFile = "co2footprint_trace_${timestamp}.txt"
    private String  summaryFile = "co2footprint_summary_${timestamp}.txt"
    private String  reportFile = "co2footprint_report_${timestamp}.html"
    private String  location = null
    private Double  ci = null               // CI: carbon intensity
    private Double  pue = null              // PUE: power usage effectiveness efficiency, coefficient of the data centre
    private Double  powerdrawMem = 0.3725   // Power draw of memory [W per GB]
    private Boolean ignoreCpuModel = false
    private Double  powerdrawCpuDefault = null
    private String  customCpuTdpFile = null
    private String  machineType = null      // Type of computer on which the workflow is run ['server', 'local', '']

    // Constants
    private final Double  default_ci = 475
    private final List<String> supportedMachineTypes = ['server', 'local']

    /**
     * Retrieve carbon intensity (CI) value from file containing CI values for different locations
     *
     * @param location Location as a country-code String
     * @return CI at location
     */
    protected Double retrieveCi(String location) {
        def dataReader = new InputStreamReader(this.class.getResourceAsStream('/CI_aggregated.v2.2.csv'))

        Double localCi = 0.0
        for ( String line : dataReader.readLines() ) {
            def row = line.split(",")
            if (row[0] == location) {
                localCi = row[4].toDouble()
                break
            }
        }
        dataReader.close()
        if (localCi == 0.0) {
            throw new IllegalArgumentException("Invalid 'location' parameter: $location. Could not be found in 'CI_aggregated.v2.2.csv'.")
        }

        return localCi
    }

    CO2FootprintConfig(Map<String, Object> configMap, TDPDataMatrix cpuData){
        configMap = configMap as ConcurrentHashMap<String, Object> ?: [:]

        // Assign values from map to config
        configMap.keySet().each { name  ->
            this.setProperty(name, configMap.remove(name))
        }

        // Reassign CI from location
        if (ci && location) {
            log.warn(
                    'Both \'ci\' and \'location\' were specified in configuration.' +
                    'The \'ci\' value will take precedence, ignoring the \'location\'.'
            )
        }

        // Keeps ci if already defined, if not uses location if given, fallback to default_ci
        ci ?= location ? retrieveCi(location) : default_ci

        // Assign PUE if not already given
        pue ?= switch (machineType) {
            case 'local' ->  1.0
            case 'server' -> 1.67
            default -> 1.0
        }

        // Reassign values based on machineType
        if (machineType) {
            if (supportedMachineTypes.contains(machineType)) {
                cpuData.fallbackModel = "default $machineType"
            }
            else {
                log.warn(
                        "machineType '${machineType}' is not supported. Please chose one of ${supportedMachineTypes}." +
                        "Using fallbacks: pue=${pue} & fallbackModel=${cpuData.fallbackModel}."
                )
            }
        }

        // Set default value if given
        if (powerdrawCpuDefault) {
            cpuData.set(powerdrawCpuDefault, cpuData.fallbackModel, 'tdp (W)')
        }

        // Use custom TDP file
        if (customCpuTdpFile) { cpuData.update( TDPDataMatrix.loadCsv(Paths.get(customCpuTdpFile as String)) ) }

        // Check whether all entries in the map could be assigned to a class property
        if (!configMap.isEmpty()) {
            log.warn(
                    'Configuration map is not empty after retrieving all possible properties.'
                    + "The keys '${configMap.keySet()}' remain unused."
            )
        }
    }

    String getTraceFile() { traceFile }
    String getSummaryFile() { summaryFile }
    String getReportFile() { reportFile }
    Boolean getIgnoreCpuModel() { ignoreCpuModel }
    String getLocation() { location }
    Double getCi() { ci }
    Double getPue() { pue }
    Double getPowerdrawMem() { powerdrawMem }
    Double getPowerdrawCpuDefault() { powerdrawCpuDefault }
    String getCustomCpuTdpFile() { customCpuTdpFile }

    // Different functions to collect options for reporting, grouped by purpose
    SortedMap<String, Object> collectInputFileOptions() {
        return [
                "customCpuTdpFile": customCpuTdpFile
        ].sort() as SortedMap
    }
    SortedMap<String, Object> collectOutputFileOptions() {
        return [
                "traceFile": traceFile,
                "summaryFile": summaryFile,
                "reportFile": reportFile
        ].sort() as SortedMap
    }
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
