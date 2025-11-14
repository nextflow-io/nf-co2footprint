package nextflow.co2footprint.Logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.Layout
import ch.qos.logback.core.read.ListAppender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Test the Logging  (especially the definition of a Duplication TurboFilter in logback-test.xml)
 */
@Stepwise
class CustomHighlightConverterTest extends Specification {

    static LoggerContext lc = LoggerFactory.getILoggerFactory() as LoggerContext

    @Shared
    Logger logger
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>()

    // Setup method for the class
    def setupSpec() {
        TurboFilter dmf = new DeduplicateMarkerFilter([Markers.unique])
        dmf.start()
        lc.addTurboFilter(dmf)
        logger = lc.getLogger(LoggerTestClass)
    }

    // Setup method that executes once before each test
    def setup() {
        logger.setLevel(Level.DEBUG)
        listAppender.start()
        logger.addAppender(listAppender)
    }

    // Repeated cleanup method that executes once after each test
    def cleanup() {
        listAppender.list.clear()
        logger.detachAndStopAllAppenders()
        listAppender.stop()
    }

    def 'Should change coloring' () {
        setup:
        String pattern = '%customHighlight(%-5level - %msg)'
        LoggingAdapter loggingAdapter = new LoggingAdapter()

        // Define layout
        Layout layout = loggingAdapter.defineLayout(pattern)

        // Define Event
        ILoggingEvent event = new LoggingEvent(
                '', logger as ch.qos.logback.classic.Logger, Level.WARN, '1234', null, []
        )

        when:
        final String message = layout.doLayout(event)

        then:
        message == '\u001B[33mWARN  - 1234\u001B[0;39m'
    }
}
