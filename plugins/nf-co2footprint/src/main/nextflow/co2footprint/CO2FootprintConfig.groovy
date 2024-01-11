package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

/**
 * This class allows model an specific configuration, extracting values from a map and converting
 *
 * In this plugin, the user can configure the output file names of the CO2 footprint calculations
 *
 * co2footprint {
 *     file = "co2footprint_trace.txt"
 *     summaryFile = "co2footprint_summary.txt"
 *     ci = 300
 *     pue = 1.4
 *     powerdrawMem = 0.67
 * }
 *
 *
 * We anotate this class as @PackageScope to restrict the access of their methods only to class in the
 * same package
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 *
 */
@Slf4j
@PackageScope
class CO2FootprintConfig {

    final private String  file
    final private String  summaryFile
    final private String  reportFile
    final private String  location
    final private Double  ci    // CI: carbon intensity
    final private Double  pue   // PUE: power usage effectiveness efficiency, coefficient of the data centre
    final private Double  powerdrawMem  // Power draw of memory [W per GB]
    final private Boolean ignoreCpuModel
    final private Double powerdrawCpuDefault

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
        if (localCi == 0.0)
            throw new IllegalArgumentException("Invalid 'location' parameter: $location. Could not be found in 'CI_aggregated.v2.2.csv'.")

        return localCi
    }

    // Load user provided file containing custom TDP values for different CPU models
    protected void loadCustomCpuTdpData(Map<String, Double> data, String customCpuTdpFile) {
        new File(customCpuTdpFile).withReader(){ reader ->
            String line
            while( line = reader.readLine() ) {
                def h = line.split(",")
                if ( h[0] != 'model' ) {
                    if ( data.containsKey(h[0]) ) log.warn "Already existing CPU model specific TDP value for ${h[0]} is overwritten with provided custom value: ${h[3]}"
                    data[h[0]] = h[3].toDouble()
                }
            }
        }
        log.debug "$data"
    }

    CO2FootprintConfig(Map map, Map<String, Double> cpuData){
        def config = map ?: Collections.emptyMap()
        file = config.file ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_FILE_NAME
        summaryFile = config.summaryFile ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_SUMMARY_FILE_NAME
        reportFile = config.reportFile ?: CO2FootprintFactory.CO2FootprintReportObserver.DEF_REPORT_FILE_NAME
        ignoreCpuModel = config.ignoreCpuModel ?: false

        ci = 475
        if (config.ci && config.location)
            throw new IllegalArgumentException("Invalid combination of 'ci' and 'location' parameters specified for the CO2Footprint plugin. Please specify either 'ci' or 'location'!")
        if (config.ci)
            ci = config.ci
        if (config.location) {
            ci = retrieveCi(config.location)
            location = config.location
        }

        pue = config.pue ?: 1.67
        powerdrawMem = config.powerdrawMem ?: 0.3725
        powerdrawCpuDefault = config.powerdrawCpuDefault ?: 12.0
        cpuData['default'] = powerdrawCpuDefault

        if (config.customCpuTdpFile)
            loadCustomCpuTdpData(cpuData, config.customCpuTdpFile)
    }

    String getFile() { file }
    String getSummaryFile() { summaryFile }
    String getReportFile() { reportFile }
    Boolean getIgnoreCpuModel() { ignoreCpuModel }
    String getLocation() { location }
    Double getCI() { ci }
    Double getPUE() { pue }
    Double getPowerdrawMem() { powerdrawMem }
}
