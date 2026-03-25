package nextflow.co2footprint.Recorders

/**
 * A sample of a memory record captured for a process.
 */
class MemorySample {
    long timestamp

    // Memory
    long rssBytes               // Resident Set Size
    long virtualMemoryBytes     // VSZ
}
