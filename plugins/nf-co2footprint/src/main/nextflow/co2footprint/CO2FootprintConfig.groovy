package nextflow.co2footprint

import nextflow.co2footprint.utils.DataMatrix
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
    private final List<String> supportedMachineTypes = ['local', 'compute cluster', 'cloud']

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

        // Sets machineType and pue based on the executor if machineType is not already set
        if (this.machineType == null) {
            setMachineTypeAndPueFromExecutor(processMap?.get('executor') as String)
        } 

        // Assign PUE if not already given
        pue ?= switch (machineType) {
            case 'local' -> 1.0
            case 'compute cluster' -> 1.67
            case 'cloud' -> 1.56  // source: (https://datacenter.uptimeinstitute.com/rs/711-RIA-145/images/2024.GlobalDataCenterSurvey.Report.pdf)
            default -> 1.0 // Fallback PUE (assigned if machineType is null)
        }
        
        // Reassign values based on machineType
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

    /**
     * Sets the machine type and PUE based on the executor.
     * It reads a CSV file to get the machine type and PUE for the given executor.
     *
     * @param executor The executor name (e.g., 'awsbatch', 'local', etc.)
     */
    private void setMachineTypeAndPueFromExecutor(String executor) {
        // Read the CSV file as a DataMatrix - set RowIndex to 'executor'
        DataMatrix matrix = DataMatrix.fromCsv(Paths.get(this.class.getResource('/executor_machine_pue_mapping.csv').toURI()), ',', 0, null, 'executor')    
        // Check if matrix contains the required columns
        matrix.checkRequiredColumns(['machineType', 'pue'])
        try {
            this.machineType = matrix.get(executor, 'machineType') as String
            this.pue ?= matrix.get(executor, 'pue') as Double // assign pue only if not already set
        } catch (IllegalArgumentException e) {
            log.warn("Executor '${executor}' is not supported. MachineType set to null.")
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
