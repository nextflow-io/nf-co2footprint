package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.FileCreators.ReportFile
import nextflow.co2footprint.FileCreators.SummaryFile
import nextflow.co2footprint.FileCreators.TraceFile
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

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

    // Output files
    private final TraceFile traceFile
    private final SummaryFile summaryFile
    private final ReportFile reportFile

    // Plugin configuration
    CO2FootprintConfig config

    // Aggregator for resource usage stats
    private CO2RecordAggregator aggregator

    // Calculator for CO₂ footprint
    private CO2FootprintComputer co2FootprintComputer
    CO2FootprintComputer getCO2FootprintComputer() { co2FootprintComputer }

    // Holds the the start time for tasks started/submitted but not yet completed
    @PackageScope
    Map<TaskId, TraceRecord> submittedTasks = new ConcurrentHashMap<>()

    // Stores CO₂ emission records by task ID
    final private Map<TaskId, CO2Record> co2eRecords = new ConcurrentHashMap<>()
    Map<TaskId,CO2Record> getCO2eRecords() { co2eRecords }

    // Stores all trace records by task ID
    final private Map<TaskId, TraceRecord> traceRecords = new ConcurrentHashMap<>()

    /**
     * Initialize a CO2FootprintObserver. Skips initialization, when all files are disabled.
     *
     * @param session Nextflow session
     * @param version Plugin version
     * @param config Plugin configuration
     * @param co2FootprintComputer CO₂ computation instance
     * @return null, or a CO2FootprintObserver instance
     */
    static CO2FootprintObserver initialize(
            Session session,
            String version,
            CO2FootprintConfig config,
            CO2FootprintComputer co2FootprintComputer
    ){
        // See if any file is enabled
        if (['trace', 'summary', 'report'].any { String fileEntry -> config.get(fileEntry).getEnabled() }) {
            return new CO2FootprintObserver(session, version, config, co2FootprintComputer)
        } else {
            log.error('All output files are disabled (`enabled=false`). Observer not created.')
            return null
        }
    }

    /**
     * Constructor for the observer.
     *
     * @param session Nextflow session
     * @param version Plugin version
     * @param config Plugin configuration
     * @param co2FootprintComputer CO₂ computation instance
     */
    CO2FootprintObserver(
            Session session,
            String version,
            CO2FootprintConfig config,
            CO2FootprintComputer co2FootprintComputer
    ) {
        this.session = session
        this.version = version
        this.config = config
        this.co2FootprintComputer = co2FootprintComputer

        this.traceFile = TraceFile.initialize(config.getTrace())
        this.summaryFile = SummaryFile.initialize(config.getSummary())
        this.reportFile = ReportFile.initialize(config.getReport())
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
     * Start the recording of a trace
     *
     * @param trace Trace Record of started task
     */
    private synchronized void startRecord(TraceRecord trace) {
        // Keep started tasks
        submittedTasks[trace.taskId] = trace

        // Extend trace records
        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
        }
    }

    /**
     * Aggregate the trace and CO2 records
     *
     * @param trace Trace Record of the task to derive all stats from
     * @return co2Record derived from TraceRecord
     */
    private synchronized void aggregateRecords(TraceRecord trace) {
        // Remove the record from the submittedTasks records
        submittedTasks.remove(trace.taskId)

        // Record TraceRecord
        traceRecords[ trace.taskId ] = trace

        // Extract CO2 records
        final CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(trace.taskId, trace)

        // Collect results
        co2eRecords[trace.taskId] = co2Record

        // Aggregate stats
        aggregator.add(trace, co2Record)

        // Save to the files
        this.traceFile?.write(trace.taskId, trace, co2Record)
    }

    // ------ OBSERVER METHODS ------

    // ---- WORKFLOW LEVEL ----

    /**
     * Start of the workflow; Creates the trace file.
     *
     * @param session The submittedTasks Nextflow session
     */
    @Override
    void onFlowCreate(Session session) {
        log.debug('Workflow started')

        // Construct session and aggregator
        this.session = session
        this.aggregator = new CO2RecordAggregator()

        // Create files & parent directories
        this.traceFile?.create()
        this.summaryFile?.create()
        this.reportFile?.create()
    }

    /**
     * Save the pending processes and close the files
     */
    void onFlowComplete() {
        log.debug('Workflow completed -- rendering & saving files')

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
                return
            }
        }

        // Catch unfinished tasks
        submittedTasks.each { TaskId taskId, TraceRecord traceRecord -> aggregateRecords(traceRecord) }

        // Write report and summary
        this.summaryFile?.write(totalStats, co2FootprintComputer, config, version)
        this.reportFile?.addEntries(processStats, totalStats, co2FootprintComputer, config, version, session, traceRecords, co2eRecords)
        this.reportFile?.write()

        // Close all files (writes remaining tasks in the trace file)
        this.traceFile?.close()
        this.summaryFile?.close()
        this.reportFile?.close()
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
        log.trace("Trace report - start process > $handler")

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
        log.trace("Trace report - complete process > $handler")

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
        log.trace("Trace report - cached process > $handler")

        // Event was triggered by a stored task, ignore it
        if (trace == null) { return }

        aggregateRecords(trace)
    }
}
