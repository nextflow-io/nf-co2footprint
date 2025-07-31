package nextflow.co2footprint.utils

import nextflow.co2footprint.Logging.Markers

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
     * Retrieves a value from the trace record by key, or returns a default if the value is missing.
     * If the value is `null`, a warning is logged (once per unique dedupKey).
     *
     * @param trace        The TraceRecord containing task metrics.
     * @param taskID       The ID of the current task (used for logging).
     * @param key          The trace field key to retrieve (e.g. 'realtime', '%cpu', 'rss').
     * @param defaultValue The fallback value to return if the key is missing or null.
     * @param dedupKey     A unique identifier to deduplicate warning messages in the logs.
     * @return             The value from the trace if present, otherwise the defaultValue.
     */
    static Object getTraceOrDefault(TraceRecord trace, TaskId taskID, String key, Object defaultValue, String dedupKey) {
        def value = trace.get(key)
        if (value == null) {
            String warnMessage
            if (key == '%cpu') {
                def numCores = (defaultValue instanceof Number) ? defaultValue / 100 : defaultValue
                warnMessage = "Missing trace value '${key}' for task ${taskID}, using default: 100% for all ${numCores} cores."
            } else {
                warnMessage = "Missing trace value '${key}' for task ${taskID}, using default: ${defaultValue}."
            }
            String extraInfo = "\n\tThis message may also appear for other tasks — see `.nextflow.log` for all occurrences."
            log.warn(
                Markers.unique,
                warnMessage + extraInfo,
                dedupKey,
                warnMessage
            )
        }
        return value ?: defaultValue
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