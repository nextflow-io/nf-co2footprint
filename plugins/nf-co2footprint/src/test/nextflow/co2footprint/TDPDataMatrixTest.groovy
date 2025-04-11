package nextflow.co2footprint

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.read.ListAppender

import nextflow.co2footprint.utils.DataMatrix
import nextflow.co2footprint.utils.DeduplicateMarkerFilter
import nextflow.co2footprint.utils.Markers
import nextflow.co2footprint.utils.Matrix

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

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
            ['Intel® i3-Fantasy', 'Ampere ultraEfficient Processor', 'AMD YPS-x42', 'default'] as LinkedHashSet,
    )

    static LoggerContext lc = LoggerFactory.getILoggerFactory() as LoggerContext

    @Shared
    Logger logger
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>()

    // Setup method for the class
    def setupSpec() {
        TurboFilter dmf = new DeduplicateMarkerFilter([Markers.unique])
        dmf.start()
        lc.addTurboFilter(dmf)
        logger = lc.getLogger(TDPDataMatrix)
    }

    def setup() {
        listAppender.start()
        logger.addAppender(listAppender)
    }

    def cleanup() {
        listAppender.list.clear()
        logger.detachAndStopAllAppenders()
        listAppender.stop()
    }

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
        firstName == 'Intel i3-Fantasy'
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
        listAppender.list[0] as String ==  '[WARN] Could not find CPU model "Non-existent" in given TDP data table. ' +
                'Using default CPU power draw value (100.0 W).'
        // Second instance should be filtered
        listAppender.list.size() == 1

    }

    def 'Should match the default model names correctly' () {
        expect:
        // match default
        df.matchModel('default').getData() == [[100, 4, 8]]

        // match standard model
        df.matchModel('AMD YPS-x42').getData() == [[42, 10**3, 2 * 10**3]]
    }

    def 'Should match the non-ASCII model names correctly' () {
        expect:
        // match against model with ASCII characters
        df.matchModel('Intel® i3-Fantasy™').getData() == [[13, 1, 1]]

        // match against missing ASCII characters
        df.matchModel('Intel i3-Fantasy').getData() == [[13, 1, 1]]

        // match against replaces ASCII characters
        df.matchModel('Intel(R) i3-Fantasy(TM)').getData() == [[13, 1, 1]]

        // match against mix of non-ASCII and replaced ASCII symbols
        df.matchModel('Intel® i3-Fantasy(TM)').getData() == [[13, 1, 1]]
    }

    def 'Should match the @ Statement model names correctly' () {
        expect:
        // match against @ statement
        df.matchModel('Intel® i3-Fantasy(TM) @ 10Trillion GW').getData() == [[13, 1, 1]]

        // match against multiple @ statements
        df.matchModel('Intel® i3-Fantasy(TM) @ 10Trillion GW @ 0.00001MHz').getData() == [[13, 1, 1]]
    }

    def 'Should match the processor/CPU containing model names correctly' () {
        expect:
        // match against extra 'Processor'
        df.matchModel('Intel® Processor i3-Fantasy(TM)').getData() == [[13, 1, 1]]

        // match against extra 'Processor & 'Processors'
        df.matchModel('Intel® Processor i3-Fantasy(TM) Processors').getData() == [[13, 1, 1]]

        // match against extra 'CPUs'
        df.matchModel('Intel® CPUs i3-Fantasy(TM)').getData() == [[13, 1, 1]]

        // match against missing 'processor'
        df.matchModel('ampere ultraefficient').getData() == [[13, 2, 2]]
    }

    def 'Should match the differing case model names correctly' () {
        expect:
        // match against lower case
        df.matchModel('ampere ultraefficient').getData() == [[13, 2, 2]]

        // match against upper case
        df.matchModel('AMPERE ULTRAEFFICIENT').getData() == [[13, 2, 2]]

        // match against arbitrary case
        df.matchModel('amPErE ulTrAEffIciEnt').getData() == [[13, 2, 2]]
    }

    def 'Should not match non-existent model names and issue warnings' () {
        expect:
        // match against non existent model
        df.matchModel('Non-existent2').getData() == [[100, 4, 8]]
        listAppender.list[0] as String == '[WARN] Could not find CPU model "Non-existent2" in given TDP data table. ' +
                'Using default CPU power draw value (100.0 W).'

        // match against unaccounted variance of model
        df.matchModel('Intel® i3-Fantasy(TM) 10Trillion GW').getData() == [[100, 4, 8]]
        listAppender.list[1] as String == '[WARN] Could not find CPU model "Intel® i3-Fantasy(TM) 10Trillion GW" in given TDP data table. ' +
                'Using default CPU power draw value (100.0 W).'
    }
}
