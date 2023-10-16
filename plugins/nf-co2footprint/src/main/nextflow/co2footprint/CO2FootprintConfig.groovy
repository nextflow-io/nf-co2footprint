package nextflow.co2footprint

import groovy.transform.PackageScope


/**
 * This class allows model an specific configuration, extracting values from a map and converting
 *
 * In this plugin, the user can configure the output file names of the CO2 footprint calculations
 *
 * co2footprint {
 *     file = "co2footprint.txt"
 *     summaryFile = "co2footprint.summary.txt"
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
@PackageScope
class CO2FootprintConfig {

    final private String  file
    final private String  summaryFile
    final private String  reportFile
    final private String  location
    final private Double  ci    // CI: carbon intensity
    final private Double  pue   // PUE: power usage effectiveness efficiency, coefficient of the data centre
    final private Double  powerdrawMem  // Power draw of memory [W per GB]

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

    CO2FootprintConfig(Map map){
        def config = map ?: Collections.emptyMap()
        file = config.file ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_FILE_NAME
        summaryFile = config.summaryFile ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_SUMMARY_FILE_NAME
        reportFile = config.reportFile ?: CO2FootprintFactory.CO2FootprintReportObserver.DEF_REPORT_FILE_NAME

        ci = 475
        if (config.ci && config.country)
            throw new IllegalArgumentException("Invalid combination of 'ci' and 'location' parameters specified for the CO2Footprint plugin. Please specify either 'ci' and 'location'!")
        if (config.ci)
            ci = config.ci
        if (config.location) {
            ci = retrieveCi(config.location)
            location = config.location
        }

        pue = config.pue ?: 1.67
        powerdrawMem = config.powerdrawMem ?: 0.3725
    }

    String getFile() { file }
    String getSummaryFile() { summaryFile }
    String getReportFile() { reportFile }
    String getLocation() { location }
    Double getCI() { ci }
    Double getPUE() { pue }
    Double getPowerdrawMem() { powerdrawMem }
}
