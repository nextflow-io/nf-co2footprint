package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j


/**
 * Base class for configurations
 */
@Slf4j
class BaseConfig {
    final private ConfigParameters parameters

    BaseConfig(def parameters=[], Map<String, Object> configMap=null) {
        // Initializes parameters
        if (parameters instanceof ConfigParameters) { this.parameters = parameters  }
        else { this.parameters = new ConfigParameters(parameters as Set) }

        // Adds mapped entries to config
        if (configMap != null) { fill(configMap) }

        // Sets defaults if not set by configure / fill
        setDefaults()
    }

    ConfigParameters getParameters() { parameters }

    /**
     * Add a Config Parameter with args and kwArgs.
     *
     * @param args Argument list to be considered first
     * @param keywordArgs Keyword arguments to complement the argument list.
     */
    void addParameter(List<?> args, Map<String, ?> keywordArgs=[:]) {
        parameters.add(args, keywordArgs)
    }

    /**
     * Fills the parameters with the mapped entries
     *
     * @param configMap Map with entries for the config
     */
    void fill(Map<String, Object> configMap) {
        parameters.fill(configMap)
    }

    /**
     * Initialize the parameters.
     *
     * @param args Arguments for default function, default `null` skips function initialization
     * @param overwrite Whether to overwrite the previously set value
     */
    void setDefaults(List<Object> args=null, boolean overwrite=false) {
        parameters.setDefaults(args, overwrite)
    }

    /**
     * Initialize the parameters.
     *
     * @param name Name of the parameter
     * @param value Value of the parameter
     * @param overwrite Whether to overwrite the previously set value
     */
    void setDefault(String name, List<Object> args, boolean overwrite=false) {
        parameters.setDefault(name, args, overwrite)
    }

    /**
     * Get value of the parameter.
     *
     * @param name Name of the parameter
     * @return The value to the key
     */
    def get(String name) { return parameters.get(name) }

    /**
     * Evaluate the parameter if it is a function.
     *
     * @param name Name of the parameter
     * @return The evaluated value of the key
     */
    <T> T evaluate(String name) { return parameters.evaluate(name) }

    /**
     * Set value of the parameter.
     *
     * @param name Name of the parameter
     * @param value Value to be set to
     */
    void set(String name, def value) { parameters.set(name, value) }

    /**
     * Set value of the parameter, when it's null before.
     *
     * @param name Name of the parameter
     * @param value Value to be set to
     */
    void setEmpty(String name, def value) {
        parameters.setEmpty(name, value)
    }

    /**
     * Does the config have this parameter?
     *
     * @param name Name of the parameter
     * @return Whether the property exists in the config instance
     */
    Boolean has(String name) { return parameters.has(name) }
}
