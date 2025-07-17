package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j

/**
 * Base class for configurations
 */
@Slf4j
class BaseConfig {
    protected HashMap<String, Object> constants
    final private HashMap<String, ConfigParameter> parameters

    BaseConfig(Set<ConfigParameter> parameters, Map<String, Object> configMap, HashMap<String, Object> constants=[:]) {
        this.constants = constants
        this.parameters = parameters.collectEntries { ConfigParameter configParameter ->
            [configParameter.name, configParameter]
        } as HashMap<String, ConfigParameter>

        // Ensure configMap is not null
        configMap ?= [:]

        // Assign values from map to config
        configMap.each { name, value -> configure(name, value)}
    }

    HashMap<String, Object> getConstants() { constants }
    HashMap<String, ConfigParameter> getParameters() { parameters }


    /**
     * Configure a parameter of the configuration
     *
     * @param name Name of the parameter
     * @param value Value of the parameter
     */
    void configure(String name, def value) {
        if (has(name)) {
            if (get(name) == null || value in get(name))
            set(name, value)
        } else {
            // Log warning and skip the key
            log.warn("Skipping unknown configuration key: '${name}'")
        }
    }

    /**
     * Get value of the parameter.
     *
     * @param name Name of the parameter
     * @return The value to the key
     */
    def get(String name) {
        return parameters.get(name).get()
    }

    /**
     * Evaluate the parameter if it is a function.
     *
     * @param name Name of the parameter
     * @return The evaluated value of the key
     */
    <T> T evaluate(String name) {
        T value = parameters.get(name).evaluate()
        return value
    }

    /**
     * Set value of the parameter.
     *
     * @param name Name of the parameter
     * @param value Value to be set to
     */
    void set(String name, def value) {
        ConfigParameter parameter = parameters.get(name)
        parameter.set(value)
    }

    /**
     * Does the config have this parameter?
     *
     * @param name Name of the parameter
     * @return Whether the property exists in the config instance
     */
    Boolean has(String name) {
        return parameters.containsKey(name)
    }

    /**
     * Get the current parameter entries.
     *
     * @return The entries as a Map
     */
    Map<String, Object> getEntries() {
        return getParameters().collectEntries { String name, ConfigParameter configParameter ->
            [name, configParameter.get()]
        }
    }
}
