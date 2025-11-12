package nextflow.co2footprint.DataContainers

import nextflow.co2footprint.TestHelpers.LogChecker
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

@Stepwise
class TDPDataMatrixTest extends Specification {
    private final Path tempPath = Files.createTempDirectory('tmpdir')
    final Matrix df = new TDPDataMatrix(
            [
                    [13, 1, 1],
                    [13, 2, 2],
                    [42, 10**3, 2*10**3],
                    [100, 4, 8],
            ],
            ['tdp (W)', 'cores', 'threads'] as LinkedHashSet,
            ['Indel® i3-Fantasy', 'Ambere ultraEfficient Processor', 'AMT YPS-x42', 'default'] as LinkedHashSet,
    )

    @Shared
    LogChecker logChecker

    def setup() {
        logChecker = new LogChecker(TDPDataMatrix)
    }

    def cleanup() {
        logChecker.clear()
    }

    def 'Should get a valid DataMatrix Extension' () {
        setup:
        Path filePath = tempPath.resolve('tdpDM.csv')
        df.saveCsv(filePath)

        when:
        Matrix tdpDM = TDPDataMatrix.fromCsv(filePath, ',', 0, 0)

        then:
        tdpDM == df
    }


    def 'Should get a valid DataMatrix Extension' () {
        setup:
        String modelName = 'AMT YPS-x42'
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
        df.getTDP() == 13.0
        df.getCores() == 1
        df.getThreads() == 1
        df.getTDP(null, 'default') == 100
        df2.getTDP() == 20.0
        df2.getCores() == 2
        df2.getThreads() == 4
    }

    def 'Should return first name of cpu model' () {
        when:
        Object firstName = df.getFirstName()

        then:
        // non-ASCII symbols are replaced
        firstName == 'Indel i3-Fantasy'
    }

    def 'Should return correct TPD per Core/Thread' () {
        setup:
        Matrix df2 = new TDPDataMatrix(
                df.data, df.getOrderedColumnKeys(), df.getOrderedRowKeys(),
                'default', 20, 2, 4
        )

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
        logChecker.checkLogs(1,  [
                'Could not find CPU model "Non-existent" in given TDP data table. ' +
                'Using default CPU power draw value (100.0 W).\n' +
                '\t🔖 To fix this warning, please refer to https://nextflow-io.github.io/nf-co2footprint/usage/faq/#cpu-model.'
        ])
    }

    def 'Should match the default model names correctly' () {
        expect:
        // match default
        df.matchModel('default').getData() == [[100, 4, 8]]

        // match standard model
        df.matchModel('AMT YPS-x42').getData() == [[42, 10**3, 2 * 10**3]]
    }

    def 'Should match the non-ASCII model names correctly' () {
        expect:
        // match against model with ASCII characters
        df.matchModel('Indel® i3-Fantasy™').getData() == [[13, 1, 1]]

        // match against missing ASCII characters
        df.matchModel('Indel i3-Fantasy').getData() == [[13, 1, 1]]

        // match against replaces ASCII characters
        df.matchModel('Indel(R) i3-Fantasy(TM)').getData() == [[13, 1, 1]]

        // match against mix of non-ASCII and replaced ASCII symbols
        df.matchModel('Indel® i3-Fantasy(TM)').getData() == [[13, 1, 1]]
    }

    def 'Should match the @ Statement model names correctly' () {
        expect:
        // match against @ statement
        df.matchModel('Indel® i3-Fantasy(TM) @ 10Trillion GW').getData() == [[13, 1, 1]]

        // match against multiple @ statements
        df.matchModel('Indel® i3-Fantasy(TM) @ 10Trillion GW @ 0.00001MHz').getData() == [[13, 1, 1]]
    }

