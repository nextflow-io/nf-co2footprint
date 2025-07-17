package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j


/**
 * Base class for configurations
 */
@Slf4j
class BaseConfig {
    final private ConfigParameters parameters

    BaseConfig(def parameters=[], Map<String, Object> configMap=null) {
        if (parameters instanceof ConfigParameters) { this.parameters = parameters  }
        else { this.parameters = new ConfigParameters(parameters as Set) }

        if (configMap != null) { this.parameters.fill(configMap) }
        initialize()
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
     * Initialize the parameters.
     *
     * @param name Name of the parameter
     * @param value Value of the parameter
     */
    void initialize() { parameters.initialize() }

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
