package nextflow.co2footprint.Config

import spock.lang.Specification

class ConfigEntryTest extends Specification {
    def 'Initialize ConfigParameter correctly' () {
        when:
        ConfigEntry parameter = new ConfigEntry(name, defaultValue, allowedTypes, returnType, description)

        then:
        parameter.toMap() == [name: name, value: null, allowedTypes: expAllowedTypes, returnType: expReturnType, description: description]
        parameter.setDefault(initializationArgs)
        parameter.toMap() == [name: name, value: expValue, allowedTypes: expAllowedTypes, returnType: expReturnType, description: description]
        def evaluated = parameter.evaluate()
        if (evaluated != null) { evaluated in expReturnType }

        where:
        name    || defaultValue                     || allowedTypes || returnType   || description  || initializationArgs   || expValue || expAllowedTypes  || expReturnType
        'a'     || 1                                || [Integer]    || Integer      || 'a'          || []                   || 1        || [Integer]        || Integer
        'b'     || '1'                              || [String]     || null         || 'b'          || []                   || '1'      || [String]         || String
        'c'     || 1 as Long                        || null         || null         || 'long Long'  || []                   || 1l       || [Long]           || Long
        'd'     || null                             || null         || null         || 'd'          || []                   || null     || [Object]         || Object
        'e'     || { -> 3 }                         || null         || Integer      || 'd'          || []                   || 3        || [Integer]        || Integer
        'f'     || {x, y -> "${x}${y}".toString()}  || null         || String       || 'd'          || [1, 3]               || '13'     || [String]         || String
    }

    def 'Evaluate function values correctly'() {
        when:
        ConfigEntry parameter = new ConfigEntry('a', 1, [Integer, Closure<Integer>])
        parameter.set({ -> 4})

        then:
        parameter.evaluate() == 4l
    }
}
