package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.FileCreation.ProvenanceFileCreator
import nextflow.co2footprint.Parsers.TraceFileParser
import nextflow.co2footprint.Records.CO2RecordTree
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
     * @param filePath Path to the trace or provenance file to parse and estimate from
     * @param configModifications Which changes should be made to the given config. Default: [:]
     * @param mode Whether to parse a trace file or a provenance file, default: 'trace'
     * @return A structure containing the CO2RecordTree with all inferred CO2Records and the config used for the estimation
     */
    @Function
    Output calculateCO2(
            Path filePath,
            Map<String, Object> configModifications=null,
            String mode='trace'
    ){
        assert mode in ['trace', 'provenance'], 'Please provide a valid mode, either `trace` or `provenance`.'

        // Define separate observer
        CO2FootprintConfig config = factory.defineConfig(configModifications, session)
        CO2FootprintCalculator calculator = new CO2FootprintCalculator(TDPDataMatrix.tdpDataMatrix, config)
        CO2FootprintObserver observer = new CO2FootprintObserver(config, calculator)

        // Create trace file
        observer.traceFile.create()

        // Define workflow metadata
        Session session = new Session()
        session.runName = 'Extension estimation'
        WorkflowMetadata metadata = new WorkflowMetadata(session, new ScriptFile( filePath ) )
        metadata.scriptName = 'nf-co2footprint Extension'
        metadata.projectName = 'nf-co2footprint Extension post-run'
        metadata.success = true
        metadata.commandLine = 'Executed from within workflow'

        if (mode == 'trace') {
            tracePostRun(filePath, observer, metadata)
        }
        else if (mode == 'provenance') {
            provenancePostRun(filePath, observer, metadata)
        }

        // Render files
        observer.renderFiles(observer.workflowStats, metadata)

        return new Output(observer.workflowStats, config)
    }

    /**
     * Define workflow metadata based on start and complete times.
     *
     * @param metadata WorkflowMetadata instance to fill with start, complete and duration based on the given start and complete times
     * @param start Start time in milliseconds since epoch, optional
     * @param complete Complete time in milliseconds since epoch, optional
     * @return The given WorkflowMetadata instance with filled start, complete and duration if the given start and complete times were not null, otherwise with unchanged values
     */
    static WorkflowMetadata defineTimeMetadata(WorkflowMetadata metadata, Long start, Long complete) {
        if (start != null){
            metadata.start =  Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC) ?: null
        }
        if (complete != null) {
            metadata.complete = Instant.ofEpochMilli(complete).atOffset(ZoneOffset.UTC) ?: null
        }
        if (metadata.start != null && metadata.complete != null) {
            metadata.duration = Duration.between(metadata.start, metadata.complete)
        }

        return metadata
    }


    /**
     * Post run estimation of footprint from a trace file.
     *
     * @param tracePath Path to the trace file
     * @param observer A CO2FootprintObserver instance to use for the estimation
     * @param metadata WorkflowMetadata instance to fill with workflow metadata based on the trace file
     * @return A Tuple2 containing the CO2FootprintObserver instance and the WorkflowMetadata instance with filled metadata
     */
    static void tracePostRun (Path tracePath, CO2FootprintObserver observer, WorkflowMetadata metadata) {
        // Parse the trace file
        List<TraceRecord> traceRecords = TraceFileParser.parseExecutionTraceFile(tracePath)

        // Collect CO2Records from traces & optionally write the corresponding files
        traceRecords.each { TraceRecord traceRecord ->
            observer.recordStarted(traceRecord)
            observer.aggregateRecords(traceRecord)
        }

        // Extract minimum start and maximum complete for workflow start and end approximation
        Long start = null
        Long complete = null
        traceRecords.each { TraceRecord traceRecord ->
            Long currentStart = traceRecord.get('start') as Long
            Long currentComplete = traceRecord.get('complete') as Long
            if (currentStart != null && (start == null || start > currentStart)){ start = currentStart }
            if (currentComplete != null && (complete == null || complete > currentComplete)){ complete = currentComplete }
        }
        metadata = defineTimeMetadata(metadata, start, complete)
    }

    /**
     * Post run estimation of footprint from a provenance file.
     *
     * @param provenancePath Path to the provenance file
     * @param observer A CO2FootprintObserver instance to use for the estimation
     * @param metadata WorkflowMetadata instance to fill with workflow metadata based on the provenance file
     */
    static void provenancePostRun (Path provenancePath, CO2FootprintObserver observer, WorkflowMetadata metadata) {
        // Parse provenance file
        CO2RecordTree co2RecordTree = ProvenanceFileCreator.read(provenancePath)

        for (CO2RecordTree taskTree : co2RecordTree.descentTo('task')) {
            observer.recordStarted(taskTree.co2Record)
            observer.aggregateRecords(taskTree.co2Record)
        }

        // Extract start and complete from provenance
        Long start = co2RecordTree.co2Record.store.get('start') as Long
        Long complete = co2RecordTree.co2Record.store.get('complete') as Long
        metadata = defineTimeMetadata(metadata, start, complete)
    }

    /**
     * Structure for Extension output.
     */
    class Output {
        CO2RecordTree co2RecordTree
        CO2FootprintConfig config

        Output(CO2RecordTree co2RecordTree, CO2FootprintConfig config) {
            this.co2RecordTree = co2RecordTree
            this.config = config
        }
    }
}
