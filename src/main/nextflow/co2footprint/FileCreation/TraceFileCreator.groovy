package nextflow.co2footprint.FileCreation

import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.Config.TraceFileConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.processor.TaskId
import nextflow.trace.TraceHelper
import nextflow.trace.TraceRecord

/**
 * Generates the CO₂ footprint trace file.
 *
 * Writes per-task energy, CO₂, and resource usage in tab-separated format.
 * Uses an agent for thread-safe writing.
 */
@Slf4j
class TraceFileCreator extends BaseFileCreator {

    // Agent for thread-safe writing to the trace file
    private Agent<PrintWriter> traceWriter

    /**
     * Constructor for the trace file.
     *
     * @param config A {@link TraceFileConfig} that defines the created file.
     */
    TraceFileCreator(TraceFileConfig config) {
        super(config)

        if(!config.enabled) {
            this.metaClass.create = { -> null }
            this.metaClass.write = { CO2Record X -> null }
            this.metaClass.close = { Map<TaskId, TraceRecord> X -> null }
        }
    }

    /**
     * Create the trace file and write the header.
     * If file already exists, it is overwritten or rolled depending on settings.
     */
    void create() {
        super.create()

        // Create a new trace file writer
        writer = TraceHelper.newFileWriter(path, overwrite, 'co2footprint')
        file = new PrintWriter(this.writer)

        // Launch the agent for thread-safe writing
        traceWriter = new Agent<PrintWriter>(file)

        traceWriter.send {
            file.println( String.join('\t', CO2Record.emissionMetrics) )
            file.flush()
        }
    }

    /**
     * Write a single task's trace record to the file.
     *
     * @param traceRecord   TraceRecord for the task
     * @param co2Record     CO2Record for the task
     */
    void write(CO2Record co2Record){
        if (!created) { return }

        List<String> recordedEntries = co2Record.getReadableEntries(CO2Record.emissionMetrics)

        traceWriter.send { PrintWriter writer ->
            writer.println( String.join('\t', recordedEntries) )
            writer.flush()
        }
    }

    /**
     * Close the trace file after writing any remaining records.
     *
     * @param current Map of TaskId to TraceRecord for unfinished tasks
     */
    void close(Map<TaskId, TraceRecord> current) {
        if (!created) { return }

        // Wait for agent to finish and flush content
        traceWriter.await()

        // Write remaining records for unfinished tasks
        current.values().each { TraceRecord record ->
            file.println("${record.taskId}\t-")
        }
        file.flush()

        file.close()
        writer.close()
    }
}
