package nextflow.co2footprint.FileCreation

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
class TraceFileCreator extends BaseFileCreator {

    // Agent for thread-safe writing to the trace file
    private Agent<PrintWriter> traceWriter

    // Execution & CO2-footprint trace keys that are included into trace file
    private List<String> entryKeys = [
            'task_id', 'status', 'name', 'energy', 'co2e', 'co2eMarket', 'ci', 'cpuUsage', 'memory', 'time', 'cpus',
            'powerdrawCPU', 'cpu_model', 'rawEnergyProcessor', 'rawEnergyMemory'
    ]

    // Mapping of entry keys to the CO2-footprint trace file header
    private final Map<String, String> keyHeaderMapping = [
        task_id: 'task_id', status: 'status',
        name:'name', energy: 'energy_consumption', co2e: 'CO2e', co2eMarket: 'CO2e_market', ci: 'carbon_intensity',
        cpuUsage: '%cpu', memory: 'memory', time: 'realtime', cpus: 'cpus', powerdrawCPU: 'powerdraw_cpu',
        cpu_model: 'cpu_model', rawEnergyProcessor: 'rawEnergyProcessor', rawEnergyMemory: 'rawEnergyMemory'
    ]

    /**
     * Constructor for the trace file.
     *
     * @param path      Path to the trace file
     * @param overwrite Whether to overwrite existing files
     */
    TraceFileCreator(Path path, boolean overwrite) {
        super(path, overwrite)
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

        // Write the header line to the trace file
        List<String> headers = entryKeys.collect { String entryName -> keyHeaderMapping.get(entryName) }

        traceWriter.send {
            file.println( String.join('\t', headers) )
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

        List<String> recordedEntries = co2Record.getReadableEntries(entryKeys) //  entryKeys.collect { String key -> getReadableEntry(key, traceRecord, co2Record) } ?

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
