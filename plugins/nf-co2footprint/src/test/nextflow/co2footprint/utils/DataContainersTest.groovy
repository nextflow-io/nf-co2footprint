package nextflow.co2footprint.utils

import spock.lang.Specification
import spock.lang.Stepwise


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.core.read.ListAppender
import ch.qos.logback.classic.spi.ILoggingEvent

import java.nio.file.Path
import java.nio.file.Files


class BiMapTest extends  Specification {
    def 'Should create an empty bidirectional Map'() {
        setup:
        BiMap<String, Integer> bm = [:]

        expect:
        bm.size() == 0
        bm.valueSet() == [] as Set
        bm.keySet() == [] as Set
        bm == bm.clear()
    }

    def 'Should access a bidirectional Map'() {
        setup:
        BiMap<String, Integer> bm = new BiMap(['E1': 1, 'E2': 2])

        expect:
        bm.getValue('E1') == 1
        bm.getKey(1) == 'E1'
        bm.getValue('E2') == 2
        bm.getKey(2) == 'E2'

        bm.containsKey('E2')
        bm.containsValue(1)
    }

    def 'Should put values in bidirectional Map'() {
        setup:
        BiMap<String, Integer> bm = new BiMap()

        when:
        bm.put('E1', 1)

        then:
        bm.getValue('E1') == 1
        bm.getKey(1) == 'E1'
    }

    def 'Should remove values from bidirectional Map'() {
        setup:
        BiMap<String, Integer> bm = new BiMap(['E1': 1, 'E2': 2])

        when:
        Integer value = bm.removeByKey('E1')
        String key = bm.removeByValue(2)

        then:
        bm == new BiMap([:])
        value == 1
        key == 'E2'
    }

    def 'Should sort the bidirectional Map entries' () {
        setup:
        BiMap<String, Integer> bm = new BiMap(['E1': 1, 'E2': 0])

        expect:
        bm.sortByValues() == ['E2': 0, 'E1': 1] as BiMap
        bm.sortByKeys() == bm
    }

    def 'Should filter the Map correctly' () {
        setup:
        BiMap<String, Integer> bm = new BiMap(['E1': 1, 'E2': 2, '3': -1, '4': 0])

        expect:
        bm.filterValues { it >= 1 } == [1, 2]
        bm.filterKeys { str -> str.matches('\\d')} == ['3', '4']
    }
}


class DataMatrixTest extends  Specification {
    private final Path tempPath = Files.createTempDirectory('tmpdir')
    final Matrix df = new DataMatrix(
        [
            [1, 2, 'E'],
            [4, 'S', 6],
        ],
        ['C1', 'C2', 'C3'] as LinkedHashSet,
        ['R1', 'R2'] as LinkedHashSet,
    )

    def 'Should create empty DataMatrix' () {
        setup:
        Matrix df = new DataMatrix()

        expect:
        df.getData() == []
        df.getColumnIndex().keySet() == [] as Set
        df.getColumnIndex().valueSet() == [] as Set
        df.getRowIndex().keySet() == [] as Set
        df.getRowIndex().valueSet() == [] as Set
    }

    def 'Should create a DataMatrix with specified entries' () {
        setup:
        Matrix df = new DataMatrix(
                [
                        [1, 2, '3'],
                        [4, '5', 6],
                ],
                ['C1', 'C2', 'C3'] as LinkedHashSet,
                ['R1', 'R2'] as LinkedHashSet,
        )

        expect:
        df.getData() == [[1,2,'3'], [4, '5', 6]]
        df.getColumnIndex().keySet() == ['C1', 'C2', 'C3'] as TreeSet
        df.getColumnIndex().valueSet() == [0, 1, 2] as Set
        df.getRowIndex().keySet() == ['R1', 'R2'] as TreeSet
        df.getRowIndex().valueSet() == [0, 1] as Set
    }

    def 'Should select the right sections of the DataMatrix' () {
        when:
        Matrix selection1 = df.select(['R1'] as LinkedHashSet, ['C2', 'C3'] as LinkedHashSet)
        Matrix selection2 = df.select(['R2'] as LinkedHashSet, ['C1', 'C3'] as LinkedHashSet)

        then:
        df == df
        selection1.getData() == [[2, 'E']]
        selection2.getData() == [[4, 6]]
    }

    def 'Should get the right entry in the DataMatrix' () {
        when:
        def selection1 = df.get('R1', 'C3')
        def selection2 = df.get('R2', 'C1')

        then:
        df == df
        selection1 == 'E'
        selection2 == 4
    }

    def 'Should get the right enty in the DataMatrix' () {
        setup:
        Matrix df2 = new DataMatrix(
                [
                        [1, 2, '3'],
                        [4, '6', 7],
                ],
                ['C1', 'C2', 'C3'] as LinkedHashSet,
                ['R1', 'R2'] as LinkedHashSet,
        )

        when:
        df2.set('E', 'R1', 'C3')
        df2.set('S', 'R2', 'C2')
        df2.set(6, 'R2', 'C3')

        then:
        df == df2
    }

    def 'Should save a csv table of DataMatrix' () {
        setup:
        Path filePath = tempPath.resolve('dm.csv')
        println(filePath)

        when:
        df.saveCsv(filePath, ',')
        Map<String, Object> parsedCsv = DataMatrix.readCsv(filePath, ',', 0, 0)
        DataMatrix df2 =  new DataMatrix(
                parsedCsv.data,
                parsedCsv.columnIndex,
                parsedCsv.rowIndex
        )

        then:
        Files.isRegularFile(filePath)
        df == df2
    }
}
