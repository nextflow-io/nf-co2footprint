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
    private String  machineType = null      // Type of computer on which the workflow is run ['local', 'compute cluster', '']

    // Constants
    private final Double  default_ci = 475
    private final List<String> supportedMachineTypes = ['local', 'compute cluster', '']

    // Getter methods for private values
    String getTimestamp() { timestamp }
    String getTraceFile() { traceFile }
    String getSummaryFile() { summaryFile }
    String getReportFile() { reportFile }
    String getLocation() { location }
    Double getCi() { ci }
    Double getPue() { pue }
    Boolean getIgnoreCpuModel() { ignoreCpuModel }
    Double getPowerdrawCpuDefault() { powerdrawCpuDefault }
    Double getPowerdrawMem() { powerdrawMem }
    String getCustomCpuTdpFile() { customCpuTdpFile }
    String getMachineType()  { machineType }

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

    CO2FootprintConfig(Map<String, Object> configMap, TDPDataMatrix cpuData, Map<String, Object> processMap){
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

        // Assign machine Type if not already given
        machineType = machineType == null ? matchProcessExecutor(processMap?.get('executor') as String) : machineType

        // Assign PUE if not already given
        pue ?= switch (machineType) {
            case 'local' ->  1.0
            case 'compute cluster' -> 1.67
            default -> 1.0
        }

        // Reassign values based on machineType
        if (machineType) {
            if (supportedMachineTypes.contains(machineType)) {
                cpuData.fallbackModel = "default $machineType"
            }
            else {
                String message = "machineType '${machineType}' is not supported." +
                        "Please chose one of ${supportedMachineTypes}."
                log.error(message, new IllegalArgumentException(message))
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


    private static String matchProcessExecutor(String executor) {
        return switch(executor) {
            case 'awsbatch' -> 'compute cluster'                // AWS cloud
            case 'azurebatch' -> 'compute cluster'              // MS Azure cloud
            case 'bridge' -> 'compute cluster'
            case 'flux' -> 'compute cluster'
            case 'google-batch' -> 'compute cluster'            // Google cloud
            case 'google-lifesciences' -> 'compute cluster'     // Google cloud
            case 'condor' -> 'compute cluster'
            case 'hq' -> 'compute cluster'
            case 'k8s' -> 'compute cluster'
            case 'local' -> 'local'
            case 'lsf' -> 'compute cluster'
            case 'moab' -> 'compute cluster'
            case 'nqsii' -> 'compute cluster'
            case 'oar' -> 'compute cluster'
            case 'pbs' -> 'compute cluster'
            case 'pbspro' -> 'compute cluster'
            case 'sge' -> 'compute cluster'
            case 'slurm' -> 'compute cluster'
            default -> null
        }
    }


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
