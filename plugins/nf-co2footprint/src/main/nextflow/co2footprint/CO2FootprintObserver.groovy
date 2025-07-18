package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.FileCreators.CO2FootprintReport
import nextflow.co2footprint.FileCreators.CO2FootprintSummary
import nextflow.co2footprint.FileCreators.CO2FootprintTrace
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap


/**
 * Observer for CO₂ footprint reporting in Nextflow workflows.
 *
 * Tracks task execution, collects resource usage, computes CO₂ emissions,
 * and writes trace, summary, and HTML report files.
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>,
 *         Sabrina Krakau <sabrinakrakau@gmail.com>,
 *         Josua Carl <josua.carl@uni-tuebingen.de>
 */
@Slf4j
class CO2FootprintObserver implements TraceObserver {

    // Plugin version
    private String version

    // Holds workflow session
    private Session session

    // Output file paths
    private Map<String, Path> paths = [:]

    // Output file objects
    private CO2FootprintTrace co2eTraceFile
    private CO2FootprintSummary co2eSummaryFile
    private CO2FootprintReport co2eReportFile

    // Overwrite existing files if true
    private boolean overwrite

    // Max number of tasks allowed in the report, when they exceed this number the tasks table is omitted
    private int maxTasks

    // Plugin configuration
    CO2FootprintConfig config

    // Aggregator for resource usage stats
    private CO2RecordAggregator aggregator

    // Calculator for CO₂ footprint
    private CO2FootprintComputer co2FootprintComputer
    CO2FootprintComputer getCO2FootprintComputer() { co2FootprintComputer }

    // Holds the the start time for tasks started/submitted but not yet completed
    @PackageScope
    Map<TaskId, TraceRecord> current = new ConcurrentHashMap<>()

    // Stores CO₂ emission records by task ID
    final private Map<TaskId, CO2Record> co2eRecords = new ConcurrentHashMap<>()
    Map<TaskId,CO2Record> getCO2eRecords() { co2eRecords }

    // Stores all trace records by task ID
    final private Map<TaskId, TraceRecord> traceRecords = new LinkedHashMap<>()

    /**
     * Constructor for the observer.
     *
     * @param session Nextflow session
     * @param version Plugin version
     * @param config Plugin configuration
     * @param co2FootprintComputer CO₂ computation instance
     * @param overwrite Whether to overwrite existing documents
     * @param maxTasks The maximum number of tasks until the table in the report is dropped
     */
    CO2FootprintObserver(
            Session session,
            String version,
            CO2FootprintConfig config,
            CO2FootprintComputer co2FootprintComputer,
            boolean overwrite=true,
            int maxTasks=10_000
    ) {
        this.session = session
        this.version = version
        this.config = config

        // Generate CO2 footprint output files (trace, summary, HTML report)
        this.paths['co2eTrace'] = (config.getTraceFile() as Path).complete()
        this.paths['co2eSummary'] = (config.getSummaryFile() as Path).complete()
        this.paths['co2eReport'] = (config.getReportFile() as Path).complete()

        this.co2FootprintComputer = co2FootprintComputer
        this.overwrite = overwrite
        this.maxTasks = maxTasks
    }

    /**
     * Enables the collection of the task executions metrics in order to be reported in the HTML report.
     *
     * @return {@code true} to enable metrics collection
     */
    @Override
    boolean enableMetrics() { return true }

    /**
     * Set the maximum number of tasks to include in the report table.
     * If exceeded, the table is cut off after the maximum number.
     *
     * @param value Maximum number of tasks to include in the report
     * @return This observer instance
     */
    CO2FootprintObserver setMaxTasks(int value) {
        this.maxTasks = value
        return this
    }

    // ------ OBSERVER METHODS ------

    // ---- WORKFLOW LEVEL ----

    /**
     * Start of the workflow; Creates the trace file.
     *
     * @param session The current Nextflow session
     */
    @Override
    void onFlowCreate(Session session) {
        log.debug("Workflow started -- co2e outputs: ${paths}")

        // Construct session and aggregator
        this.session = session
        this.aggregator = new CO2RecordAggregator()

        // make sure parent paths exists
        paths.each {key, path ->
            final Path parent = path.normalize().getParent()
            if (parent) {
                Files.createDirectories(parent)
                return
            }
        }

        co2eTraceFile = new CO2FootprintTrace(paths['co2eTrace'], overwrite)
        co2eTraceFile.create()

        co2eSummaryFile = new CO2FootprintSummary(paths['co2eSummary'], overwrite)

        co2eReportFile = new CO2FootprintReport(paths['co2eReport'], overwrite, maxTasks)
    }

    /**
     * Save the pending processes and close the files
     */
    void onFlowComplete() {
        log.debug("Workflow completed -- rendering & saving files")

        // Compute the statistics (total, mean, min, max, quantiles) on process level
        final Map<String, Map<String, Map<String, ?>>> processStats = aggregator.computeProcessStats()

        // Collect the total sums of all metrics
        final Map<String, Double> totalStats = [:]
        processStats.each { String processName, Map<String, Map<String, ?>> processMetrics ->
            processMetrics.each { String metricName, Map<String, ?> metricValue ->
                // Add up the different metrics (co2e, energy, ...)
                if (metricValue['total']) {
                    totalStats[metricName] = metricValue['total'] as Double + (totalStats.get(metricName) as Double ?: 0d)
                }
            }
        }

        // Write report and summary
        co2eSummaryFile.write(totalStats, co2FootprintComputer, config, version)

        co2eReportFile.addEntries(processStats, totalStats, co2FootprintComputer, config, version, session, traceRecords, co2eRecords)
        co2eReportFile.write()

        // Close all files (writes remaining tasks in the trace file)
        co2eTraceFile.close(current)
        co2eSummaryFile.close()
        co2eReportFile.close()
    }


    // ---- PROCESS LEVEL ----

    /**
     * This method is invoked when a process is created
     *
     * @param process The process created ({@link nextflow.processor.TaskProcessor})
     */
    @Override
    void onProcessCreate(TaskProcessor process) { 
    }

    /**
     * This method is invoked before a process run is going to be submitted.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - submit process > $handler")

        current[trace.taskId] = trace

        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
        }
    }

    /**
     * This method is invoked when a process run is going to start.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessStart(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - start process > $handler")

        current[trace.taskId] = trace

        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
        }
    }

    /**
     * This method is invoked when a process run completes.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - complete process > $handler")
        final TaskId taskId = handler.task.id

        // Ensure the presence of a Trace Record
        if (!trace) {
            log.warn("Unable to find TraceRecord for task with id: ${taskId}")
            return
        }

        // Remove the record from the current records
        current.remove(taskId)

        // Extract CO2e records
        final CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(taskId, trace)

        // Collect results
        co2eRecords[taskId] = co2Record

        // Aggregate results
        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
            aggregator.add(trace, co2Record)
        }

        // Save to files
        co2eTraceFile.write(taskId, trace, co2Record)
    }

    /**
     * This method is invoked when a process was cached.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - cached process > $handler")
        final TaskId taskId = handler.task.id

        // Event was triggered by a stored task, ignore it
        if (trace == null) { return }

        // Extract records
        final CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(taskId, trace)

        // Collect results
        co2eRecords[taskId] = co2Record

        // Aggregate results
        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
            aggregator.add(trace, co2Record)
        }


        // Save to the files
        co2eTraceFile.write(taskId, trace, co2Record)
    }

}
