package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

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

    private String  traceFile = CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_TRACE_FILE_NAME
    private String  summaryFile = CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_SUMMARY_FILE_NAME
    private String  reportFile = CO2FootprintFactory.CO2FootprintReportObserver.DEF_REPORT_FILE_NAME
    private String  location = null
    private Double  ci = 475   // CI: carbon intensity
    private Double  pue = 1.67  // PUE: power usage effectiveness efficiency, coefficient of the data centre
    private Double  powerdrawMem = 0.3725 // Power draw of memory [W per GB]
    private Boolean ignoreCpuModel = false
    private Double  powerdrawCpuDefault = 12.0
    private String  customCpuTdpFile = null

    // Retrieve CI value from file containing CI values for different locations
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

    CO2FootprintConfig(Map map, TDPDataMatrix cpuData){
        def config = map ?: Collections.emptyMap()
        if (config.traceFile) {
            traceFile = config.traceFile
        }
        if (config.summaryFile) {
            summaryFile = config.summaryFile
        }
        if (config.reportFile) {
            reportFile = config.reportFile
        }
        if (config.ignoreCpuModel) {
            ignoreCpuModel = config.ignoreCpuModel
        }
        if (config.ci && config.location) {
            throw new IllegalArgumentException("Invalid combination of 'ci' and 'location' parameters specified for the CO2Footprint plugin. Please specify either 'ci' or 'location'!")
        }
        if (config.ci) {
            ci = config.ci as Double
        }
        if (config.location) {
            ci = retrieveCi(config.location as String)
            location = config.location
        }
        if (config.pue) {
            pue = config.pue as Double
        }
        if (config.powerdrawMem) {
            powerdrawMem = config.powerdrawMem as Double
        }
        if (config.powerdrawCpuDefault) {
            powerdrawCpuDefault = config.powerdrawCpuDefault as Double
        }
        cpuData.set(powerdrawCpuDefault, 'default', 'tdp (W)')

        if (config.customCpuTdpFile) {
            customCpuTdpFile = config.customCpuTdpFile
            cpuData.update(
                    TDPDataMatrix.loadCsv(Paths.get(config.customCpuTdpFile as String))
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
        Map<String, Object> newMap = [:]
        newMap["customCpuTdpFile"] = customCpuTdpFile
        return newMap.sort() as SortedMap
    }
    SortedMap<String, Object> collectOutputFileOptions() {
        Map<String, Object> newMap = [:]
        newMap["traceFile"] = traceFile
        newMap["summaryFile"] = summaryFile
        newMap["reportFile"] = reportFile
        return newMap.sort() as SortedMap
    }
    SortedMap<String, Object> collectCO2CalcOptions() {
        Map<String, Object> newMap = [:]
        newMap["location"] = location
        newMap["ci"] = ci   // Might be indirectly determined for location parameter
        newMap["pue"] = pue
        newMap["powerdrawMem"] = powerdrawMem
        newMap["powerdrawCpuDefault"] = powerdrawCpuDefault
        newMap["ignoreCpuModel"] = ignoreCpuModel
        return newMap.sort() as SortedMap
    }
}
