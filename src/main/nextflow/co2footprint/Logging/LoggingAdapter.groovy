package nextflow.co2footprint.Logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.encoder.Encoder
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import ch.qos.logback.core.filter.Filter
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.Session
import org.slf4j.LoggerFactory

import java.util.function.Supplier

/**
 * An adaptor class for modifying logging
 */
@Slf4j
class LoggingAdapter {
    Session session
    LoggerContext loggerContext

    LoggingAdapter(
            LoggerContext loggerContext=LoggerFactory.getILoggerFactory() as LoggerContext,
            Session session=Global.session as Session
    ) {
        this.session = session
        this.loggerContext = loggerContext
    }

    /**
     * Adds the ROOT appenders to the current scope
     *
     * @param scope Name of the scope, defaults to 'nextflow.co2footprint' Logger
     */
    void addRootAppendersToLogger(
            Logger logger=loggerContext.getLogger('nextflow.co2footprint')
    ) {
        // Ensure appenders are present
        Iterator<Appender> appenders = logger.iteratorForAppenders()
        if (appenders.size() == 0) {
            Iterator<Appender<ILoggingEvent>> rootAppenders = loggerContext.getLogger('ROOT').iteratorForAppenders()
            for (Appender rootAppender : rootAppenders) {
                logger.addAppender(rootAppender)
            }
            logger.setAdditive(false)
        }
    }

    /**
     * Defines a layout
     *
     * @param pattern Patter of the PattenLayout
     * @return A configured layout
     */
    PatternLayout defineLayout(String pattern) {
        // Define layout
        PatternLayout layout = new CustomPatternLayout()
        layout.setContext(loggerContext)
        layout.setPattern(pattern)

        try {
            log.trace("Trying with Supplier in ConverterMap.")
            layout.getInstanceConverterMap().put('customHighlight', { -> new CustomHighlightConverter() } as Supplier)
            layout.start()
        }
        // For backwards compatibility to logback v1.4.X
        catch (ClassCastException ignore) {
            log.debug("Logback version < 1.5. Fallback to Logback's standard highlighting.")
            layout.stop()
            layout.setPattern('%level - [nf-co2footprint] %msg')
            layout.start()
        }

        return layout
    }

    /**
     * Change the logback pattern of the console output
     *
     * @param pattern New pattern, the default only differs in the colored level (highlight)
     * @param scope Scope of the changes, the default only affects this plugin, if an extra logger is given at this level
     */
    void changePatternConsoleAppender(
            String pattern='%customHighlight(%level - [nf-co2footprint] %msg)',
            String scope='nextflow.co2footprint'
    ) {
        // Logback implementation
        PatternLayout layout = defineLayout(pattern)

        // Define logger and add appender
        Logger co2FootprintLogger = loggerContext.getLogger(scope)

        // Ensure appenders are given
        addRootAppendersToLogger(co2FootprintLogger)

        // Modify console output
        Iterator<Appender<ILoggingEvent>> appenders = co2FootprintLogger.iteratorForAppenders()
        for (Appender appender : appenders) {

            // CaptureAppender with chained ANSI Logger
            if (appender.getClass().getName() == 'nextflow.util.LoggerHelper$CaptureAppender') {
                log.trace("Modifying ${appender.getName()} (${appender.getClass().getName()})")

                // Remove old logger
                appender.stop()
                co2FootprintLogger.detachAppender(appender)
                appender.start()

                // Replace with custom logger
                CustomCaptureAppender customCaptureAppender = new CustomCaptureAppender(session, layout)
                for (Filter filter : appender.getCopyOfAttachedFiltersList()) {
                    customCaptureAppender.addFilter(filter)
                }

                customCaptureAppender.start()
                co2FootprintLogger.addAppender(customCaptureAppender)
            }

            // Console appender (the easy case)
            else if (appender instanceof ConsoleAppender) {
                log.trace("Modifying ${appender.getName()} (${appender.getClass().getName()})")
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

    /**
     * Adds a DeduplicateMarkerFilter to filter out all Markers.unique markers
     */
    void addUniqueMarkerFilter() {
        final TurboFilter deduplicateMarkerFilter = new DeduplicateMarkerFilter(
                [Markers.unique, Markers.silentUnique]
        )   // Define DeduplicateMarkerFilter
        deduplicateMarkerFilter.start()
        loggerContext.addTurboFilter(deduplicateMarkerFilter)                                       // Add filter to context
    }
}
