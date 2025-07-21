package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j

import javax.naming.NameNotFoundException

@Slf4j
class ConfigParameters {
    final private HashMap<String, ConfigEntry> vault

    /**
    * Constructs the parameter store from a set of ConfigEntry instances. 
    *
    * @param configurationParameters Optional set of parameters to preload
    */
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
     * Fill the vault with the entries of the map, if the parameter is given
     *
     * @param configMap
     */
    void fill(Map<String, Object> configMap) {
        // Ensure configMap is not null
        configMap ?= [:]

        // Assign values from map to config
        configMap.each { name, value -> configure(name, value)}
    }

    /**
     * Configure the value of a specific parameter.
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
     * @param overwrite Whether to overwrite the previously set value
     */
    void setDefaults(List<Object> args=null, boolean overwrite=false) {
        vault.each { String name, ConfigEntry parameter -> parameter.setDefault(args, overwrite) }
    }

    /**
     * Initialize the vault.
     *
     * @param name Name of the parameter
     * @param value Value of the parameter
     * @param overwrite Whether to overwrite the previously set value
     */
    void setDefault(String name, List<Object> args=null, boolean overwrite=false) {
        vault.get(name).setDefault(args, overwrite)
    }

    /**
     * Get value of the parameter.
     *
     * @param name Name of the parameter
     * @return The value to the key
     */
    def get(String name) {
        if (has(name)){
            return vault.get(name).get()
        } else {
            String message = "`${name}` is not in configuration."
            log.error(message)
            throw new NameNotFoundException(message)
        }
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
     * Check if a parameter exists by name.
     *
     * @param name Parameter name
     * @return true if the parameter is defined, false otherwise
     */
    Boolean has(String name) {
        return vault.containsKey(name)
    }

    /**
     * Returns the number of registered parameters.
     *
     * @return Number of entries in the store
     */
    Integer getSize() { return vault.size() }

    /**
     * Get the current parameter entries.
     *
     * @return The entries as a Map
     */
    Map<String, Object> getEntries() {
        return vault.collectEntries { String name, ConfigEntry configParameter ->
            [name, configParameter.evaluate()]
        }
    }
}
