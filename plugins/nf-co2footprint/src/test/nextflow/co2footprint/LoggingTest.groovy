package nextflow.co2footprint

import ch.qos.logback.classic.turbo.TurboFilter
import nextflow.co2footprint.utils.DeduplicateMarkerFilter
import nextflow.co2footprint.utils.Markers

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender

import groovy.util.logging.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Slf4j
class LoggerTestClass {

    static trace() {
        log.trace('Trace message')
    }

    static debug() {
        log.debug('Debug message')
    }

    static info() {
        log.info('Info message')
    }

    static warn() {
        log.warn( Markers.unique, 'Warn message')
    }

    static error() {
        log.error('Error message')
    }

    static void main(String[] args) {
        trace()
        debug()
        info()
        warn()
        error()
    }
}


/**
 * Test the Logging  (especially the definition of a Duplication TurboFilter in logback-test.xml)
 */
@Stepwise
class LoggingTest extends Specification {

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
        listAppender.start()
        logger.addAppender(listAppender)
    }

    // Repeated cleanup method that executes once after each test
    def cleanup() {
        listAppender.list.clear()
        logger.detachAndStopAllAppenders()
        listAppender.stop()
    }

    def 'Should return warning only once' () {
        when:
        LoggerTestClass.warn()
        LoggerTestClass.warn()
        LoggerTestClass.warn()

        then:
        // Additional warnings are blocked
        listAppender.list.size() == 1
    }

    def 'Should block further warnings' () {
        when:
        LoggerTestClass.warn()

        then:
        // Warnings are still blocked
        listAppender.list.size() == 0

    }

    def 'Should only retain INFO level and above and ignore warnings further' () {
        when:
        LoggerTestClass.main()

        then:
        // Warnings are still blocked & Messages with level below Info (Debug & Trace) are ignored
        listAppender.list.size() == 3
        listAppender.list.collect({it as String}) as Set ==
                ['[DEBUG] Debug', '[INFO] Info', '[ERROR] Error'].collect({"${it} message" as String}) as Set

    }
}

