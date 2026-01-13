package nextflow.co2footprint.TestHelpers

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import nextflow.co2footprint.Logging.LoggingAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A class that facilitates the capturing of logs within tests.
 *  !!! WARNING
 *      Because the logger context is outside of the individual test, parallelization can lead to some wild logging!
 */
class LogChecker {
    Logger logger
    synchronized ListAppender<ILoggingEvent> listAppender = new ListAppender<>()

    /**
     * Setup a log checker to capture all logs above a defined level.
     *
     * @param loggingScope Logger context of current test. Can be a class, but can also be 'ROOT'
     * @param level Lowest level of logging that is captures, default: INFO
     */
    LogChecker(Object loggingScope='ROOT', Level level=Level.INFO) {
        LoggerContext loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        LoggingAdapter loggingAdapter = new LoggingAdapter(loggerContext)
        loggingAdapter.addUniqueMarkerFilter()
        loggingAdapter.changePatternConsoleAppender()

        logger = loggerContext.getLogger(loggingScope)
        logger.setLevel(level)
        listAppender.start()
        logger.addAppender(listAppender)
    }

    /**
     * Stop the capturing of logs within this instance.
     */
    void stop() {
        logger.detachAndStopAllAppenders()
        listAppender.stop()
    }

    /**
     * Clear the logged list.
     */
    void clear() {
        listAppender.list.clear()
    }

    /**
     * Check the logs for their length and the presence of messages.
     *
     * @param numLogs Number of logges messages
     * @param logMessages A list of messages that have to be logged
     */
    void checkLogs(Integer numLogs=null, List<String> logMessages=null) {
        if (numLogs != null) {
            assert listAppender.list.size() == numLogs
        }
        if(logMessages != null) {
            logMessages.each { String logMessage ->
                assert listAppender.list.any { ILoggingEvent event ->
                    String message = event.getFormattedMessage()
                    message == logMessage
                },
                "${logMessage} not in ${listAppender.list}"
            }
        }
    }
}
