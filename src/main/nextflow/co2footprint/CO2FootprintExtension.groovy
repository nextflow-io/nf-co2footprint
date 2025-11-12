package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Parsers.TraceFileParser
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import nextflow.trace.TraceRecord

import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * A {@link PluginExtensionPoint} to interact with core functionalities of the plugin.
 */
class CO2FootprintExtension extends PluginExtensionPoint {
    /**
     * Instance of an observer to simulate a run with a parsed trace file.
     */
    CO2FootprintFactory factory

    /**
     * Initializes the Extension point of the plugin with the session to create an observer.
     */
    @Override
    void init(Session session) {
        factory = new CO2FootprintFactory()
        factory.create(session)[0]
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
     * @param timeCIs A map of times linked to CI values. Can be used to infer the CI during the run which produced the trace file
     * @return A {@link List} of {@link CO2Record}s that were extracted from the given tasks
     */
    @Function
    List<CO2Record> calculateCO2(
            Path tracePath,
            Map<String, Object> configModifications=null,
            Map<LocalDateTime, Number> timeCIs=null
    ){
        // Define separate observer
        CO2FootprintConfig config = factory.defineConfig(configModifications)
        CO2FootprintObserver observer = factory.defineObserver(config)

        // Parse the trace file
        List<TraceRecord> traceRecords = parseTraceFile(tracePath)

        // Determine CI
        if (timeCIs) {
            observer.timeCiRecordCollector = new CiRecordCollector(config, timeCIs as ConcurrentHashMap)
        }

        // Prepare aggregator
        observer.aggregator = new CO2RecordAggregator()

        // Create trace file
        observer.traceFile.create()

        // Collect CO2Records from traces & optionally write the corresponding files
        List<CO2Record> co2Records = []
        traceRecords.each { TraceRecord traceRecord ->
            observer.startRecord(traceRecord)
            co2Records.add(observer.aggregateRecords(traceRecord))
        }
        observer.renderFiles()

        return co2Records
    }
}
