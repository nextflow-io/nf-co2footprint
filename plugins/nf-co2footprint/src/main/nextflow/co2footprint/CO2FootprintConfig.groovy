package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import nextflow.trace.TraceHelper

import java.nio.file.Paths

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
    private Double  ci = 475                // CI: carbon intensity
    private Double  pue = 1.67              // PUE: power usage effectiveness efficiency, coefficient of the data centre
    private Double  powerdrawMem = 0.3725   // Power draw of memory [W per GB]
    private Boolean ignoreCpuModel = false
    private Double  powerdrawCpuDefault = 12.0
    private String  customCpuTdpFile = null
    private Logger  LOGGER = LoggerFactory.getLogger('nextflow.co2footprint')

    /**
     * Retrieve carbon intensity (CI) value from file containing CI values for different locations
     *
     * @param location Location as a country-code String
     * @return CI at location
     */
    protected Double retrieveCi(String location) {
        def dataReader = new InputStreamReader(this.class.getResourceAsStream('/CI_aggregated.v2.2.csv'))

        Double localCi = 0.0
        String line
        while ( line = dataReader.readLine() ) {
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

    /**
     * Sanity checks for configuration map
     * @param config configuration map
     */
    private static checkConfig(Map configMap) {
        if (configMap.ci && configMap.location) {
            throw new IllegalArgumentException("Invalid combination of 'ci' and 'location' parameters specified for the CO2Footprint plugin. Please specify either 'ci' or 'location'!")
        }
    }

    CO2FootprintConfig(Map<String, Object> configMap, TDPDataMatrix cpuData){
        configMap = configMap ?: [:]

        // Sanity checking
        checkConfig(configMap)

        // Assign values from map to config
        configMap.keySet().each { name  ->
            this.setProperty(name, configMap.remove(name))
        }

        // Reassign CI from location
        ci = location ? retrieveCi(location) : ci

        // Reassign default CPU from config
        if (powerdrawCpuDefault) {
            cpuData.set(powerdrawCpuDefault, 'default', 'tdp (W)')
        }

        // Use custom TDP file
        if (customCpuTdpFile) {
            cpuData.update(
                    TDPDataMatrix.loadCsv(Paths.get(customCpuTdpFile as String))
            )
        }

        // Check whether all entries in the map could be assigned to a class property
        if (!configMap.isEmpty()) {
            LOGGER.warn(
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
        ] as SortedMap
    }
    SortedMap<String, Object> collectOutputFileOptions() {
        return [
                "traceFile": traceFile,
                "summaryFile": summaryFile,
                "reportFile": reportFile
        ] as SortedMap
    }
    SortedMap<String, Object> collectCO2CalcOptions() {
        return [
                "location": location,
                "ci": ci,
                "pue": pue,
                "powerdrawMem": powerdrawMem,
                "powerdrawCpuDefault": powerdrawCpuDefault,
                "ignoreCpuModel": ignoreCpuModel,
        ] as SortedMap
    }
}
