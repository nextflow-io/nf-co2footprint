package nextflow.co2footprint.Logging

import nextflow.co2footprint.TestHelpers.LogChecker
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise


/**
 * Test the Logging  (especially the definition of a Duplication TurboFilter in logback-test.xml)
 */
@Stepwise
class LoggingTest extends Specification {
    @Shared
    LogChecker logChecker

    def setup() {
        logChecker = new LogChecker(LoggerTestClass)
    }

    def 'Should return warning only once' () {
        when:
        LoggerTestClass.warn()
        LoggerTestClass.warn()
        LoggerTestClass.warn()

        then:
        // Additional warnings are blocked
        logChecker.checkLogs(1)
    }

    def 'Should block further warnings' () {when:
        LoggerTestClass.warn()

        then:
        // Warnings are still blocked
        logChecker.checkLogs(0)

    }

    def 'Should only retain INFO level and above and ignore warnings further' () {
        when:
        LoggerTestClass.main()

        then:
        // Warnings are still blocked & Messages with level below Info (Debug & Trace) are ignored
        logChecker.checkLogs(2, ['Info message', 'Error message'])
    }

    def 'Should deduplicate based on dedupKey and allow custom trace message' () {
        setup:
        String dedupKey = "memory_is_null"
        String warnMessage = "Requested memory is null for task 123."

        when:
        // Log with dedupKey 
        logChecker.logger.warn(Markers.unique, warnMessage, dedupKey)
        logChecker.logger.warn(Markers.unique, warnMessage, dedupKey)
        logChecker.logger.warn(Markers.unique, warnMessage, dedupKey)

        then:
        // Only the first warning should be logged
        logChecker.checkLogs(1, ["🔁 ${warnMessage}"])
    }
}

