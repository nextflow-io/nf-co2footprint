package nextflow.co2footprint.DataContainers

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CIDataMatrixTest extends Specification {

    def "should create CIDataMatrix from given data"() {
        given:
        List<List<Integer>> data = [[1, 2], [3, 4]]
        LinkedHashSet<String> columns = ['col1', 'col2'] as LinkedHashSet
        LinkedHashSet<String> rows = ['row1', 'row2'] as LinkedHashSet

        when:
        CIDataMatrix matrix = new CIDataMatrix(data, columns, rows)

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
        CIDataMatrix matrix = CIDataMatrix.fromCsv(tempFile)

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
        List<List<Double>> data = [[123.45]]
        CIDataMatrix matrix = Spy(CIDataMatrix, constructorArgs: [data, ['Carbon intensity gCO₂eq/kWh (Life cycle)'] as LinkedHashSet, ['ZONE'] as LinkedHashSet])

        matrix.get('ZONE', 'Carbon intensity gCO₂eq/kWh (Life cycle)') >> "123.45"

        when:
        CIMatch ci = matrix.findCiInMatrix('ZONE')

        then:
        ci.value == 123.45d
        ci.zone == 'ZONE'
    }
}
