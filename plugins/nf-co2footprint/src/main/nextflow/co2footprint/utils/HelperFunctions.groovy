package nextflow.co2footprint.utils

import groovy.util.logging.Slf4j
import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord

@Slf4j
class HelperFunctions {

    // Helper function to return bold text
    static String bold(String text) {
        return "\033[1m${text}\033[0m"
    }

    /**
     * Helper to safely get a trace value or return a default value if the key is missing.
     * Logs a warning if the key is not found.
     */
    static Object getTraceOrDefault(TraceRecord trace, TaskId taskID, String key, Object defaultValue) {
        def value = trace.get(key)
        if (value == null) {
            log.warn("[WARN] Missing trace value '${key}' for task ${taskID}, using default: ${defaultValue}")
            return defaultValue
        }
        return value
    }

    /**
     * Helper to safely get available system memory in bytes.
     * Throws an exception if unavailable.
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