package nextflow.co2footprint.DataContainers


import spock.lang.Specification
import java.nio.file.Path
import java.nio.file.Files

class CIDataMatrixTest extends Specification {

    def "should create CIDataMatrix from given data"() {
        given:
        def data = [[1, 2], [3, 4]]
        def columns = ['col1', 'col2'] as LinkedHashSet
        def rows = ['row1', 'row2'] as LinkedHashSet

        when:
        def matrix = new CIDataMatrix(data, columns, rows)

        then:
        matrix.data == data
        matrix.getOrderedColumnKeys() == columns
        matrix.getOrderedRowKeys() == rows
    }

    def "should create CIDataMatrix from CSV"() {
        given:
        // Uses system temp directory
        Path tempDir = Files.createTempDirectory("test_dir")
        Path tempFile = tempDir.resolve("fallbackCIDataTable.csv")
        tempFile.toFile().text = """\
        Zone id,Carbon intensity gCO₂eq/kWh (Life cycle)
        US,100
        DE,50
        """.stripIndent()

        when:
        def matrix = CIDataMatrix.fromCsv(tempFile)

        then:
        matrix instanceof CIDataMatrix
        matrix.get('US', 'Carbon intensity gCO₂eq/kWh (Life cycle)') == 100
        matrix.get('DE', 'Carbon intensity gCO₂eq/kWh (Life cycle)') == 50

        
        cleanup:
        Files.deleteIfExists(tempFile)
        Files.deleteIfExists(tempDir)
        
    }

    def "should find carbon intensity in matrix"() {
        given:
        def data = [[123.45]]
        def matrix = Spy(CIDataMatrix, constructorArgs: [data, ['Carbon intensity gCO₂eq/kWh (Life cycle)'] as LinkedHashSet, ['ZONE'] as LinkedHashSet])

        matrix.get('ZONE', 'Carbon intensity gCO₂eq/kWh (Life cycle)') >> "123.45"

        when:
        def ci = matrix.findCiInMatrix('ZONE')

        then:
        ci == 123.45d
    }

    
    def "should return null if zone not found"() {
        given:
        def matrix = Spy(CIDataMatrix)
        matrix.get(_, _) >> { throw new IllegalArgumentException("Not found") }

        when:
        def ci = matrix.findCiInMatrix('UNKNOWN_ZONE')

        then:
        ci == null
    }
    
}


class CIValueComputerTest extends Specification {

    def "should retrieve real-time carbon intensity when API key is set and request succeeds"() {
        given:
        def emApiKey = "dummy_api_key"
        def location = "US"
        def ciData = Mock(CIDataMatrix)
        def computer = Spy(CIValueComputer, constructorArgs: [emApiKey, location, ciData])

        computer.getRealtimeCI() >> 123.45

        when:
        def ciClosure = computer.computeCI()
        def ciValue = ciClosure()

        then:
        ciValue == 123.45
    }
    
    def "should fallback to CIDataMatrix if API key is missing"() {
        given:
        def location = "DE"
        def ciData = Mock(CIDataMatrix)
        def computer = new CIValueComputer(null, location, ciData)

        ciData.findCiInMatrix(location) >> 50.0

        when:
        def ciValue = computer.computeCI()

        then:
        ciValue == 50.0
    }
    
    def "should fallback to GLOBAL if no location-specific data is found"() {
        given:
        def location = "UNKNOWN"
        def ciData = Mock(CIDataMatrix)
        def computer = new CIValueComputer(null, location, ciData)

        ciData.findCiInMatrix(location) >> null
        ciData.findCiInMatrix('GLOBAL') >> 80.0

        when:
        def ciValue = computer.computeCI()

        then:
        ciValue == 80.0
    }
    
    def "should return null if real-time retrieval and fallback both fail"() {
        given:
        def location = "UNKNOWN"
        def ciData = Mock(CIDataMatrix)
        def computer = new CIValueComputer(null, location, ciData)

        ciData.findCiInMatrix(location) >> null
        ciData.findCiInMatrix('GLOBAL') >> null

        when:
        def ciValue = computer.computeCI()

        then:
        ciValue == null
    }
}
