package nextflow.co2footprint.utils

import ch.qos.logback.classic.spi.ILoggingEvent
import spock.lang.Specification

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import ch.qos.logback.core.read.ListAppender

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
        Matrix df2 = DataMatrix.loadCsv(filePath, ',', 0, 0)

        then:
        Files.isRegularFile(filePath)
        df == df2
    }
}


class TDPDataMatrixTest extends Specification {
    private final Path tempPath = Files.createTempDirectory('tmpdir')
    final Matrix df = new TDPDataMatrix(
            [
                    [13, 1, 1],
                    [42, 10**3, 2*10**3],
                    [100, 4, 8],
            ],
            ['tdp (W)', 'cores', 'threads'] as LinkedHashSet,
            ['Intel i3-Fantasy', 'AMD YPS-x42', 'default'] as LinkedHashSet,
    )
    private Logger logger = (Logger) LoggerFactory.getLogger(TDPDataMatrix)
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>()

    def 'Should get a valid DataMatrix Extension' () {
        setup:
        Path filePath = tempPath.resolve('tdpDM.csv')
        df.saveCsv(filePath)

        when:
        Matrix tdpDM = TDPDataMatrix.loadCsv(filePath, ',', 0, 0)

        then:
        tdpDM == df
    }


    def 'Should get a valid DataMatrix Extension' () {
        setup:
        String modelName = 'AMD YPS-x42'
        Matrix model = new TDPDataMatrix(
            [
                [42, 10**3, 2*10**3],
            ],
            ['tdp (W)', 'cores', 'threads'] as LinkedHashSet,
            [modelName] as LinkedHashSet,
        )

        when:
        DataMatrix modelRes = df.matchModel(modelName)

        then:
        modelRes == model
        modelRes.get(0, "tdp (W)", true) == 42
    }

    def 'Should return correct TPD, Core & Thread values' () {
        setup:
        Matrix df2 = new TDPDataMatrix(
                df.data, df.getOrderedColumnKeys(), df.getOrderedRowKeys(),
                'default', 20, 2, 4
        )

        expect:
        df.getTDP() == 13
        df.getCores() == 1
        df.getThreads() == 1
        df.getTDP(null, 'default') == 100
        df2.getTDP() == 20
        df2.getCores() == 2
        df2.getThreads() == 4
    }

    def 'Should return correct TPD per Core/Thread' () {
        setup:
        Matrix df2 = new TDPDataMatrix(
                df.data, df.getOrderedColumnKeys(), df.getOrderedRowKeys(),
                'default', 20, 2, 4
        )
        listAppender.start()
        logger.addAppender(listAppender)

        when:
        Double dfTDPPerCore = df.matchModel('Non-existent').getCoreTDP()
        Double df2TDPPerCore = df2.getCoreTDP()
        Double dfTDPPerThread = df.matchModel('Non-existent').getThreadTDP()
        Double df2TDPPerThread = df2.getThreadTDP()

        then:
        dfTDPPerCore == 25.0
        df2TDPPerCore == 10.0
        dfTDPPerThread == 12.5
        df2TDPPerThread == 5.0
        listAppender.list[0].toString() ==  '[WARN] Could not find CPU model "Non-existent" in given TDP data table. ' +
                                            'Using default CPU power draw value (100 W).'
        listAppender.list[1].toString() ==  '[WARN] Could not find CPU model "Non-existent" in given TDP data table. ' +
                                            'Using default CPU power draw value (100 W).'

        cleanup:
        listAppender.stop()
    }

    def 'Should return first name of cpu model' () {
        when:
        Object firstName = df.getFirstName()

        then:
        firstName == 'Intel i3-Fantasy'
    }

    def 'Should match the model names correctly' () {
        setup:
        listAppender.start()
        logger.addAppender(listAppender)

        expect:
        // Standard calls
        df.matchModel('Intel i3-Fantasy').getData() == [[13, 1, 1]]
        df.matchModel('AMD YPS-x42').getData() == [[42, 10**3, 2*10**3]]
        df.matchModel('default').getData() == [[100, 4, 8]]

        // Handling different string possibilities
        df.matchModel('Intel® i3-Fantasy™').getData() == [[13, 1, 1]]
        df.matchModel('Intel(R) i3-Fantasy(TM)').getData() == [[13, 1, 1]]
        df.matchModel('Intel® i3-Fantasy(TM)').getData() == [[13, 1, 1]]
        df.matchModel('Intel® i3-Fantasy(TM) @ 10Trillion GW').getData() == [[13, 1, 1]]
        df.matchModel('Intel® i3-Fantasy(TM) @ 10Trillion GW @ 0.00001MHz').getData() == [[13, 1, 1]]

        // Getting default when non-existent
        df.matchModel('Non-existent2').getData() == [[100, 4, 8]]
        listAppender.list[0].toString() ==  '[WARN] Could not find CPU model "Non-existent2" in given TDP data table. ' +
                'Using default CPU power draw value (100 W).'
        df.matchModel('Intel® i3-Fantasy(TM) 10Trillion GW, 0.00001MHz').getData() == [[100, 4, 8]]
        listAppender.list[1].toString() ==  '[WARN] Could not find CPU model "Intel® i3-Fantasy(TM) 10Trillion GW, 0.00001MHz" in given TDP data table. ' +
                'Using default CPU power draw value (100 W).'

        cleanup:
        listAppender.stop()
    }
}
