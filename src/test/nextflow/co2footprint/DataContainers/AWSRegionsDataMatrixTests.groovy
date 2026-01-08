package nextflow.co2footprint.DataContainers

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Paths


class AWSRegionsDataMatrixTests extends Specification {
    @Shared
    AWSRegionsDataMatrix awsRegionsDataMatrix = AWSRegionsDataMatrix.fromCsv(
        Paths.get(this.class.getResource('/aws_regions/aws_regions_test.csv').toURI())
    )

    def 'Match region'() {
        when:
        AWSRegionsDataMatrix regionDataMatrix = awsRegionsDataMatrix.matchRegion(region)

        then:
        if (regionDataMatrix) {
            regionDataMatrix.getZoneId() == expectedZoneId
        }
        else {
            expectedZoneId == null
        }

        where:
        region          || expectedZoneId
        'eu-central-1'  || 'DE'
        'X'             || null
    }
}
