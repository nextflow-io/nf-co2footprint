package nextflow.co2footprint.Config

import spock.lang.Specification

class ConfigEntryTest extends Specification {
    def 'Initialize ConfigParameter correctly' () {
        when:
        ConfigEntry parameter = new ConfigEntry(name, description, defaultValue, returnType, additionalTypes)

        then:
        parameter.toMap() == [name: name, description: description, value: null, returnType: expReturnType, allowedTypes: expAllowedTypes] as Map<String, Object>
        parameter.setDefault(initializationArgs)
        parameter.toMap() == [name: name, description: description, value: expValue, returnType: expReturnType, allowedTypes: expAllowedTypes]
        def evaluated = parameter.evaluate()
        if (evaluated != null) { evaluated in expReturnType }

        where:
        name    || defaultValue                     || additionalTypes  || returnType   || description  || initializationArgs   || expValue || expAllowedTypes  || expReturnType
        'a'     || 1                                || Set.of(Integer)  || Integer      || 'a'          || []                   || 1        || Set.of(Integer)  || Integer
        'b'     || '1'                              || Set.of(String)   || null         || 'b'          || []                   || '1'      || Set.of(String)   || String
        'c'     || 1 as Long                        || null             || null         || 'long Long'  || []                   || 1l       || Set.of(Long)     || Long
        'd'     || null                             || null             || null         || 'd'          || []                   || null     || Set.of(Object)   || Object
        'e'     || { -> 3 }                         || null             || Integer      || 'd'          || []                   || 3        || Set.of(Integer)  || Integer
        'f'     || {x, y -> "${x}${y}".toString()}  || null             || String       || 'd'          || [1, 3]               || '13'     || Set.of(String)   || String
    }

    def 'Evaluate function values correctly'() {
        when:
        ConfigEntry parameter = new ConfigEntry('a', null, 1, null, Set.of(Integer, Closure<Integer>))
        parameter.set({ -> 4})

        then:
        parameter.evaluate() == 4l
    }

    def 'Ensure modification via map is not possible' () {
        when:
        ConfigEntry parameter = new ConfigEntry('a', null, [], null, Set.of(Integer, Closure<Integer>))
        parameter.setDefault()
        Map<String, Object> map = parameter.toMap()
        map.get('value').add(1)

        then:
        map.get('value') == [1]
        parameter.evaluate() == []
    }
}