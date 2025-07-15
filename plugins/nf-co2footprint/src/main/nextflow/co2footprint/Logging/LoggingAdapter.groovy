package nextflow.co2footprint.Logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Layout
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

import nextflow.Global
import nextflow.Session

import java.lang.module.ModuleDescriptor.Version
import java.util.function.Supplier

/**
 * An adaptor class for modifying logging
 */
@Slf4j
class LoggingAdapter {
    Session session
    LoggerContext loggerContext

    LoggingAdapter(
            Session session=Global.session as Session,
            LoggerContext loggerContext=LoggerFactory.getILoggerFactory() as LoggerContext
    ) {
        this.session = session
        this.loggerContext = loggerContext
    }

    /**
     * Defines a layout
     *
     * @param pattern Patter of the PattenLayout
     * @return A configured layout
     */
    Layout defineLayout(String pattern) {
        Version implementationVersion = Version.parse(LoggerContext.package.implementationVersion)
        // Define layout
        PatternLayout layout = new PatternLayout()
        layout.setContext(loggerContext)
        layout.setPattern(pattern)
        if (implementationVersion >= Version.parse('1.5')) {
            layout.getInstanceConverterMap().put('customHighlight', { -> new CustomHighlightConverter() } as Supplier)
        }
        // For backwards compatibility to logback v1.4.X
        else {
            layout.getInstanceConverterMap().put('customHighlight', CustomHighlightConverter.getName())
        }
        layout.start()

        return layout
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
     * Change the logback pattern of the console output
     *
     * @param pattern New pattern, the default only differs in the colored level (highlight)
     * @param scope Scope of the changes, the default only affects this plugin, if an extra logger is given at this level
     */
    void changePatternConsoleAppender(
            String pattern='%customHighlight(%-5level - %msg)',
            String scope='nextflow.co2footprint'
    ) {
        // Logback implementation
        Layout layout = defineLayout(pattern)

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
                appender.stop()

                // Replacing old logger
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
}
