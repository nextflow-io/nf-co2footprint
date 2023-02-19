package nextflow.hello

import groovy.transform.PackageScope


/**
 * This class allows model an specific configuration, extracting values from a map and converting
 *
 * In this plugin, the user can configure how the messages are prefixed with a String, i.e.
 * due a nextflow.config
 *
 * hello {
 *     prefix = '>>'
 * }
 *
 * when the plugin reverse a String it will append '>>' at the beginning instead the default 'Mr.'
 *
 * We anotate this class as @PackageScope to restrict the access of their methods only to class in the
 * same package
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@PackageScope
class HelloConfig {

    final private String prefix

    HelloConfig(Map map){
        def config = map ?: Collections.emptyMap()
        prefix = config.prefix ?: 'Mr.'
    }

    String getPrefix() { prefix }
}
