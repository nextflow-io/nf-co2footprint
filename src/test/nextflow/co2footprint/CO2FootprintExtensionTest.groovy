package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class CO2FootprintExtensionTest extends Specification {
    @Shared
    Session session = new Session([co2footprint: [ci: 100.0]])

    def 'Should calculate the CO2Footprint from an old trace file'() {
        when:
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)
        List<CO2Record> co2Records = extension.calculateCO2(
                this.class.getResource('/execution-trace-test.txt').path as Path, null, null,
                false
        )

        then:
        co2Records.size() == 8
        co2Records[7] == new CO2Record(
                3.2729169285E-6, 3.2729169285E-4, null, 2.777778E-4, 100.0,
                1, 11.41, 100.0, 1, 'VALUE_TESTING', null
        )
    }
}
