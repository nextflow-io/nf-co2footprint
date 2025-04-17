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
@PackageScope
class CO2FootprintConfig {

    private String  timestamp = TraceHelper.launchTimestampFmt()
    private String  traceFile = "co2footprint_trace_${timestamp}.txt"
    private String  summaryFile = "co2footprint_summary_${timestamp}.txt"
    private String  reportFile = "co2footprint_report_${timestamp}.html"
    private String  location = null
    private Closure<Double> ci = null       // CI: carbon intensity
    private String  apiKey = null           // API key for electricityMaps 
    private Double  pue = 1.67              // PUE: power usage effectiveness efficiency, coefficient of the data centre
    private Double  powerdrawMem = 0.3725   // Power draw of memory [W per GB]
    private Boolean ignoreCpuModel = false
    private Double  powerdrawCpuDefault = 12.0
    private String  customCpuTdpFile = null


    CO2FootprintConfig(Map<String, Object> configMap, TDPDataMatrix cpuData, CIDataMatrix ciData) {
        log.info("Config map: ${configMap}")

        // Assign values from map to config
        configMap.keySet().each { name ->
            if (name == 'ci') {
                // Handle 'ci' specifically
                def value = configMap.remove(name)
                if (value instanceof Number) {
                    this.ci = { -> value as Double } // Wrap Number in a Closure
                    log.info("Using provided carbon intensity value: ${this.ci} gCO₂eq/kWh")
                } else {
                    throw new IllegalArgumentException("Invalid type for 'ci': ${value.getClass().getName()}. Expected Number or Closure.")
                }
            } else {
                // Assign other properties dynamically
                this.setProperty(name, configMap.remove(name))
            }
        }

        // Determine the carbon intensity (CI) value
        if (this.ci == null) {
            // Create an instance of GetCIvalue and determine carbon intensity
            def ciValueComputer = new CIValueComputer(apiKey, location, ciData)
            this.ci = ciValueComputer.getCI()
        }

        // Reassign default CPU from config
        if (powerdrawCpuDefault) {
            cpuData.set(powerdrawCpuDefault, 'default', 'tdp (W)')
        }

        // Use custom TDP file
        if (customCpuTdpFile) {
            cpuData.update(
                    TDPDataMatrix.fromCsv(Paths.get(customCpuTdpFile as String))
            )
        }

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
    Closure<Double> getCi() { this.ci }
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
