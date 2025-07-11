package nextflow.co2footprint.FileCreators

import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.Records.CO2Record
import nextflow.processor.TaskId
import nextflow.trace.TraceHelper
import nextflow.trace.TraceRecord

import java.nio.file.Path

/**
 * Generates the CO₂ footprint trace file.
 *
 * Writes per-task energy, CO₂, and resource usage in tab-separated format.
 * Uses an agent for thread-safe writing.
 */
@Slf4j
class CO2FootprintTrace extends CO2FootprintFile {

    // Agent for thread-safe writing to the trace file
    private Agent<PrintWriter> traceWriter

    /**
     * Constructor for the trace file.
     *
     * @param path      Path to the trace file
     * @param overwrite Whether to overwrite existing files
     */
    CO2FootprintTrace(Path path, boolean overwrite) {
        super(path, overwrite)
    }

    /**
     * Create the trace file and write the header.
     * If file already exists, it is overwritten or rolled depending on settings.
     */
    void create() {
        // Create a new trace file writer
        file = new PrintWriter(TraceHelper.newFileWriter(path, overwrite, 'co2footprint'))

        // Launch the agent for thread-safe writing
        traceWriter = new Agent<PrintWriter>(file)

        // Write the header line to the trace file
        List<String> headers = [
                'task_id', 'status', 'name', 'energy_consumption', 'CO2e', 'CO2e_market', 'time',
                'carbon_intensity', 'carbon_intensity_market', 'cpus', 'powerdraw_cpu',
                'cpu_model', 'cpu_usage', 'requested_memory'
        ]

        traceWriter.send {
            file.println( String.join('\t', headers) )
            file.flush()
        }
    }

    /**
     * Write a single task's trace record to the file.
     *
     * @param taskId    Task identifier
     * @param trace     TraceRecord for the task
     * @param co2Record CO2Record for the task
     */
    void write(TaskId taskId, TraceRecord trace, CO2Record co2Record){
        List<String> records = co2Record.getReadableEntries()

        records = [taskId as String, trace.get('status') as String] + records

        traceWriter.send { PrintWriter writer ->
            writer.println( String.join('\t', records) )
            writer.flush()
        }
    }

    /**
     * Close the trace file after writing any remaining records.
     *
     * @param current Map of TaskId to TraceRecord for unfinished tasks
     */
    void close(Map<TaskId, TraceRecord> current) {
        // Wait for agent to finish and flush content
        traceWriter.await()

        // Write remaining records for unfinished tasks
        current.values().each { record ->
            file.println("${record.taskId}\t-")
        }
        file.flush()
        file.close()
    }
}
