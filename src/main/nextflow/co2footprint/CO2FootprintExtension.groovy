package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Parsers.TraceFileParser
import nextflow.co2footprint.Records.CO2Record
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import nextflow.script.ScriptFile
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceRecord
import nextflow.util.Duration

import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset

/**
 * A {@link PluginExtensionPoint} to interact with core functionalities of the plugin.
 */
class CO2FootprintExtension extends PluginExtensionPoint {
    /**
     * Instance of an observer to simulate a run with a parsed trace file.
     */
    CO2FootprintFactory factory

    /**
     * Instance of the current session.
     */
    Session session

    /**
     * Initializes the Extension point of the plugin with the session to create an observer.
     */
    @Override
    void init(Session session) {
        factory = new CO2FootprintFactory()
        this.session = session
    }

    /**
     * Parse the content of a Nextflow trace file in its raw and readable format.
     *
     * @param tracePath Path to the trace file
     * @param delimiter Delimiter of the trace file, default: \t
     * @return A list of all task-specific trace records inferred from the trace file
     */
    @Function
    List<TraceRecord> parseTraceFile(Path tracePath, String delimiter='\t') {
        return TraceFileParser.parseExecutionTraceFile(tracePath, delimiter)
    }

    /**
     * Calculate the CO2 footprint of all tasks in a trace file.
     *
     * @param tracePath Path to the trace file
     * @param configModifications Which changes should be made to the given config. Default: [:]
     * @return A {@link List} of {@link CO2Record}s that were extracted from the given tasks
     */
    @Function
    Output calculateCO2(
            Path tracePath,
            Map<String, Object> configModifications=null
    ){
        // Define separate observer
        CO2FootprintConfig config = factory.defineConfig(configModifications, session)
        CO2FootprintCalculator calculator = new CO2FootprintCalculator(TDPDataMatrix.tdpDataMatrix, config)
        CO2FootprintObserver observer = new CO2FootprintObserver(config, calculator)

        // Parse the trace file
        List<TraceRecord> traceRecords = parseTraceFile(tracePath)

        // Create trace file
        observer.traceFile.create()

        // Collect CO2Records from traces & optionally write the corresponding files
        List<CO2Record> co2Records = []
        traceRecords.each { TraceRecord traceRecord ->
            observer.recordStarted(traceRecord)
            co2Records.add(observer.aggregateRecords(traceRecord))
        }

        // Define workflow metadata
        Session session = new Session()
        session.runName = 'Extension estimation'
        WorkflowMetadata metadata = new WorkflowMetadata(session, new ScriptFile( tracePath ) )
        metadata.scriptName = 'nf-co2footprint Extension'
        metadata.projectName = 'nf-co2footprint Extension post-run'
        metadata.success = true
        metadata.commandLine = 'Executed from within workflow'
        Long start = null
        Long complete = null
        traceRecords.each { TraceRecord traceRecord ->
            Long currentStart = traceRecord.get('start') as Long
            Long currentComplete = traceRecord.get('complete') as Long
            if (currentStart != null && (start == null || start > currentStart)){ start = currentStart}
            if (currentComplete != null && (complete == null || complete > currentComplete)){ complete = currentComplete}
        }
        if (start != null){
            metadata.start =  Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC) ?: null
        }
        if (complete != null) {
            metadata.complete = Instant.ofEpochMilli(complete).atOffset(ZoneOffset.UTC) ?: null
        }
        if (metadata.start != null && metadata.complete != null) {
            metadata.duration = Duration.between(metadata.start, metadata.complete)
        }

        // Render files
        observer.renderFiles(observer.workflowStats, metadata)


        return new Output(co2Records, config)
    }

    /**
     * Structure for Extension output.
     */
    class Output {
        List<CO2Record> co2Records
        CO2FootprintConfig config

        Output(List<CO2Record> co2Records, CO2FootprintConfig config) {
            this.co2Records = co2Records
            this.config = config
        }
    }
}
