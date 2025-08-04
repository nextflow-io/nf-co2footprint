package nextflow.co2footprint.Logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import nextflow.Global
import nextflow.Session
import nextflow.trace.AnsiLogObserver
import nextflow.util.LoggerHelper

/**
 * Mirrors the functionality of nextflow.util.LoggerHelper$CaptureAppender with added customizability
 */
class CustomCaptureAppender extends AppenderBase<ILoggingEvent> {
    final PatternLayout layout

    CustomCaptureAppender(PatternLayout layout) {
        super()
        this.layout = layout
    }

    /**
     * Appends the logging event to the right renderer
     *
     * @param event Logged event
     */
    @Override
    protected void append(ILoggingEvent event) {
        final Session session = Global.session as Session

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
                switch (event.level) {
                    case Level.ERROR -> renderer.appendError(message)
                    case Level.WARN -> renderer.appendWarning(message)
                    default -> renderer.appendInfo(message)
                }
            }
        }
        catch (Throwable throwable) {
            throwable.printStackTrace()
        }
    }
}
