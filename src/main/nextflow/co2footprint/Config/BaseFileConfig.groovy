package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.script.dsl.Description
import java.nio.file.Path

/**
 * Base configuration class for file-based outputs of the nf-co2footprint plugin. Stores
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
     * Parses a file-based sub-configuration for nf-co2footprint and sets up defaults and fallbacks.
     *
     * @param fileConfigMap  User-provided configuration options
     * @param timestamp      Timestamp for generating default filenames
     * @param subConfigName  Name of the configuration scope
     * @param fileEnding     Output file extension (default: txt)
     */
    BaseFileConfig(Map<String, Object> fileConfigMap, String timestamp, String subConfigName, String fileEnding='txt') {
        this.name = subConfigName
        this.ending = fileEnding ?: 'txt'

        LinkedHashSet<String> usedKeys = [] as LinkedHashSet<String>

        file = Path.of(CO2FootprintConfig.getCollect('file', fileConfigMap, usedKeys) as String ?: "co2footprint_${name}_${timestamp}.${ending}")
        enabled =  fileConfigMap.containsKey('enabled') ? CO2FootprintConfig.getCollect('enabled', fileConfigMap, usedKeys) : true

        CO2FootprintConfig.checkKeyUsage(fileConfigMap, usedKeys)
    }
}
