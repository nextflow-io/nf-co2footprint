package nextflow.co2footprint.Logging

import org.slf4j.Marker
import org.slf4j.MarkerFactory

/**
 * A marker to indicate that this message should only be shown once
 */
class Markers {
    /*
     * Marker for messages that should only be logged once and prepended with a 🔁
     */
    final static Marker unique = MarkerFactory.getMarker('unique')

    /*
     * Marker for messages that should only be logged once without additions
     */
    final static Marker silentUnique = MarkerFactory.getMarker('silentUnique')
}
