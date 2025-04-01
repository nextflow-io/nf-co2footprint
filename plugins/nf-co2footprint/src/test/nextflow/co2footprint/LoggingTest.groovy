package nextflow.co2footprint

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import ch.qos.logback.classic.LoggerContext

import groovy.util.logging.Slf4j

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
        log.warn('Warn message')
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

@Stepwise
class LoggingTest extends Specification {

    LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory()

    Logger logger = LoggerFactory.getLogger(LoggerTestClass)
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>()

    def setup() {
        listAppender.start()
        logger.addAppender(listAppender)
    }

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
        listAppender.list.size() == 1
    }

    def 'Should block further warnings' () {
        when:
        LoggerTestClass.warn()

        then:
        listAppender.list.size() == 0

    }

    def 'Should only retain INFO level and above and ignore warnings further' () {
        when:
        LoggerTestClass.main()

        then:
        listAppender.list.size() == 2
        listAppender.list.collect({it as String}) as Set ==
                ['[INFO] Info', '[ERROR] Error'].collect({"${it} message" as String}) as Set

    }
}

