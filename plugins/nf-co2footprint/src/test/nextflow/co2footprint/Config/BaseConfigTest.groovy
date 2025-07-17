package nextflow.co2footprint.Config

import spock.lang.Specification

class BaseConfigTest extends Specification{
    def 'Base configuration class should be correctly configured' () {
        when:
        BaseConfig config = new BaseConfig(parameters as Set, configMap)

        then:
        config.parameters.getEntries() == expectedConfigMap

        where:
        parameters                  || configMap    || expectedConfigMap
        [new ConfigEntry('a')] || [a: 1] || [a: 1]
        [new ConfigEntry('a')] || [b: 1] || [a: null]
        []                          || [a: 1]       || [:]
    }

    def 'Test evaluation'() {
        when:
        BaseConfig config = new BaseConfig([new ConfigEntry('a')] as Set, [a: { -> 1}])

        then:
        config.evaluate('a') == 1
    }
}
