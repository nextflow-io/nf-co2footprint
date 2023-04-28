package nextflow.co2footprint

import groovy.transform.PackageScope


/**
 * This class allows model an specific configuration, extracting values from a map and converting
 *
 * In this plugin, the user can configure how the messages are prefixed with a String, i.e.
 * due a nextflow.config
 *
 * co2footprint {
 *     prefix = '>>'
 * }
 *
 * when the plugin reverse a String it will append '>>' at the beginning instead the default 'Mr.'
 *
 * We anotate this class as @PackageScope to restrict the access of their methods only to class in the
 * same package
 *
 * @author : Sabrina Krakau <sabrinakrakau@gmail.com>
 *
 */
@PackageScope
class CO2FootprintConfig {

    // final private Boolean enabled
    final private String  file
    final private String  summaryFile

    CO2FootprintConfig(Map map){
        def config = map ?: Collections.emptyMap()
        // if (config.enabled == null ) {
        //     enabled = true
        // } else {
        //     enabled = false
        //     return
        // }
        file = config.file ?: CO2FootprintObserver.DEF_FILE_NAME
        summaryFile = config.summaryFile ?: CO2FootprintObserver.DEF_SUMMARY_FILE_NAME
    }

    // Boolean isEnabled() { enabled }

    String getFile() { file }

    String getSummaryFile() { summaryFile }
}
