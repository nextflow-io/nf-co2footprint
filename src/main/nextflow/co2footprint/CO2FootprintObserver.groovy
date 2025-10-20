package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.FileCreation.ReportFileCreator
import nextflow.co2footprint.FileCreation.SummaryFileCreator
import nextflow.co2footprint.FileCreation.TraceFileCreator
import nextflow.co2footprint.Records.CO2RecordTree
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

    // Calculator for CO₂ footprint
    private CO2FootprintComputer co2FootprintComputer
    CO2FootprintComputer getCO2FootprintComputer() { co2FootprintComputer }

    // Record for CI values during execution
    CiRecordCollector timeCiRecordCollector
    CiRecordCollector getTimeCiRecordCollector() { timeCiRecordCollector }

    // Holds the the start time for tasks started/submitted but not yet completed
    @PackageScope
    Map<TaskId, TraceRecord> runningTasks = new ConcurrentHashMap<>()

    // Hierarchical tree that stores all results and execution traces
    final protected CO2RecordTree workflowStats

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

        // Create a CORecordTree root node for the run, tagged with 'workflow' level,
        // to collect and organize execution metrics hierarchically.
        this.workflowStats = new CO2RecordTree(session.runName, [level: 'workflow'])

        // Make file instances
        this.traceFile = new TraceFileCreator((config.value('traceFile') as Path).complete(), overwrite)
        this.summaryFile = new SummaryFileCreator((config.value('summaryFile') as Path).complete(), overwrite)
        this.reportFile = new ReportFileCreator((config.value('reportFile') as Path).complete(), overwrite, maxTasks)

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
    private synchronized void recordStarted(TraceRecord traceRecord) {
        // Keep started tasks
        runningTasks[traceRecord.taskId] = traceRecord

        // Add a process node under the workflow if it doesn’t exist yet
        if(!workflowStats.getChild(traceRecord.processName)) {
            workflowStats.addChild(new CO2RecordTree(traceRecord.processName, [level: 'process']))
        }
    }

    /**
     * Aggregates the trace and CO₂ records of a finished task.
     *
     * @param trace TraceRecord of the finished task
     */
    private synchronized void aggregateRecords(TraceRecord traceRecord) {
        // Remove task from set of running tasks
        runningTasks.remove(traceRecord.taskId)

        // Compute CO₂ footprint for this task
        final CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(traceRecord, timeCiRecordCollector)

        // Optionally write to trace file
        this.traceFile?.write(co2Record)

        // Add a task node with its CO2Record to the corresponding process
        CO2RecordTree processNode = workflowStats.getChild(traceRecord.processName)
        processNode.addChild(new CO2RecordTree(traceRecord.taskId, [level: 'task'], co2Record))
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

        // Start hourly CI updating
        timeCiRecordCollector.start()

        // Create trace file
        traceFile.create()
    }

    /**
     * Save the pending processes and close the files
     */
    void onFlowComplete() {
        log.debug('Workflow completed -- rendering & saving files')

        // Stop hourly CI updating
        timeCiRecordCollector.stop()

        // Catch unfinished tasks
        runningTasks.each { TaskId taskId, TraceRecord traceRecord -> aggregateRecords(traceRecord) }

        // Close all files (writes remaining tasks in the trace file)
        traceFile.close(runningTasks)

        workflowStats.summarize()
        workflowStats.collectAdditionalMetrics()

        // Create report and summary if any content exists to write to the file
        if (workflowStats) {
            summaryFile.create()
            reportFile.create()

            // Write report and summary
            summaryFile.write(workflowStats, co2FootprintComputer, config, version)

            reportFile.addEntries(workflowStats, co2FootprintComputer, config, version, session, timeCiRecordCollector)
            reportFile.write()

        }
        summaryFile.close()
        reportFile.close()

        log.info(
            "🌱 The workflow run used ${workflowStats.co2Record.getReadable('energy')} of electricity, " +
            "resulting in the release of ${workflowStats.co2Record.getReadable('co2e')} of CO₂ equivalents into the athmosphere."
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

        recordStarted(trace)
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

        recordStarted(trace)
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

        // Ensure the presence of a Trace BaseRecord
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
