package nextflow.co2footprint.FileCreators

import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.CO2Record
import nextflow.processor.TaskId
import nextflow.trace.TraceHelper
import nextflow.trace.TraceRecord

import java.nio.file.Path

@Slf4j
/**
 * Class to generate the Trace file
 */
class CO2FootprintTrace extends CO2FootprintFile {

    private Agent<PrintWriter> traceWriter

    /**
     * Create the trace file
     *
     * @param path A path to the file where save the CO2 emission data
     */
    CO2FootprintTrace(Path path, boolean overwrite) {
        super(path, overwrite)
    }

    /**
     * Create the trace file, if file already exists it is "rolled" to a new file
     */
    void create() {
        // create a new trace file
        file = new PrintWriter(TraceHelper.newFileWriter(path, overwrite, 'co2footprint'))

        // launch the agent
        traceWriter = new Agent<PrintWriter>(file)

        List<String> headers = [
                'task_id', 'status', 'name', 'energy_consumption', 'CO2e', 'time', 'cpus', 'powerdraw_cpu',
                'cpu_model', 'cpu_usage', 'requested_memory'
        ]

        traceWriter.send {
            file.println( String.join('\t', headers) )
            file.flush()
        }
    }

    void write(TaskId taskId, TraceRecord trace, CO2Record co2Record){
        List<String> records = co2Record.getReadableEntries()

        records = [taskId as String, trace.get('status') as String] + records

        traceWriter.send { PrintWriter writer ->
            writer.println( String.join('\t', records) )
            writer.flush()
        }
    }

    /**
     * Close the file after retrieving the remaining information from the current CO2Record
     * @param current
     */
    void close(Map<TaskId, TraceRecord> current) {
        // wait for termination and flush the agent content
        traceWriter.await()

        // write the remaining records
        current.values().each { record ->
            file.println("${record.taskId}\t-")
        }
        file.flush()
        file.close()
    }
}
