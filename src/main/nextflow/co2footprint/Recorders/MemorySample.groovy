package nextflow.co2footprint.Recorders

/**
 * A sample of a process record with trace metrics.
 */
class MemorySample {
    long timestamp

    // Memory
    long rssBytes               // Resident Set Size
    long virtualMemoryBytes     // VSZ
}
