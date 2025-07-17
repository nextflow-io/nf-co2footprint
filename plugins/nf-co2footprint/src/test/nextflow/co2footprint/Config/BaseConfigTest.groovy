package nextflow.co2footprint.Config

import spock.lang.Specification

class BaseConfigTest extends Specification{
    def 'Base configuration class should be correctly configured' () {
        when:
        BaseConfig config = new BaseConfig(parameters as Set, configMap, constants)

        then:
        config.getEntries() == expectedConfigMap

        where:
        parameters                          || constants    || configMap    || expectedConfigMap
        [new ConfigParameter('a')]          || [:]          || [a: 1]       || [a: 1]
        [new ConfigParameter('a')]          || [:]          || [b: 1]       || [a: null]
        []                                  || [:]          || [a: 1]       || [:]
    }

    def 'Test evaluation'() {
        when:
        BaseConfig config = new BaseConfig([new ConfigParameter('a')] as Set, [a: { -> 1}])

        then:
        config.evaluate('a') == 1
    }

}
