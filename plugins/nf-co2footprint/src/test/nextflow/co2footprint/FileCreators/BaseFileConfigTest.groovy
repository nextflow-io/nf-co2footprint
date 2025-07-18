package nextflow.co2footprint.FileCreators

import spock.lang.Specification

import java.nio.file.Path

class BaseFileConfigTest extends Specification {
    def 'Test base class' () {
        when:
        BaseFileConfig config = new BaseFileConfig()

        then:
        config.getEnabled() == true
        String start = Path.of('pipeline_info', 'co2footprint_')
        String regex = "^${start}\\d{8}-\\d*.txt\$"
        config.getFile().matches(regex)
    }
}
