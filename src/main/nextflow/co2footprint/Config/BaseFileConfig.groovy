package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j
import nextflow.co2footprint.CO2FootprintConfig
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
    final boolean defaultEnabled

    protected final LinkedHashSet<String> usedKeys = [] as LinkedHashSet<String>


    /**
     * Parses a file-based sub-configuration for nf-co2footprint and sets up defaults and fallbacks.
     *
     * @param subConfigName  Name of the configuration scope
     * @param fileEnding     Output file extension (default: txt)
     */
    BaseFileConfig(String subConfigName, String fileEnding, boolean defaultEnabled=true){
        this.name = subConfigName
        this.ending = fileEnding ?: 'txt'
        this.defaultEnabled = defaultEnabled
    }

    /**
     * Define a file path from the given config map and timestamp, as well as predefined variables.
     *
     * @param fileConfig The general config of this file.
     * @param timestamp A timestamp string.
     * @return The path to the file.
     */
    protected Path defineFile(Map<String, Object> fileConfig, String timestamp) {
        return Path.of(CO2FootprintConfig.getCollect('file', fileConfig, usedKeys) as String ?: "co2footprint_${name}_${timestamp}.${ending}")
    }

    /**
     * Define whether the construction of this file is enabled.
     *
     * @param fileConfig The general config of this file.
     * @return Whether or not to write the file.
     */
    protected boolean defineEnabled(Map<String, Object> fileConfig) {
        return fileConfig.containsKey('enabled') ? CO2FootprintConfig.getCollect('enabled', fileConfig, usedKeys) : defaultEnabled
    }

    /**
     * Define whether an existing file should be overwritten.
     *
     * @param fileConfig The general config of this file.
     * @return Whether or not to overwrite the file.
     */
    protected boolean defineOverwrite(Map<String, Object> fileConfig) {
        return fileConfig.containsKey('overwrite') ? CO2FootprintConfig.getCollect('overwrite', fileConfig, usedKeys) : true
    }
}
