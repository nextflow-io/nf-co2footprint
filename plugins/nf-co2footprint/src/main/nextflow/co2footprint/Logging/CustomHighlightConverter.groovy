package nextflow.co2footprint.Logging

import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.Level

class CustomHighlightConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.level.toInt()) {
            case Level.ERROR_INT -> "31" // red
            case Level.WARN_INT -> "33" // yellow
            default -> "37" // white
        }
    }
}
