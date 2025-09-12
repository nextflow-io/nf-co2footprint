package nextflow.co2footprint.Logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import nextflow.Session
import nextflow.trace.AnsiLogObserver
import nextflow.util.LoggerHelper

/**
 * Mirrors the functionality of nextflow.util.LoggerHelper$CaptureAppender with added customizability
 */
class CustomCaptureAppender extends AppenderBase<ILoggingEvent> {
    final Session session
    final PatternLayout layout

    CustomCaptureAppender(Session session, PatternLayout layout) {
        super()
        this.session = session
        this.layout = layout
    }

    /**
     * Start the Appender
     */
    @Override
    void start() {
        super.start()
        layout.start()
    }

    /**
     * Appends the logging event to the right renderer
     *
     * @param event Logged event
     */
    @Override
    protected void append(ILoggingEvent event) {
        try {
            // Format message with Layout
            final String message = layout.doLayout(event)

            // Render the results
            final AnsiLogObserver renderer = session?.ansiLogObserver
            if( !renderer || !renderer.started || renderer.stopped ) {
                System.out.println(message)
            }
            else if( event.getMarkerList()?.get(0) == LoggerHelper.STICKY ) {
                renderer.appendSticky(message)
            }
            else {
                // Choose appropriate level
                switch (event.level.levelInt) {
                    case Level.ERROR.levelInt -> renderer.appendError(message)
                    case Level.WARN.levelInt -> renderer.appendWarning(message)
                    default -> renderer.appendInfo(message)
                }
            }
        }
        catch (Throwable throwable) {
            throwable.printStackTrace()
        }
    }
}
