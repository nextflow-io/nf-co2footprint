package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j

@Slf4j
class ConfigParameters {
    final private HashMap<String, ConfigEntry> vault

    ConfigParameters(Set<ConfigEntry> configurationParameters=[]) {
        this.vault = configurationParameters.collectEntries { ConfigEntry configParameter ->
            [configParameter.name, configParameter]
        } as HashMap<String, ConfigEntry>
    }

    /**
     * Add a Config Parameter to the vault.
     *
     * @param parameter Constructed parameter that is added
     */
    void add(ConfigEntry parameter) {
        this.vault.put(parameter.getName(), parameter)
    }

    /**
     * Add a Config Parameter with args and kwArgs.
     *
     * @param args Argument list to be considered first
     * @param keywordArgs Keyword arguments to complement the argument list.
     */
    void add(List<?> args, Map<String, ?> keywordArgs=[:]) {
        List<String> optionOrder = ['name', 'defaultValue', 'allowedTypes', 'returnType', 'description']
        args.addAll(
                optionOrder.subList(args.size(), optionOrder.size()).collect(
                        { String argName -> keywordArgs.get(argName) }
                )
        )
        add(new ConfigEntry(*args))
    }

    /**
     *
     * @param configMap
     */
    void fill(Map<String, Object> configMap) {
        // Ensure configMap is not null
        configMap ?= [:]

        // Assign values from map to config
        configMap.each { name, value -> configure(name, value)}
        initialize()
    }

    /**
     * Configure a parameter of the configuration.
     *
     * @param name Name of the parameter
     * @param value Value of the parameter
     */
    void configure(String name, def value) {
        if (has(name)) {
            ConfigEntry parameter = vault.get(name)
            parameter.configure(value)
        } else {
            // Log warning and skip the key
            log.warn("Skipping unknown configuration key: '${name}'")
        }
    }

    /**
     * Initialize the vault.
     *
     * @param name Name of the parameter
     * @param value Value of the parameter
     */
    void initialize() {
        vault.each { String name, ConfigEntry parameter -> parameter.initialize() }
    }

    /**
     * Get value of the parameter.
     *
     * @param name Name of the parameter
     * @return The value to the key
     */
    def get(String name) {
        return vault.get(name).get()
    }

    /**
     * Evaluate the parameter if it is a function.
     *
     * @param name Name of the parameter
     * @return The evaluated value of the key
     */
    <T> T evaluate(String name) {
        T value = vault.get(name).evaluate()
        return value
    }

    /**
     * Set value of the parameter.
     *
     * @param name Name of the parameter
     * @param value Value to be set to
     */
    void set(String name, def value) {
        ConfigEntry parameter = vault.get(name)
        parameter.set(value)
    }
    /**
     * Set value of the parameter, when it's null before.
     *
     * @param name Name of the parameter
     * @param value Value to be set to
     */
    void setEmpty(String name, def value) {
        ConfigEntry parameter = vault.get(name)
        if (parameter.get() == null) {
            parameter.set(value)
        }
    }

    /**
     * Does the config have this parameter?
     *
     * @param name Name of the parameter
     * @return Whether the property exists in the config instance
     */
    Boolean has(String name) {
        return vault.containsKey(name)
    }

    /**
     * Get the current parameter entries.
     *
     * @return The entries as a Map
     */
    Map<String, Object> getEntries() {
        return vault.collectEntries { String name, ConfigEntry configParameter ->
            [name, configParameter.get()]
        }
    }
}
