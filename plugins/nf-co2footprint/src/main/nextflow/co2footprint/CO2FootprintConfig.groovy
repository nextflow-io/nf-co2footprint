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
 *     pue = 1.4
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
    final private Double  pue // PUE: power usage effectiveness (efficiency coefficient of the data centre)

    CO2FootprintConfig(Map map){
        def config = map ?: Collections.emptyMap()
        file = config.file ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_FILE_NAME
        summaryFile = config.summaryFile ?: CO2FootprintFactory.CO2FootprintTextFileObserver.DEF_SUMMARY_FILE_NAME
        pue = config.pue ?: 1.67
    }

    String getFile() { file }

    String getSummaryFile() { summaryFile }

    Double getPUE() { pue }
}
