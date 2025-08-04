package nextflow.co2footprint.Config

import spock.lang.Specification

class BaseConfigTest extends Specification{
    def 'Base configuration class should be correctly configured' () {
        when:
        BaseConfig config = new BaseConfig(configMap)

        then:
        config.getValueMap() == expectedValueMap

        where:
        configMap   || expectedValueMap
        [a: 1]      || [a: 1]
        [:]         || [:]
    }

    def 'Test evaluation'() {
        when:
        BaseConfig config = new BaseConfig([a: { -> 1}])

        then:
        config.value('a') as Integer == 1
    }

    def 'Nested config' () {
        when:
        BaseConfig nestedConfig = new BaseConfig(['a': 0])
        BaseConfig config = new BaseConfig([nested: nestedConfig])
        config.value('nested').set('a', 1)

        then:
        config.value('nested').value('a') == 1
    }

    def 'Removal or adding of entries -> error' () {
        setup:
        String message = 'Config instance entries can not be removed or added after initialization.'

        when:
        BaseConfig config = new BaseConfig([a: 1])
        Exception exception = null
        try {
            config."${method}"(*args)
        } catch (UnsupportedOperationException e) {
            exception = e
        }

        then:
        config.getValueMap() == [a: 1]
        exception.message == message

        where:
        method      || args
        'put'       || ['a', new ConfigEntry('a')]
        'putAll'    || [['a': new ConfigEntry('a')]]
        'remove'    || ['a']
        'clear'     || []
    }
}