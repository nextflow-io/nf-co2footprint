package nextflow.co2footprint.Recorders

/**
 * A sample of a process record with trace metrics.
 */
class RecordSample {
    long timestamp

    // CPU
    double cpuUsage         // process CPU
    double systemCpu        // whole-system CPU

    // Memory
    long rssBytes               // Resident Set Size
    long virtualMemoryBytes     // VSZ

    // I/O
    long readBytes
    long writeBytes

    // Context switches
    long voluntaryContextSwitches
    long involuntaryContextSwitches

    String toString() {
        """[${new Date(timestamp).format('HH:mm:ss.SSS')}]
          CPU (process)  : ${String.format('%.1f', cpuUsage * 100)}%
          CPU (system)   : ${String.format('%.1f', systemCpu * 100)}%
          RSS            : ${rssBytes       / (1024 ** 3)} GB
          Virtual Mem    : ${virtualMemoryBytes / (1024 ** 3)} GB
          Read bytes     : ${readBytes      / 1024} KB
          Write bytes    : ${writeBytes     / 1024} KB
          Ctx switches   : ${voluntaryContextSwitches} voluntary / ${involuntaryContextSwitches} involuntary
        """.stripIndent()
    }
}
