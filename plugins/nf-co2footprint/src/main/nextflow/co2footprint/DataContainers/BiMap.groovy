package nextflow.co2footprint.DataContainers

/**
 * Bidirectional Map with maintained K-V pairs in both directions
 * @param <K>
 * @param
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
class BiMap<K, V> {

    private final Map<K, V> keyToValueMap = new LinkedHashMap<>()
    private final Map<V, K> valueToKeyMap = new LinkedHashMap<>()

    BiMap(Map<K,V> map = [:], List<K> keys = [], List<V> values = []) {
        if (keys && values) {
            assert  keys.size() == values.size()
            assert  keys.unique() && values.unique()
            for (int i = 0; i < keys.size(); i++) { this.put(keys[i], values[i]) }
        }
        else {
            map.each { key, value -> this.put(key, value) }
        }
    }

    @Override
    boolean equals(Object other) {
        if ( other == null ) { return false }
        if ( !(other instanceof BiMap) ) { return false }
        BiMap bimap = (BiMap) other
        if (this.keyToValueMap == bimap.keyToValueMap && this.valueToKeyMap == bimap.valueToKeyMap) { return true }
        return false
    }

    /**
     * Method to put a key-value pair into the bidirectional map
     * @param key
     * @param value
     */
    void put(K key, V value)
    {
        keyToValueMap.put(key, value)
        valueToKeyMap.put(value, key)
    }

    // method to get a value based on the key
    V getValue(K key) {
        return keyToValueMap.get(key)
    }

    // method to get a key based on the value
    K getKey(V value) {
        return valueToKeyMap.get(value)
    }

    // method to check if a key exists in the map
    boolean containsKey(K key) {
        return keyToValueMap.containsKey(key)
    }

    // method to check if a value exists in the map
    boolean containsValue(V value) {
        return valueToKeyMap.containsKey(value)
    }

    // method to check if a key exists in the map
    List filterKeys(Closure filterFunction) {
        return keyToValueMap.keySet().stream().filter(filterFunction).toList()
    }

    // method to filter the Values for a function
    List filterValues(Closure filterFunction) {
        return valueToKeyMap.keySet().stream().filter(filterFunction).toList()
    }

    // method to remove a key-value pair based on the key
    V removeByKey(K key) {
        final V value = keyToValueMap.remove(key)
        valueToKeyMap.remove(value)
        return value
    }

    // method to remove a key-value pair based on the key
    K removeByValue(V value) {
        final K key = valueToKeyMap.remove(value)
        keyToValueMap.remove(key)
        return key
    }

    // method to remove all key-value pairs from the bidirectional map
    BiMap<K,V> clear() {
        keyToValueMap.clear()
        valueToKeyMap.clear()
        return this
    }

    // method to get a set of all keys in the bidirectional map
    Set<K> keySet() {
        return keyToValueMap.keySet()
    }

    // method to get a set of all values in the bidirectional map
    Set<V> valueSet() {
        return valueToKeyMap.keySet()
    }

    Integer size() {
        return keyToValueMap.size()
    }

    BiMap<K,V> sortByValues() {
        final List<V> values = valueToKeyMap.keySet().sort()
        final List<K> keys = values.collect { value -> valueToKeyMap[value]}

        return new BiMap(null, keys, values)
    }

    BiMap<K,V> sortByKeys() {
        final List<K> keys = keyToValueMap.keySet().sort()
        final List<V> values = keys.collect { key -> keyToValueMap[key]}

        return new BiMap(null, keys, values)
    }

    String toString() {
        return keyToValueMap.toString()
    }
}
