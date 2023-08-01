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
 * @author : Sabrina Krakau <sabrinakrakau@gmail.com>
 *
 */
@PackageScope
class CO2FootprintConfig {

    final private String  file
    final private String  summaryFile
    final private Double  ci    // CI: carbon intensity
    final private Double  pue   // PUE: power usage effectiveness efficiency, coefficient of the data centre
    final private Double  powerdrawMem  // Power draw of memory [W per GB]

    // Retrieve CI value from file containing CI values for different locations
    protected Double retrieveCi(String country) {
        def inData = new InputStreamReader(this.class.getResourceAsStream('/ci_values.csv')).text

        Double localCi = 0.0
        for (String line : inData.readLines()) {
            def row = line.split(",")
            if (row[0] == country)
                localCi = row[1].toFloat()
        }
        if (localCi == 0.0)
            throw new IllegalArgumentException("Invalid 'country' parameter: $country. Could not be found in 'ci_values.csv'.")

        return localCi
    }

    CO2FootprintConfig(Map map){
        def config = map ?: Collections.emptyMap()
        file = config.file ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_FILE_NAME
        summaryFile = config.summaryFile ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_SUMMARY_FILE_NAME

        ci = 475
        if (config.ci && config.country)
            throw new IllegalArgumentException("Invalid combination of 'ci' and 'country' parameters specified for the CO2Footprint plugin. Please specify either 'ci' and 'country'!")
        if (config.ci)
            ci = config.ci
        if (config.country)
            ci = retrieveCi(config.country)

        pue = config.pue ?: 1.67
        powerdrawMem = config.powerdrawMem ?: 0.3725
    }

    String getFile() { file }

    String getSummaryFile() { summaryFile }

    Double getCI() { ci }

    Double getPUE() { pue }
    Double getPowerdrawMem() { powerdrawMem }
}
