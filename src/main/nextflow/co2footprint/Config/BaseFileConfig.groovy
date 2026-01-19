package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j
import nextflow.config.spec.ConfigOption
import nextflow.script.dsl.Description
import java.nio.file.Path

/**
 * Sub-config base class for configuring the trace, summary and report output files. Stores
 * `enabled` and `file` parameters, and declares a `name` field in subclasses to control
 * the default output file name for trace, summary and report files.
 *
 * @author Murray Wham <murray.wham@ed.ac.uk>
 */
@Slf4j
class BaseFileConfig {
    final String name
    final String ending

    @ConfigOption(types=[String, GString])
    @Description('Path to the file.')
    final Path file

    @ConfigOption
    @Description('Whether to enable the file creation.')
    final Boolean enabled

    /**
     * Load a nested map configuration and set up defaults and fallbacks.
     *
     * @param subConfigName  Name for this sub-config - used to get a default file name for this output file
     * @param configMap      Map of configuration values (from Nextflow config)
     */
    BaseFileConfig(Map<String, Object> fileConfigMap, String timestamp, String subConfigName, String fileEnding='txt') {
        this.name = subConfigName
        this.ending = fileEnding ?: 'txt'

        file = Path.of(fileConfigMap.remove('file') as String ?: "co2footprint_${name}_${timestamp}.${ending}")
        enabled =  fileConfigMap.containsKey('enabled') ? fileConfigMap.remove('enabled') : true

        // Check whether all parameters were included successfully
        if (!fileConfigMap.isEmpty()){
            log.debug("`co2footprint.${name}` configuration scope contains unused parameters ${fileConfigMap.keySet()}.")
        }
    }
}
