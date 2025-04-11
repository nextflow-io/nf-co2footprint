/**
 * This script provides logging utilities for managing and filtering log messages in a controlled manner.
 * 
 * Classes included:
 * 1. **Markers**:
 *    - Defines a unique marker (`unique`) to indicate that a log message should only be shown once.
 * 
 * 2. **DeduplicateMarkerFilter**:
 *    - A custom Logback `TurboFilter` that filters log messages based on markers.
 *    - Prevents duplicate log messages by limiting the number of times a message with a specific marker is logged.
 *    - Supports advanced configuration, such as setting allowed occurrences and managing filtered markers dynamically.
´* 
 * Author: Josua Carl <josua.carl@uni-tuebingen.de>
 */
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
 * A custom Logback TurboFilter for filtering log messages based on markers.
 * 
 * This filter prevents duplicate log messages by limiting the number of times a message with a specific marker is logged.
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

    /**
     * Constructor for the DeduplicateMarkerFilter.
     * 
     * @param filteredMarkers A list of markers to filter.
     * @param allowedOccurrences The maximum number of times a message with a specific marker can be logged (default: 1).
     */
    DeduplicateMarkerFilter(List<Marker> filteredMarkers, int allowedOccurrences=1) {
        this.filteredMarkers = filteredMarkers
        this.allowedOccurrences = allowedOccurrences
    }

    /**
     * Starts the filter and initializes the message cache.
     */
    @Override
    void start() {
        if (filteredMarkers) {
            msgCache = [] as ConcurrentHashMap<String, Integer>
            super.start()
        }
    }

    /**
     * Stops the filter and clears the message cache.
     */
    @Override
    void stop() {
        msgCache.clear()
        msgCache = null
        super.stop()
    }

    /**
     * Decides whether a log message should be accepted, denied, or remain neutral based on its marker.
     * 
     * @param marker The marker associated with the log message.
     * @param logger The logger instance.
     * @param level The log level of the message.
     * @param format The log message format string.
     * @param params The parameters for the log message.
     * @param t The throwable associated with the log message (if any).
     * @return A `FilterReply` indicating whether the message should be accepted, denied, or remain neutral.
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

    /**
     * Sets the list of markers to be filtered.
     * 
     * @param filteredMarkers A list of markers to filter.
     */
    void setFilteredMarkers(List<Marker> filteredMarkers) { this.filteredMarkers = filteredMarkers }
    List<Marker> getFilteredMarkers() { return filteredMarkers }

    /**
     * Sets the allowed number of occurrences for a log message.
     * 
     * @param allowedOccurrences The maximum number of times a message can be logged.
     */
    void setAllowedOccurrences(int allowedOccurrences) { this.allowedOccurrences = allowedOccurrences }
    int getAllowedOccurrences() { return allowedOccurrences }

    void setCacheSize(int cacheSize) { this.cacheSize = cacheSize }
    int getCacheSize() { return cacheSize }

    // Advanced manipulation of parameters

    /**
     * Adds a marker to the list of filtered markers.
     * 
     * @param marker The marker (or marker name) to add to the filter list.
     */
    void addFilteredMarker(def marker) {
        marker = marker instanceof Marker ? marker : MarkerFactory.getMarker(marker as String)
        filteredMarkers.add(marker)
    }
    
    /**
     * Removes a marker from the list of filtered markers.
     * 
     * @param marker The marker (or marker name) to remove from the filter list.
     */
    void removeFilteredMarker(def marker) {
        marker = marker instanceof Marker ? marker : MarkerFactory.getMarker(marker as String)
        filteredMarkers.remove(marker)
    }
}
