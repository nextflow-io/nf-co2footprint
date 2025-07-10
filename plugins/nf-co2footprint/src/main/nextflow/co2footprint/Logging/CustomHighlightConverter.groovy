package nextflow.co2footprint.Logging

import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.Level

class CustomHighlightConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.level) {
            case Level.ERROR -> "31"    // red
            case Level.WARN -> "33"     // yellow
            case Level.INFO -> "37"     // white
            case Level.DEBUG -> "90"    // grey
            case Level.TRACE -> "90"    // grey
            default -> "37"             // white
        }
    }
}
