package nextflow.co2footprint.Config

import groovy.util.logging.Slf4j

/**
 * Base class for configurations, which extends LinkedHashMap.
 * The map is made unmodifiable by overriding modification methods.
 * Entries can be changed by calling the `set` method on the respective {@link ConfigEntry}.
 */
@Slf4j
class BaseConfig extends LinkedHashMap<String, ConfigEntry> implements GroovyObject {
    /**
     * Add all elements in the map to the BaseConfig instance.
     *
     * @param configMap Map with all entries that are used to instantiate the configuration.
     */
    BaseConfig(Map<String, Object> configMap=[:]) {
        super()

        // Ensure ConfigEntry as Entries by converting Objects if necessary
        if (configMap.values().any( { Object val -> !(val instanceof ConfigEntry)} )) {
            configMap = configMap.collectEntries(
                    { String name, Object value -> [name, new ConfigEntry(name, null, value)] }
            ) as Map<String, ConfigEntry>
        }
        super.putAll(configMap)

        // Set values to default as a start
        setDefaults()
    }

    // ##### Initialization methods #####
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

    /**
     * Sets the entries to their default values.
     */
    void setDefaults(Map<String, ConfigEntry> map=this) { map.each { String name, ConfigEntry entry -> entry.setDefault() } }

    //  ###### Accessor methods ######
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

    // ##### Disable removal/adding of new entries with normal Map methods #####
    /**
     * Throw an exception upon modification attempts.
     *
     * @throws UnsupportedOperationException
     */
    static void throwModificationException() throws UnsupportedOperationException {
        String message = 'Config instance entries can not be removed or added after initialization.'
        log.error(message)
        throw new UnsupportedOperationException(message)
    }

    @Override
    ConfigEntry put(String key, ConfigEntry value) { throwModificationException() }

    @Override
    ConfigEntry remove(Object key) { throwModificationException() }

    @Override
    void clear() { throwModificationException() }

    @Override
    void putAll(Map<? extends String, ? extends ConfigEntry> map) { throwModificationException() }
}
