package nextflow.co2footprint.Config

import spock.lang.Specification

class ConfigParametersTest extends Specification{
    def 'Test parameter making'() {
        when:
        ConfigParameters parameters = new ConfigParameters()
        parameters.add(['a', 1], [description: 'x'])

        then:
        parameters.getEntries() == [a: null]
        parameters.setDefaults()
        parameters.getEntries() == [a: 1]
    }
}
