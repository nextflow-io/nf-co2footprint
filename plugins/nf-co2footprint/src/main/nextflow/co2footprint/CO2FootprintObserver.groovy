package nextflow.co2footprint

import groovy.transform.PackageScope
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.co2footprint.Outfiles.CO2FootprintReport
import nextflow.co2footprint.Outfiles.CO2FootprintSummary
import nextflow.co2footprint.Outfiles.CO2FootprintTrace
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

@Slf4j
/**
 * Implements the CO2Footprint observer
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>, Josua Carl <josua.carl@uni-tuebingen.de>
 */
class CO2FootprintObserver implements TraceObserver {

    /**
     * Plugin version
     */
    private String version

    /**
     * Holds workflow session
     */
    private Session session

    /**
     * The path where the files are created. It is set by the object constructor
     */
    private Map<String, Path> paths = [:]

    /**
     * The actual file object
     */
    private CO2FootprintTrace co2eTraceFile
    private CO2FootprintSummary co2eSummaryFile
    private CO2FootprintReport co2eReportFile

    /**
     * Overwrite existing files (required in some cases, as rolling filename has been deprecated)
     */
    private boolean overwrite

    /**
     * Max number of tasks allowed in the report, when they exceed this
     * number the tasks table is omitted
     */
    private int maxTasks

    /**
     * Configuration of the plugin
     */
    CO2FootprintConfig config

    /**
     * Compute resources usage stats
     */
    private CO2FootprintResourcesAggregator aggregator

    /**
     * Computer for the CO2 emissions
     */
    private CO2FootprintComputer co2FootprintComputer

    CO2FootprintComputer getCO2FootprintComputer() { co2FootprintComputer }

    /**
     * Holds the the start time for tasks started/submitted but not yet completed
     */
    @PackageScope
    Map<TaskId, TraceRecord> current = new ConcurrentHashMap<>()

    /**
     * CO2 emission Records with task IDs
     */
    final private Map<TaskId,CO2Record> co2eRecords = new ConcurrentHashMap<>()

    Map<TaskId,CO2Record> getCO2eRecords() { co2eRecords }


    /**
     * Holds tasks with their trace records
     */
    final private Map<TaskId, TraceRecord> traceRecords = new LinkedHashMap<>()

    /**
     * Creates a report observer
     *
     * @param session The current session within which the Observer is called
     * @param version The current version of the plugin
     * @param config The configuration of the Plugin
     * @param co2FootprintComputer The computation instance
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
     * Enables the collection of the task executions metrics in order to be reported in the HTML report
     *
     * @return {@code true}
     */
    @Override
    boolean enableMetrics() { return true }


    /**
     * Set the number max allowed tasks. If this number is exceed the the tasks
     * json in not included in the final report
     *
     * @param value The number of max task record allowed to be included in the HTML report
     * @return The {@link CO2FootprintObserver} itself
     */
    CO2FootprintObserver setMaxTasks(int value) {
        this.maxTasks = value
        return this
    }

    // ------ OBSERVER METHODS ------

    // ---- WORKFLOW LEVEL ----

    /**
     * Start of the workflow; Creates the trace file
     *
     * @param session A {@link nextflow.Session} object representing the current session
     */
    @Override
    void onFlowCreate(Session session) {
        log.debug("Workflow started -- co2e outputs: ${paths}")

        // Construct session and aggregator
        this.session = session
        this.aggregator = new CO2FootprintResourcesAggregator(session)

        // make sure parent paths exists
        paths.each {key, path ->
            Path parent = path.normalize().getParent()
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

        Double total_energy = 0d
        Double total_co2 = 0d
        co2eRecords.values().each {co2Record ->
            total_energy += co2Record.getEnergyConsumption()
            total_co2 += co2Record.getCO2e()
        }

        CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(total_co2)

        // Write report and summary
        co2eSummaryFile.write(total_energy, total_co2, equivalences, config, version)
        co2eReportFile.write(total_energy, total_co2, equivalences, aggregator, config, version, session, traceRecords, co2eRecords)

        // Close all files (writes remaining tasks in the trace file)
        co2eTraceFile.close(current)
        co2eSummaryFile.close()
        co2eReportFile.close()
    }


    // ---- PROCESS LEVEL ----

    /**
     * This method is invoked when a process is created
     *
     * @param process A {@link nextflow.processor.TaskProcessor} object representing the process created
     */
    @Override
    void onProcessCreate(TaskProcessor process) { }


    /**
     * This method is invoked before a process run is going to be submitted
     *
     * @param handler A {@link nextflow.processor.TaskHandler} object representing the task submitted
     * @param trace A {@link nextflow.trace.TraceRecord} object representing current task
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
     * This method is invoked when a process run is going to start
     *
     * @param handler A {@link nextflow.processor.TaskHandler} object representing the task submitted
     * @param trace A {@link nextflow.trace.TraceRecord} object representing current task
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
     * This method is invoked when a process run completes
     *
     * @param handler A {@link nextflow.processor.TaskHandler} object representing the task submitted
     * @param trace A {@link nextflow.trace.TraceRecord} object representing current task
     */
    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - complete process > $handler")
        final TaskId taskId = handler.task.id

        // Ensure the presence of a Trace Record
        if (!trace) {
            log.warn("[WARN] Unable to find TraceRecord for task with id: ${taskId}")
            return
        }

        // remove the record from the current records
        current.remove(taskId)

        // Extract CO2e records
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(taskId, trace)

        // Collect results
        co2eRecords[taskId] = co2Record

        // Aggregate results
        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
            aggregator.aggregate(co2Record, trace.getSimpleName())
        }

        // save to files
        co2eTraceFile.write(taskId, trace, co2Record)
    }

    @Override
    /**
     * This method is invoked when a process was cached
     *
     * @param handler A {@link nextflow.processor.TaskHandler} object representing the task submitted
     * @param trace A {@link nextflow.trace.TraceRecord} object representing current task
     */
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        log.trace("Trace report - cached process > $handler")
        def taskId = handler.task.id

        // event was triggered by a stored task, ignore it
        if (trace == null) { return }

        // Extract records
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(taskId, trace)

        // Collect results
        co2eRecords[taskId] = co2Record

        // Aggregate results
        synchronized (traceRecords) {
            traceRecords[ trace.taskId ] = trace
            aggregator.aggregate(co2Record, trace.getSimpleName())
        }


        // save to the files
        co2eTraceFile.write(taskId, trace, co2Record)
    }


}
