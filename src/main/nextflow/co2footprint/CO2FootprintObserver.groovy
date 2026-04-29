package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.co2footprint.FileCreation.ProvenanceFileCreator
import nextflow.co2footprint.FileCreation.ReportFileCreator
import nextflow.co2footprint.FileCreation.SummaryFileCreator
import nextflow.co2footprint.FileCreation.TraceFileCreator
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.script.WorkflowMetadata
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
    // Holds workflow session
    Session session

    // Output file objects
    final TraceFileCreator traceFile
    final SummaryFileCreator summaryFile
    final ReportFileCreator reportFile
    final ProvenanceFileCreator provenanceFile

    // Plugin configuration
    CO2FootprintConfig config

    // Calculator for CO₂ footprint
    private CO2FootprintCalculator co2FootprintCalculator
    CO2FootprintCalculator getCO2FootprintCalculator() { co2FootprintCalculator }

    // Record for CI values during execution
    CiRecordCollector timeCiRecordCollector

    // Holds the the start time for tasks started/submitted but not yet completed
    @PackageScope
    Map<TaskId, TraceRecord> runningTasks = new ConcurrentHashMap<>()

    // Hierarchical tree that stores all results and execution traces
    final protected CO2RecordTree workflowStats

    /**
     * Constructor for the observer.
     *
     * @param config Plugin configuration
     * @param co2FootprintCalculator CO₂ computation instance
     */
    CO2FootprintObserver(
            CO2FootprintConfig config,
            CO2FootprintCalculator co2FootprintCalculator
    ) {
        this.config = config

        // Create a CO2RecordTree root node for the run, tagged with 'workflow' level,
        // to collect and organize execution metrics hierarchically.
        this.workflowStats = new CO2RecordTree('Unknown workflow', [level: 'workflow'])

        // Make file instances
        this.traceFile = new TraceFileCreator(config.trace)
        this.summaryFile = new SummaryFileCreator(config.summary)
        this.reportFile = new ReportFileCreator(config.report)
        this.provenanceFile = new ProvenanceFileCreator(config.provenance)

        if (!config.trace.enabled && !config.summary.enabled && !config.report.enabled && !config.provenance.enabled) {
            log.warn('No output files are enabled - to enable, set `enabled: true` in the sections `trace`, `summary` or `report`.')
        }

        this.co2FootprintCalculator = co2FootprintCalculator

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
    synchronized void recordStarted(TraceRecord traceRecord) {
        // Keep started tasks
        runningTasks[traceRecord.taskId] = traceRecord

        // Add a process node under the workflow if it doesn’t exist yet
        if(!workflowStats.getChild(traceRecord.processName)) {
            workflowStats.addChild(new CO2RecordTree(traceRecord.processName, [level: 'process']))
        }
    }

    /**
     * Calculate the CO2 emissions in form of a {@link CO2Record} from a {@link TraceRecord}
     * in conjunction with the current CI Record collector.
     *
     * @param traceRecord the TraceRecord of the task for which the CO2Record should be created
     * @param postRun whether the CO2Record is created post run or during runtime
     */
    CO2Record createCO2Record(TraceRecord traceRecord, boolean postRun=false) {
        return co2FootprintCalculator.computeTaskCO2footprint(traceRecord, timeCiRecordCollector, postRun)
    }

    /**
     * Aggregates the trace and CO₂ records of a finished task.
     *
     * @param trace TraceRecord of the finished task
     * @param postRun whether the aggregation is happening post run or during runtime
     */
    synchronized CO2Record aggregateRecords(TraceRecord traceRecord, boolean postRun=false) {
        // Remove task from set of running tasks
        runningTasks.remove(traceRecord.taskId)

        // Compute CO₂ footprint for this task
        final CO2Record co2Record = createCO2Record(traceRecord, postRun)

        // Optionally write to trace file
        this.traceFile.write(co2Record)

        // Add a task node with its CO2Record to the corresponding process
        CO2RecordTree processNode = workflowStats.getChild(traceRecord.processName)
        processNode.addChild(new CO2RecordTree(traceRecord.taskId, [level: 'task'], co2Record))

        return co2Record
    }

    void renderFiles(CO2RecordTree co2RecordTree=workflowStats, WorkflowMetadata workflowMetadata=session?.workflowMetadata) {
        // Catch unfinished tasks
        runningTasks.each { TaskId taskId, TraceRecord traceRecord -> aggregateRecords(traceRecord) }

        // Close all files (writes remaining tasks in the trace file)
        traceFile.close(runningTasks)

        // Finalize and aggregate all workflow statistics
        co2RecordTree.summarize()
        co2RecordTree.collectAdditionalMetrics()

        // Create report and summary if any content exists to write to the file
        if (workflowStats) {
            summaryFile.create()
            summaryFile.write(co2RecordTree, co2FootprintCalculator, config)

            reportFile.create()
            reportFile.addEntries(co2RecordTree, co2FootprintCalculator, config, timeCiRecordCollector, workflowMetadata)
            reportFile.write()
        }
        if (co2RecordTree) {
            provenanceFile.create()
            provenanceFile.write(co2RecordTree)
        }

        // Close all files (writes remaining tasks in the trace file)
        traceFile.close(runningTasks)
        summaryFile.close()
        reportFile.close()
        provenanceFile.close()
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

        CO2FootprintPlugin co2footprintPlugin = CO2FootprintPlugin.getPlugin()
        co2footprintPlugin?.sessionTraceRecorder?.attachSession(session)

        // Construct session and aggregator
        this.session = session

        this.workflowStats.name = session.runName

        // we wouldn't expect a config where all output files are turned off, so warn the user
        if (!traceFile && !summaryFile && !reportFile) {
            log.warn('No output files are enabled - to enable, set `enabled: true` in the sections `trace`, `summary` or `report`.')
        }

        // Start hourly CI updating
        timeCiRecordCollector.start()

        // Create trace file
        traceFile.create()
    }

    /**
     * Save the pending processes and close the files
     */
    @Override
    void onFlowComplete() {
        log.debug('Workflow completed -- rendering & saving files')

        // Stop hourly CI updating
        timeCiRecordCollector.stop()

        workflowStats.summarize()

        log.info(
            "🌱 The workflow run used ${workflowStats.co2Record.toReadable('energy_consumption')} of electricity, " +
            "resulting in the release of ${workflowStats.co2Record.toReadable('CO2e')} of CO₂ equivalents into the atmosphere."
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
        
        recordStarted(trace) // add also cashed tasks to the runningTasks to be able to report them in the output files
        aggregateRecords(trace)
    }

}
