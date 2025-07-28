package nextflow.co2footprint.utils

import nextflow.co2footprint.utils.HelperFunctions
import org.slf4j.Marker
import groovy.util.logging.Slf4j

/**
 * Collection of helper functions for logging purposes.
 */
@Slf4j
class LoggingUtils {
    
    static void logDeduplicatedWarning(Marker marker, String debugMsg, List<String> dedupRegex, String suffix = " For subsequent tasks, this will only be reported in the nextflow.log file.") {
        String dedupKey = nextflow.co2footprint.utils.HelperFunctions.removePatterns(debugMsg, dedupRegex)
        String warnMessage = debugMsg + suffix
        log.warn(marker, warnMessage, dedupKey, debugMsg)
    }


}