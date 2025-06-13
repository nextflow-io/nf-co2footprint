package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.trace.TraceHelper

import java.nio.file.Paths

/**
 * This class allows to model a specific configuration, extracting values from a map and converting
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
    private def     ci = null               // CI: carbon intensity
    private String  apiKey = null           // API key for electricityMaps
    private Double  pue = null              // PUE: power usage effectiveness efficiency, coefficient of the data centre
    private Double  powerdrawMem = 0.3725   // Power draw of memory [W per GB]
    private Boolean ignoreCpuModel = false
    private Double  powerdrawCpuDefault = null
    private String  customCpuTdpFile = null
    private String  machineType = null      // Type of computer on which the workflow is run ['local', 'compute cluster', '']

    // Constants
    private final List<String> supportedMachineTypes = ['local', 'compute cluster']

    // Getter methods for private values
    String getTimestamp() { timestamp }
    String getTraceFile() { traceFile }
    String getSummaryFile() { summaryFile }
    String getReportFile() { reportFile }
    String getLocation() { location }
    Double getCi() {
        (ci instanceof Closure) ? ci() : ci
    }
    Double getPue() { pue }
    Boolean getIgnoreCpuModel() { ignoreCpuModel }
    Double getPowerdrawCpuDefault() { powerdrawCpuDefault }
    Double getPowerdrawMem() { powerdrawMem }
    String getCustomCpuTdpFile() { customCpuTdpFile }
    String getMachineType()  { machineType }


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

            CIValueComputer ciValueComputer = new CIValueComputer(apiKey, location, ciData)
            // ci is either set to a Closure (in case the electricity maps API is used) or to a Double (in the other cases)
            // The closure is invoked each time the CO2 emissions are calculated (for each task) to make a new API call to update the real time ci value.
            ci = ciValueComputer.computeCI()
        }

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
                final String message = "machineType '${machineType}' is not supported." +
                        "Please chose one of ${supportedMachineTypes}."
                log.error(message)
                throw new IllegalArgumentException(message)
            }
        }

        // Set default value if given
        if (powerdrawCpuDefault) {
            cpuData.set(powerdrawCpuDefault, cpuData.fallbackModel, 'tdp (W)')
        }

        // Use custom TDP file
        if (customCpuTdpFile) {
            cpuData.update(
                    TDPDataMatrix.fromCsv(Paths.get(customCpuTdpFile as String))
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
