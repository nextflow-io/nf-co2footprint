package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j

/**
 * Base class for configurations, which extends LinkedHashMap.
 * The map is made unmodifiable by overriding modification methods.
 * Entries can be changed by calling a set method on the respective {@link ConfigEntry}.
 */
@Slf4j
class BaseConfig extends LinkedHashMap<String, ConfigEntry> {

    /**
     * Add all elements in the map to the BaseConfig instance.
     *
     * @param configMap Map with all entries that are used to instantiate the configuration.
     */
    BaseConfig(Map<String, ConfigEntry> configMap=[:]) {
        super(configMap)
    }

    /**
     * Throws an error upon modification attempts.
     *
     * @param message Message that is reported in the error.
     * @throws UnsupportedOperationException
     */
    static void throwModificationException(String message) throws UnsupportedOperationException {
        message = "Configuration not modifieable. ${message}."
        log.error(message)
        throw new UnsupportedOperationException(message)
    }

    // Make entries only modifiable via value.set()
    @Override
    ConfigEntry put(String key, ConfigEntry value) { throwModificationException("Can't add entry: ${key}, ${value}") }

    @Override
    ConfigEntry remove(Object key) { throwModificationException("Can't remove entry: ${key}") }

    @Override
    void clear() { throwModificationException("Can't clear config") }

    @Override
    void putAll(Map<? extends String, ? extends ConfigEntry> map) { throwModificationException("Can't add new entries: ${map}") }

    /**
     *  Define a new parameter in the configuration. Overwrites parameters that were previously set.
     *
     * @param name Parameter name and key
     * @param description Short description
     * @param defaultValue Default value or function
     * @param returnType Type of the return during evaluation
     * @param additionalTypes Additional types that should be allowed as intermediaries
     */
    void defineParameter(
            String name, String description=null, def defaultValue=null,
            Class returnType=null, Set<Class> additionalTypes=[]
    ) {
        super.put(name, new ConfigEntry(name, description, defaultValue, returnType, additionalTypes))
    }

    //
    // Syntactic sugar
    //

    /**
     * Set a new value into the config entry.
     *
     * @param key Name of the entry
     * @param value Value to be set to the entry
     */
    void set(String key, Object value) { get(key).set(value) }


    /**
     * Fill a config entry with a new value, if it is not null.
     *
     * @param key Name of the entry
     * @param value Value to be set to the entry
     */
    void fill(String key, Object value) { get(key).fill(value) }


    /**
     * Get the correctly cast and executed result of a value.
     *
     * @param key Name of the entry
     */
    <T> T value(String key) { return get(key).evaluate() }

    /**
     * The value map of a list of keys.
     *
     * @param keys Names of the entries
     * @return Map with evaluated values
     */
    Map<String, Object> getValueMap(Set<String> keys=keySet()) {
        Map<String, Object> valueMap = [:]
        keys.each { String key -> valueMap.put(key, value(key)) }
        return  valueMap
    }
}
