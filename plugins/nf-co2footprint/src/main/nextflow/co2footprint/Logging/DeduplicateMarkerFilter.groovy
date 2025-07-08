package nextflow.co2footprint.Logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import groovy.util.logging.Slf4j
import org.slf4j.Marker
import org.slf4j.MarkerFactory

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A logback TurboFilter for filtering the output according to markers.
 * Motivation: Removes duplicates in some warnings, to avoid cluttering the output with repeated information.
 * Example: If the CPU model is not found it should only be warned once, that a fallback value is used.
 */
@Slf4j
class DeduplicateMarkerFilter extends TurboFilter {

    // Markers to be Filtered
    private List<Marker> filteredMarkers

    // Number of allowed Repetitions of a message
    public int allowedOccurrences

    // Cached messages with count
    private ConcurrentHashMap<String, AtomicInteger> seenMessages

    // Scheduled outputs
    private Set<String> scheduledOut

    /**
     * Generate Filter which sends all duplicates to TRACE as [DUPLICATE]
     * after the maximum number of repetitions is exceeded
     *
     * @param filteredMarkers Markers to be filtered for (all other markers are ignored)
     * @param allowedOccurrences Number of allowed occurrences of a message
     */
    DeduplicateMarkerFilter(List<Marker> filteredMarkers, int allowedOccurrences=1) {
        this.filteredMarkers = filteredMarkers
        this.allowedOccurrences = allowedOccurrences
    }

    /**
     * Start the filter
     */
    @Override
    void start() {
        if (filteredMarkers) {
            seenMessages = [] as ConcurrentHashMap<String, AtomicInteger>
            scheduledOut = ConcurrentHashMap.newKeySet() as Set<String>
            super.start()
        }
    }

    /**
     * Stop the filter
     */
    @Override
    void stop() {
        seenMessages.clear()
        seenMessages = null
        super.stop()
    }

    /**
     * Check whether the message is duplicated and log it to TRACE if the maximum number of occurrences is exceeded.
     *
     * @param marker Marker of the log message
     * @param logger The logger the received the message
     * @param level Level of the log
     * @param format Message as a formatted string
     * @param params Parameters passed to the log to be filled into the message string
     * @param t Throwable exception
     * @return NEUTRAL, DENY, and ACCEPT command for the Logger
     */
    @Override
    FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        // Check whether the Filter started
        if (!isStarted()) {
            return FilterReply.NEUTRAL
        }

        // Checks for the right markers
        if (filteredMarkers.contains(marker)) {

            // Counts the occurrences for the markers
            AtomicInteger occurrences = seenMessages.computeIfAbsent(format, k -> new AtomicInteger(0))
            int currentOccurrences = occurrences.incrementAndGet()

            if (currentOccurrences <= allowedOccurrences) {
                return FilterReply.ACCEPT
            }
            // Send a TRACE message when the message was not accepted
            logger.trace('[DUPLICATE] ' + format, params)
            return FilterReply.DENY
        } else {
            return FilterReply.NEUTRAL
        }
    }

    // --- Getters and setters ---

    /**
     * Set the list of filtered markers.
     */
    void setFilteredMarkers(List<Marker> filteredMarkers) { this.filteredMarkers = filteredMarkers }
    List<Marker> getFilteredMarkers() { return filteredMarkers }

    /**
     * Set the allowed number of occurrences before a message is suppressed.
     */
    void setAllowedOccurrences(int allowedOccurrences) { this.allowedOccurrences = allowedOccurrences }
    int getAllowedOccurrences() { return allowedOccurrences }

    void setCacheSize(int cacheSize) { this.cacheSize = cacheSize }
    int getCacheSize() { return cacheSize }

    // --- Advanced marker manipulation ---

    /**
     * Add a Marker to the list of filtered markers.
     * @param marker Marker or name of marker to filter by
     */
    void addFilteredMarker(def marker) {
        marker = marker instanceof Marker ? marker : MarkerFactory.getMarker(marker as String)
        filteredMarkers.add(marker)
    }

    /**
     * Remove a Marker from the list of filtered Markers
     * @param marker Marker or name of marker to be removed
     */
    void removeFilteredMarker(def marker) {
        marker = marker instanceof Marker ? marker : MarkerFactory.getMarker(marker as String)
        filteredMarkers.remove(marker)
    }
}
