package nextflow.co2footprint.Logging

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent

class CustomPatternLayout extends PatternLayout {
    @Override
    String doLayout(ILoggingEvent event) {
        if (event.getMarkerList()?.contains(Markers.unique)) {
            event.formattedMessage =  "🔁 ${event.formattedMessage}" as String
        }
        return super.doLayout(event)
    }
}