    def 'Should match the processor/CPU/X-core(s) containing model names correctly' () {
        expect:
        // match against extra 'Processor'
        df.matchModel('Indel® Processor i3-Fantasy(TM)').getData() == [[13, 1, 1]]

        // match against extra 'Processor & 'Processors'
        df.matchModel('Indel® Processor i3-Fantasy(TM) Processors').getData() == [[13, 1, 1]]

        // match against extra 'CPUs'
        df.matchModel('Indel® CPUs i3-Fantasy(TM)').getData() == [[13, 1, 1]]

        // match against missing 'processor'
        df.matchModel('ambere ultraefficient').getData() == [[13, 2, 2]]

        // match against ' 32-core'
        df.matchModel('ambere ultraefficient 32-core').getData() == [[13, 2, 2]]

        // match against '64-cores'
        df.matchModel('ambere 64-cores ultraefficient').getData() == [[13, 2, 2]]
    }

    def 'Should match the differing case model names correctly' () {
        expect:
        // match against lower case
        df.matchModel('ambere ultraefficient').getData() == [[13, 2, 2]]

        // match against upper case
        df.matchModel('AMBERE ULTRAEFFICIENT').getData() == [[13, 2, 2]]

        // match against arbitrary case
        df.matchModel('amBErE ulTrAEffIciEnt').getData() == [[13, 2, 2]]
    }

    def 'Should not match non-existent model names and issue warnings' () {
        expect:
        // match against non existent model
        df.matchModel('Non-existent2').getData() == [[100, 4, 8]]
        logChecker.checkLogs(null, [
                'Could not find CPU model "Non-existent2" in given TDP data table. ' +
                'Using default CPU power draw value (100.0 W).\n' +
                '\t🔖 To fix this warning, please refer to https://nextflow-io.github.io/nf-co2footprint/usage/faq/#cpu-model.'
        ])
        logChecker.clear()

        // match against unaccounted variance of model
        df.matchModel('Indel® i3-Fantasy(TM) 10Trillion GW').getData() == [[100, 4, 8]]
        logChecker.checkLogs(null, [
                'Could not find CPU model "Indel® i3-Fantasy(TM) 10Trillion GW" in given TDP data table. ' +
                'Using default CPU power draw value (100.0 W).\n' +
                '\t🔖 To fix this warning, please refer to https://nextflow-io.github.io/nf-co2footprint/usage/faq/#cpu-model.'
        ])
    }

    @Unroll
    def "Should use fallback row '#fallbackModel' when CPU model is unknown"() {
        given:
        def data = [
            [50, 2, 4],   // default local
            [60, 4, 8],   // default compute cluster
            [100, 8, 16], // default
            [90, 4, 16]  // some other model
        ]
        def columns = ['tdp (W)', 'cores', 'threads'] as LinkedHashSet
        def rows = ['default local', 'default compute cluster', 'default', 'other'] as LinkedHashSet

        and:
        TDPDataMatrix tdpMatrix = new TDPDataMatrix(data, columns, rows, fallbackModel, null, null, null)

        when:
        TDPDataMatrix result = tdpMatrix.matchModel('NonExistentCPU')

        then:
        result.getOrderedRowKeys() == [fallbackModel] as LinkedHashSet
        result.getTDP() == expectedTDP

        where:
        fallbackModel             || expectedTDP
        'default local'           || 50
        'default compute cluster' || 60
        'default'                 || 100
    }

    def 'Should update the data correctly' () {
        setup:
        Matrix df2 = new TDPDataMatrix(
                df.data, df.getOrderedColumnKeys(), df.getOrderedRowKeys()
        )

        Matrix df3 = new TDPDataMatrix(
                [
                        [13, 13, 'red'],
                        [12, 12, 'redder'],
                ],
                ['tdp (W)', 'cores', 'color'] as LinkedHashSet,
                ['Indel i3-Fantasy', 'Indel i5-Fantasy'] as LinkedHashSet
        )


        when:
        df2.update(df3)

        then:
        df2 == new TDPDataMatrix(
                [
                        [13, 13, null],
                        [13, 2, 2],
                        [42, 10**3, 2*10**3],
                        [100, 4, 8],
                        [12, 12, null],
                ],
                ['tdp (W)', 'cores', 'threads'] as LinkedHashSet,
                ['Indel® i3-Fantasy', 'Ambere ultraEfficient Processor', 'AMT YPS-x42', 'default', 'Indel i5-Fantasy'] as LinkedHashSet,
        )
    }
}
