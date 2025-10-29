package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Config.BaseConfig
import nextflow.trace.TraceHelper
import java.nio.file.Path
import java.lang.Boolean

/**
 * Sub-config base class for configuring the trace, summary and report output files. Stores
 * `enabled` and `file` parameters, and declares a `name` field in subclasses to control
 * the default output file name for trace, summary and report files.
 *
 * @author Murray Wham <murray.wham@ed.ac.uk>
 */
@Slf4j
class CO2FootprintSubConfig extends BaseConfig {
    private String name
    private String timestamp = TraceHelper.launchTimestampFmt()

    private void defineParameters() {
        // Name, description, default value or function, return type, additional allowed types
        defineParameter(
                'enabled', 'Whether this file is to be generated', true, Boolean
        )
        defineParameter(
                'file', 'Path to the output file',
                "co2footprint_${name}_${timestamp}.txt", String, Set.of(GString, Path)
        )
    }

    /**
     * Load a nested map configuration and set up defaults and fallbacks.
     *
     * @param subConfigName  Name for this sub-config - used to get a default file name for this output file
     * @param configMap      Map of configuration values (from Nextflow config)
     */
    CO2FootprintSubConfig(String subConfigName, Map<String, Object> configMap) {
        this.name = subConfigName

        // Define the possible parameters of the configuration
        defineParameters()

        // Initialize defaults
        setDefaults()

        // Ensure configMap is not null
        configMap ?= [:]

        // Assign values from map to config
        configMap.each { name, value ->
            if (this.containsKey(name)) {
                this.get(name).set(value)
            } else if (name != 'params') {
                log.debug("Skipping unknown configuration key: '${name}'")
            } 
        }
    }
}
