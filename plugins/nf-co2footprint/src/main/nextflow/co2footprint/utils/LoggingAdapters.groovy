package nextflow.co2footprint.utils

import org.slf4j.Marker
import org.slf4j.MarkerFactory

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply

import java.util.concurrent.ConcurrentHashMap

/**
 * A marker to indicate that this message should only be shown once
 */
class Markers {
    final static Marker unique = MarkerFactory.getMarker('unique')
}


/**
 * A logback TurboFilter for filtering the output according to markers.
 */
class DeduplicateMarkerFilter extends TurboFilter {

    /**
     * Markers to be Filtered
     */
    private List<Marker> filteredMarkers

    /**
     * Number of allowed Repetitions of a message
     */
    public int allowedOccurrences

    /**
     * Cached messages with count
     */
    private ConcurrentHashMap<String, Integer> msgCache

    DeduplicateMarkerFilter(List<Marker> filteredMarkers, int allowedOccurrences=1) {
        this.filteredMarkers = filteredMarkers
        this.allowedOccurrences = allowedOccurrences
    }

    @Override
    void start() {
        if (filteredMarkers) {
            msgCache = [] as ConcurrentHashMap<String, Integer>
            super.start()
        }
    }

    @Override
    void stop() {
        msgCache.clear()
        msgCache = null
        super.stop()
    }

    @Override
    FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
            // Check whether the Filter started
            if (!isStarted()) {
                return FilterReply.NEUTRAL
            }

            // Checks for the right markers
            if (filteredMarkers.contains(marker)) {

                // Counts the occurrences for the markers
                Integer count = msgCache.putIfAbsent(format, 0) ?: 0

                // Increments the occurrences by 1
                msgCache[format] = count + 1

                if (count < allowedOccurrences) {
                    return FilterReply.ACCEPT
                } else {
                    return FilterReply.DENY
                }
            } else {
                return FilterReply.NEUTRAL
            }
    }

    // Simple getters / setters
    void setFilteredMarkers(List<Marker> filteredMarkers) { this.filteredMarkers = filteredMarkers }
    List<Marker> getFilteredMarkers() { return filteredMarkers }

    /**
     * The allowed number of occurrences before the message is no longer repeated
     *
     * @param allowedOccurrences
     */
    void setAllowedOccurrences(int allowedOccurrences) { this.allowedOccurrences = allowedOccurrences }
    int getAllowedOccurrences() { return allowedOccurrences }

    void setCacheSize(int cacheSize) { this.cacheSize = cacheSize }
    int getCacheSize() { return cacheSize }

    // Advanced manipulation of parameters
    /**
     * Adds a Marker to the list of filtered Markers
     * @param marker Marker or name of marker to filter by
     */
    void addFilteredMarker(def marker) {
        marker = marker instanceof Marker ? marker : MarkerFactory.getMarker(marker as String)
        filteredMarkers.add(marker)
    }

    void removeFilteredMarker(def marker) {
        marker = marker instanceof Marker ? marker : MarkerFactory.getMarker(marker as String)
        filteredMarkers.remove(marker)
    }
}
