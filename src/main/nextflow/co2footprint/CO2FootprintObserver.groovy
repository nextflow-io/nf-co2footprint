package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.FileCreation.ReportFileCreator
import nextflow.co2footprint.FileCreation.SummaryFileCreator
import nextflow.co2footprint.FileCreation.TraceFileCreator
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

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

    // Output file objects
    private TraceFileCreator traceFile
    private SummaryFileCreator summaryFile
    private ReportFileCreator reportFile

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

    // Record for CI values during execution
    CiRecordCollector timeCiRecordCollector
    CiRecordCollector getTimeCiRecordCollector() { timeCiRecordCollector }

    // Holds the the start time for tasks started/submitted but not yet completed
    @PackageScope
    Map<TaskId, TraceRecord> runningTasks = new ConcurrentHashMap<>()

    // Stores CO₂ emission records by task ID
    final private Map<TaskId, CO2Record> co2eRecords = new ConcurrentHashMap<>()
    Map<TaskId,CO2Record> getCO2eRecords() { co2eRecords }

    // Stores all trace records by task ID
    final private Map<TaskId, TraceRecord> traceRecords = new ConcurrentHashMap<>()

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

        // Make file instances
        def traceConf = config.value('trace')
        if (traceConf && traceConf.value('enabled')) {
            this.traceFile = new TraceFileCreator((traceConf.value('file') as Path).complete(), overwrite)
        }

        def summaryConf = config.value('summary')
        if (summaryConf && summaryConf.value('enabled')) {
            this.summaryFile = new SummaryFileCreator((summaryConf.value('file') as Path).complete(), overwrite)
        }

        def reportConf = config.value('report')
        if (reportConf && reportConf.value('enabled')) {
            this.reportFile = new ReportFileCreator((reportConf.value('file') as Path).complete(), overwrite, maxTasks)
        }

        this.co2FootprintComputer = co2FootprintComputer
        this.overwrite = overwrite
        this.maxTasks = maxTasks

        this.timeCiRecordCollector = new CiRecordCollector(config)
    }

    /**
     * Enables the collection of the task executions metrics in order to be reported in the HTML report.
     *
     * @return {@code true} to enable metrics collection
     */
    @Override
    boolean enableMetrics() { return true }

    // ------ HELPER METHODS ------

    /**
     * Records the start of a task by storing its {@link TraceRecord}.
     *
     * @param trace the TraceRecord of the task that just started
     */
    private synchronized void startRecord(TraceRecord trace) {
        // Keep started tasks
        runningTasks[trace.taskId] = trace

        // Extend trace records
        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
        }
    }

    /**
     * Aggregates the trace and CO₂ records of a finished task.
     *
     * @param trace TraceRecord of the finished task
     */
    private synchronized void aggregateRecords(TraceRecord trace) {
        // Remove task from set of running tasks
        runningTasks.remove(trace.taskId)

        // Store final trace
        traceRecords[ trace.taskId ] = trace

        // Compute CO₂ footprint for this task
        final CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(trace.taskId, trace, timeCiRecordCollector)

        // Save per-task CO₂ result
        co2eRecords[trace.taskId] = co2Record

        // Update overall aggregated stats
        aggregator.add(trace, co2Record)

        // Optionally write to trace file
        this.traceFile?.write(trace, co2Record)
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
        log.debug('Workflow started -- CO2Footprint file instantiated')

        // Construct session and aggregator
        this.session = session
        this.aggregator = new CO2RecordAggregator()

        // we wouldn't expect a config where all output files are turned off, so warn the user
        if (!traceFile && !summaryFile && !reportFile) {
            log.warn('No output files are enabled - to enable, set `enabled: true` in the sections `trace`, `summary` or `report`.')
        }

        // Start hourly CI updating
        timeCiRecordCollector.start()

        // Create trace file
        traceFile?.create()
    }

    /**
     * Save the pending processes and close the files
     */
    void onFlowComplete() {
        log.debug('Workflow completed -- rendering & saving files')

        // Stop hourly CI updating
        timeCiRecordCollector.stop()

        // Compute the statistics (total, mean, min, max, quantiles) on process level
        final Map<String, Map<String, Map<String, ?>>> processStats = aggregator.computeProcessStats()
        // Collect the total sums of all metrics
        final Map<String, Double> totalStats = [:]
        // Iterate over each process and its metrics
        processStats.each { String processName, Map<String, Map<String, ?>> processMetrics ->
            // Iterate over each metric for the current process (e.g., co2e, energy, etc.)
            processMetrics.each { String metricName, Map<String, ?> metricValue ->
                // Extract the 'total' value for the current metric
                Double totalValue = metricValue['total'] as Double
                // If the total value exists, add it to the running sum for this metric
                if (totalValue != null) {
                    // Accumulate the total for each metric across all processes
                    totalStats[metricName] = (totalStats.get(metricName) ?: 0d) + totalValue
                }
            }
        }

        // Catch unfinished tasks
        runningTasks.each { TaskId taskId, TraceRecord traceRecord -> aggregateRecords(traceRecord) }

        // Create report and summary if any content exists to write to the file
        if (totalStats) {
            if (summaryFile) {
                summaryFile.create()
                summaryFile.write(totalStats, co2FootprintComputer, config, version)
                summaryFile.close()
            }

            if (reportFile) {
                reportFile.create()
                reportFile.addEntries(processStats, totalStats, co2FootprintComputer, config, version, session, traceRecords, co2eRecords, timeCiRecordCollector)
                reportFile.write()
                reportFile.close()
            }
        }

        // Close all files (writes remaining tasks in the trace file)
        traceFile?.close(runningTasks)
        summaryFile?.close()
        reportFile?.close()

        log.info(
            "🌱 The workflow run used ${Converter.toReadableUnits(totalStats.get('energy'),'k','Wh')} of electricity, " +
            "resulting in the release of ${Converter.toReadableUnits(totalStats.get('co2e'),'', 'g')} of CO₂ equivalents into the atmosphere."
        )
    }


    // ---- PROCESS LEVEL ----

    /**
     * This method is invoked when a process is created
     *
     * @param process The process created ({@link nextflow.processor.TaskProcessor})
     */
    @Override
    void onProcessCreate(TaskProcessor process) {}

    /**
     * This method is invoked before a process run is going to be submitted.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - submit process > ${handler}")

        startRecord(trace)
    }

    /**
     * This method is invoked when a process run is going to start.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessStart(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - start process > ${handler}")

        startRecord(trace)
    }

    /**
     * This method is invoked when a process run completes.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - complete process > ${handler}")

        // Ensure the presence of a Trace Record
        if (!trace) {
            log.warn("Unable to find TraceRecord for task with id: ${handler.task.id}")
            return
        }

        aggregateRecords(trace)
    }

    /**
     * This method is invoked when a process was cached.
     *
     * @param handler The task handler ({@link nextflow.processor.TaskHandler})
     * @param trace   The trace record for the task ({@link nextflow.trace.TraceRecord})
     */
    @Override
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - cached process > ${handler}")

        // Event was triggered by a stored task, ignore it
        if (trace == null) { return }

        aggregateRecords(trace)
    }

}
