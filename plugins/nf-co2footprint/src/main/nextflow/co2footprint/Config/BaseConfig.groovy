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

        // Ensures that all entries in configMap are ConfigEntry objects
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
     * @param name Parameter name
     * @param description Short description
     * @param defaultValue Default value or function
     * @param returnType Type of the return during evaluation
     * @param additionalTypes Set of additional acceptable types 
     */
    void defineParameter(
            String name, String description=null, def defaultValue=null,
            Class returnType=null, Set<Class> additionalTypes=[]
    ) {
        super.put(name, new ConfigEntry(name, description, defaultValue, returnType, additionalTypes))
    }

    /**
     *  Applies default values to all ConfigEntry objects in the given map.
     *
     * @param map Map of configuration entries (defaults to 'this').
     *            The map must have String keys and ConfigEntry values.
     */
    void setDefaults(Map<String, ConfigEntry> map=this) { map.each { String name, ConfigEntry entry -> entry.setDefault() } }

    //  ###### Accessor methods ######
    /**
     * Set the value of a config entry.
     *
     * @param key Name of the entry
     * @param value Value to be set to the entry
     */
    void set(String key, Object value) { get(key).set(value) }

    /**
     * Fill a config entry with a new value, if it is null.
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
