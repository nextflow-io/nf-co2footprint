package nextflow.co2footprint.Records

import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.CIMatch
import spock.lang.Specification

class CiRecordTest extends Specification {

    def "should fallback to CIDataMatrix if API key is missing"() {
        given:
        String location = "DE"
        CIDataMatrix ciData = new CIDataMatrix(
                [[50.0]],
                Set.of('Carbon intensity gCO₂eq/kWh (Life cycle)') as LinkedHashSet<Object>,
                Set.of('DE') as LinkedHashSet<Object>
        )
        ciData.findCiInMatrix(location) >> new CIMatch('DE', 50.0)

        when:
        CiRecord ci = new CiRecord(null, ciData, location, null)

        then:
        ci.value == 50.0
    }

    def "should fallback to GLOBAL if no location-specific data is found"() {
        given:
        String location = "UNKNOWN"
        CIDataMatrix ciData = new CIDataMatrix(
                [[80.0]],
                Set.of('Carbon intensity gCO₂eq/kWh (Life cycle)') as LinkedHashSet<Object>,
                Set.of('GLOBAL') as LinkedHashSet<Object>
        )

        when:
        CiRecord ci = new CiRecord(null, ciData, location, null)

        then:
        ci.value == 80.0
    }

    def "should return null if real-time retrieval and fallback both fail"() {
        given:
        String location = 'UNKNOWN'
        CIDataMatrix ciData = Mock(CIDataMatrix)

        when:
        CiRecord ci = new CiRecord(null, ciData, location, null)

        then:
        ci.value == null
    }
}
