package nextflow.co2footprint.utils

import nextflow.co2footprint.Logging.Markers
import nextflow.co2footprint.utils.LoggingUtils


import groovy.util.logging.Slf4j
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

/**
 * Collection of helper functions.
 */
@Slf4j
class HelperFunctions {

    // Helper function to return bold text
    static String bold(String text) {
        return "\033[1m${text}\033[0m"
    }

    /**
    * Removes all occurrences of the specified patterns from the input string.
    *
    * @param input    The original string
    * @param patterns A list of regex patterns to remove from the string
    * @return         The string with all patterns removed
    */
    static String removePatterns(String input, List<String> patterns) {
        String result = input
        patterns.each { pattern ->
            result = result.replaceAll(pattern, '')
        }
        return result.trim()
    }

    /**
     * Returns the value for a given key from a TraceRecord, or a default if missing.
     * Logs a warning to stdout and log file the first time a key is missing,
     * and only to the log file for subsequent occurrences.
     *
     * @param trace        The TraceRecord to query
     * @param taskID       The TaskId for logging context
     * @param key          The key to look up in the trace
     * @param defaultValue The value to return if the key is missing
     * @return             The value from the trace, or the default if missing
     */
    static Object getTraceOrDefault(TraceRecord trace, TaskId taskID, String key, Object defaultValue) {
        def value = trace.get(key)
        if (value == null) {
            String debugMessage
            if (key == '%cpu') {
                def numCores = (defaultValue instanceof Number) ? defaultValue / 100 : defaultValue
                debugMessage = "Missing trace value '${key}' for task ${taskID}, using default: 100% for all ${numCores} cores."
            } else {
                debugMessage = "Missing trace value '${key}' for task ${taskID}, using default: ${defaultValue}."
            }
            LoggingUtils.logDeduplicatedWarning(
                Markers.unique,
                debugMessage,
                [ / for task [^,]+/ ],
                " For subsequent tasks missing '${key}', this will only be reported in the Nextflow log file."
            )
            return defaultValue
        }
        return value
    }

    /**
     * Get the available system memory in bytes using the OperatingSystemMXBean.
     * Throws an exception if the value cannot be determined.
     *
     * @param taskID The TaskId for logging context
     * @return       Available system memory in bytes
     */
    static Long getAvailableSystemMemory(TaskId taskID) {
        try {
            final OperatingSystemMXBean OS = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()
            Long availableMemory = OS.getTotalMemorySize() as Long
            if (availableMemory == null || availableMemory == 0) {
                log.error("Could not determine available system memory for task ${taskID}.")
                throw new IllegalStateException("Available memory is null or zero for task ${taskID}. Cannot proceed.")
            }
            return availableMemory
        } catch (Exception e) {
            log.error("Error while retrieving available system memory for task ${taskID}: ${e.message}", e)
            throw new IllegalStateException("Error retrieving available memory for task ${taskID}.", e)
        }
    }

}