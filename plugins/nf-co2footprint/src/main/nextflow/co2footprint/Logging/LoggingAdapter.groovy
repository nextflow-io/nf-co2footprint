package nextflow.co2footprint.Logging

import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.core.Appender

import nextflow.Session

import java.util.function.Supplier

/**
 * An adaptor class for modifying logging
 */
@Slf4j
class LoggingAdapter {
    Session session
    LoggerContext loggerContext

    LoggingAdapter(
            Session session,
            LoggerContext loggerContext= LoggerFactory.getILoggerFactory() as LoggerContext
    ) {
        this.session = session
        this.loggerContext = loggerContext
    }

    /**
     * Adds a DeduplicateMarkerFilter to filter out all Markers.unique markers
     */
    void addUniqueMarkerFilter() {
        final TurboFilter deduplicateMarkerFilter = new DeduplicateMarkerFilter([Markers.unique])   // Define DeduplicateMarkerFilter
        deduplicateMarkerFilter.start()

        loggerContext.addTurboFilter(deduplicateMarkerFilter)                                       // Add filter to context
    }

    /**
     * Change the logback pattern of the console output
     *
     * @param pattern New pattern, the default only differs in the colored level (highlight)
     * @param scope Scope of the changes, the default only affects this plugin
     */
    void changePatternConsoleAppender(
            String pattern='%d{HH:mm:ss} %customHighlight(%-5level - %msg)',    // Changing colors doesn't combine well with Nextflow
            String scope='nextflow.co2footprint'
    ) {
        // Define layout
        PatternLayout layout = new PatternLayout()
        layout.setContext(loggerContext)
        layout.getInstanceConverterMap().put('customHighlight', { -> new CustomHighlightConverter() } as Supplier) // CustomHighlightConverter.getName()) doesn't work
        layout.setPattern(pattern)
        layout.start()

        // Define logger and add appender
        Logger co2FootprintLogger = loggerContext.getLogger(scope)

        // Modify console output
        for (Appender appender : co2FootprintLogger.iteratorForAppenders()) {

            // CaptureAppender with chained ANSI Logger
            if (appender.getClass().getName() == 'nextflow.util.LoggerHelper$CaptureAppender') {
                log.trace("Modifying ${appender.getName()} (${appender.getClass().getName()})\"")
                appender.stop()

                // Replacing old logger
                CustomCaptureAppender customCaptureAppender = new CustomCaptureAppender(session, layout)
                for (Filter filter : appender.getCopyOfAttachedFiltersList()) {
                    customCaptureAppender.addFilter(filter)
                }
                appender.start()
                customCaptureAppender.start()
                co2FootprintLogger.addAppender(customCaptureAppender)
            }

            // Console appender (the easy case)
            else if (appender instanceof ConsoleAppender) {
                log.trace("Modifying ${appender.getName()} (${appender.getClass().getName()})\"")
                appender.stop()

                // Changing encoder to desired layout
                Encoder encoder = appender.getEncoder() as LayoutWrappingEncoder
                encoder.stop()
                encoder.setLayout(layout)
                encoder.start()

                appender.setEncoder(encoder)
                appender.start()
            }
        }
    }
}
