/*
 * Copyright 2021, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.co2footprint

import nextflow.co2footprint.utils.DeduplicateMarkerFilter
import nextflow.co2footprint.utils.Markers
import nextflow.co2footprint.utils.Converter

import groovy.text.GStringTemplateEngine
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovyx.gpars.agent.Agent

import nextflow.processor.TaskHandler
import nextflow.processor.TaskProcessor
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceHelper
import nextflow.trace.TraceRecord

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import nextflow.processor.TaskId

import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.turbo.TurboFilter

import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements the CO2Footprint observer factory
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
@Slf4j
@CompileStatic
@PackageScope(PackageScopeTarget.FIELDS)
class CO2FootprintFactory implements TraceObserverFactory {

    private String version

    /**
     * Logging:
     * Removes duplicates in some warnings, to avoid cluttering the output with repeated information.
     * Example: If the CPU model is not found it should only be warned once, that a fallback value is used.
     */
    static {
        LoggerContext lc = LoggerFactory.getILoggerFactory() as LoggerContext   // Get Logging Context
        TurboFilter dmf = new DeduplicateMarkerFilter([Markers.unique])         // Define DeduplicateMarkerFilter
        dmf.start()
        lc.addTurboFilter(dmf)                                                  // Add filter to context
    }

    private CO2FootprintConfig config
    private Session session
    final private Map<TaskId,CO2Record> co2eRecords = new ConcurrentHashMap<>()

    Double total_energy = 0
    Double total_co2 = 0

    protected void getPluginVersion() {
        def reader = new InputStreamReader(this.class.getResourceAsStream('/META-INF/MANIFEST.MF'))
        String line
        while ( (line = reader.readLine()) && !version ) {
            def h = line.split(": ")
            if ( h[0] == 'Plugin-Version' ) this.version = h[1]
        }
        reader.close()
    }

    /**
     * External Data integration of TDP (Thermal design power) and CI (Carbon intensity) values
     */
    private final TDPDataMatrix tdpDataMatrix = TDPDataMatrix.loadCsv(
            Paths.get(this.class.getResource('/CPU_TDP.csv').toURI())
    )

    private final CIDataMatrix ciDataMatrix = null

    private CO2FootprintComputer co2FootprintComputer
    CO2FootprintComputer getCO2FootprintComputer() { co2FootprintComputer }

    @Override
    Collection<TraceObserver> create(Session session) {
        getPluginVersion()
        log.info "nf-co2footprint plugin  ~  version ${this.version}"

        this.session = session
        this.config = new CO2FootprintConfig(
                session.config.navigate('co2footprint') as Map,
                this.tdpDataMatrix
        )

        co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)

        final result = new ArrayList(2)

        // Generate CO2 footprint text output files
        def co2eTraceFile = (this.config.getTraceFile() as Path).complete()
        def co2eSummaryFile = (this.config.getSummaryFile() as Path).complete()

        result.add( new CO2FootprintTextFileObserver(co2eTraceFile, co2eSummaryFile) )

        // Generate CO2 footprint report with box-plot
        def co2eReport = (this.config.getReportFile() as Path).complete()
        result.add( new CO2FootprintReportObserver(co2eReport) )

        return result
    }

    /**
     * Class to generate text output
     */
    class CO2FootprintTextFileObserver implements TraceObserver {

        /**
         * Overwrite existing trace file (required in some cases, as rolling filename has been deprecated)
         */
        boolean overwrite = true

        /**
         * The path where the files are created. It is set by the object constructor
         */
        private Path co2eTracePath
        private Path co2eSummaryPath

        /**
         * The actual file object
         */
        private PrintWriter co2eTraceFile
        private PrintWriter co2eSummaryFile


        /**
         * Holds the the start time for tasks started/submitted but not yet completed
         */
        @PackageScope
        Map<TaskId, TraceRecord> current = new ConcurrentHashMap<>()


        private Agent<PrintWriter> traceWriter
        private Agent<PrintWriter> summaryWriter


        /**
         * Create the trace observer
         *
         * @param co2eTraceFile A path to the file where save the CO2 emission data
         */
        CO2FootprintTextFileObserver(Path co2eTraceFile, Path co2eSummaryFile) {
            this.co2eTracePath = co2eTraceFile
            this.co2eSummaryPath = co2eSummaryFile
        }

        /**
         * Create the trace file, in file already existing with the same name it is
         * "rolled" to a new file
         */
        @Override
        void onFlowCreate(Session session) {
            log.debug "Workflow started -- co2e traceFile: ${co2eTracePath.toUriString()}"

            // make sure parent path exists
            def parent = co2eTracePath.normalize().getParent()
            if (parent)
                Files.createDirectories(parent)

            def summaryParent = co2eSummaryPath.normalize().getParent()
            if (summaryParent)
                Files.createDirectories(summaryParent)

            // create a new trace file
            co2eTraceFile = new PrintWriter(TraceHelper.newFileWriter(co2eTracePath, overwrite, 'co2footprint'))

            // launch the agent
            traceWriter = new Agent<PrintWriter>(co2eTraceFile)

            List<String> headers = [
                    'task_id', 'status', 'name', 'energy_consumption', 'CO2e', 'time', 'cpus', 'powerdraw_cpu',
                    'cpu_model', 'cpu_usage', 'requested_memory'
            ]
            traceWriter.send {
                co2eTraceFile.println( String.join('\t', headers) )
                co2eTraceFile.flush()
            }
        }

        /**
         * Save the pending processes and close the trace file
         */
        @Override
        void onFlowComplete() {
            log.debug "Workflow completed -- saving trace and summary file"

            // wait for termination and flush the agent content
            traceWriter.await()

            // create a summary trace file
            co2eSummaryFile = new PrintWriter(TraceHelper.newFileWriter(co2eSummaryPath, overwrite, 'co2footprintsummary'))

            // launch the agent
            summaryWriter = new Agent<PrintWriter>(co2eSummaryFile)

            co2eSummaryFile.println("Total CO2e footprint measures of this workflow run")
            co2eSummaryFile.println("CO2e emissions: ${Converter.toReadableUnits(total_co2,3, 'g')}")
            co2eSummaryFile.println("Energy consumption: ${Converter.toReadableUnits(total_energy,3, 'Wh')}")

            CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(total_co2)
            List<String> readableEquivalences = equivalences.getReadableEquivalences()
            if (readableEquivalences.any()) {
                co2eSummaryFile.println("\nWhich equals: ")
                for (var readableEquivalence : readableEquivalences) {
                   co2eSummaryFile.println(readableEquivalence)
                }
            }

            co2eSummaryFile.println("\nThe calculation of these values is based on the carbon footprint computation method developed in the Green Algorithms project.")
            co2eSummaryFile.println("Lannelongue, L., Grealey, J., Inouye, M., Green Algorithms: Quantifying the Carbon Footprint of Computation. Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707")
            co2eSummaryFile.println()
            co2eSummaryFile.println("nf-co2footprint plugin version: ${version}")
            co2eSummaryFile.println()
            co2eSummaryFile.println("nf-co2footprint options")
            config.collectInputFileOptions().each { co2eSummaryFile.println("${it.key}: ${it.value}") }
            config.collectOutputFileOptions().each { co2eSummaryFile.println("${it.key}: ${it.value}") }
            config.collectCO2CalcOptions().each { co2eSummaryFile.println("${it.key}: ${it.value}") }
            co2eSummaryFile.flush()
            co2eSummaryFile.close()

            // write the remaining records
            current.values().each { co2eTraceFile.println("${it.taskId}\t-") }
            co2eTraceFile.flush()
            co2eTraceFile.close()
        }

        @Override
        void onProcessCreate(TaskProcessor process) {

        }

        /**
         * This method is invoked before a process run is going to be submitted
         * @param handler
         */
        @Override
        void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
            current[trace.taskId] = trace
        }

        /**
         * This method is invoked when a process run is going to start
         * @param handler
         */
        @Override
        void onProcessStart(TaskHandler handler, TraceRecord trace) {
            current[trace.taskId] = trace
        }

        /**
         * This method is invoked when a process run completes
         * @param handler
         */
        @Override
        void onProcessComplete(TaskHandler handler, TraceRecord trace) {
            final taskId = handler.task.id
            if (!trace) {
                log.debug "[WARN] Unable to find record for task run with id: ${taskId}"
                return
            }

            // remove the record from the current records
            current.remove(taskId)

            // Extract record
            CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(taskId, trace)
            total_energy += co2Record.getEnergyConsumption()
            total_co2 += co2Record.getCO2e()
            co2eRecords[taskId] = co2Record

            // save to the file
            List<String> co2RecordEntries = co2Record.getReadableEntries()
            co2RecordEntries = [taskId as String, trace.get('status') as String] + co2RecordEntries
            traceWriter.send { PrintWriter writer ->
                writer.println( String.join('\t', co2RecordEntries) )
                writer.flush()
            }
        }

        @Override
        void onProcessCached(TaskHandler handler, TraceRecord trace) {
            def taskId = handler.task.id
            // event was triggered by a stored task, ignore it
            if (trace == null) { return }

            // Extract record
            CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(taskId, trace)
            total_energy += co2Record.getEnergyConsumption()
            total_co2 += co2Record.getCO2e()
            co2eRecords[taskId] = co2Record

            // save to the file
            List<String> co2RecordEntries = co2Record.getReadableEntries()
            co2RecordEntries = [taskId as String, trace.get('status') as String] + co2RecordEntries
            traceWriter.send { PrintWriter writer ->
                writer.println( String.join('\t', co2RecordEntries) )
                writer.flush()
            }
        }
    }


    /**
     * Class to generate HTML report with box-plots
     */
    class CO2FootprintReportObserver implements TraceObserver {

        static final public int DEF_MAX_TASKS = 10_000

        /**
         * Holds the the start time for tasks started/submitted but not yet completed
         */
        final private Map<TaskId, TraceRecord> records = new LinkedHashMap<>()

        /**
         * Holds workflow session
         */
        private Session session

        /**
         * The path the HTML report file created
         */
        private Path reportFile

        /**
         * Max number of tasks allowed in the report, when they exceed this
         * number the tasks table is omitted
         */
        private int maxTasks = DEF_MAX_TASKS

        /**
         * Compute resources usage stats
         */
        private CO2FootprintResourcesAggregator aggregator

        /**
         * Overwrite existing trace file (required in some cases, as rolling filename has been deprecated)
         */
        boolean overwrite

        /**
         * Creates a report observer
         *
         * @param file The file path where to store the resulting HTML report document
         */
        CO2FootprintReportObserver(Path file ) {
            this.reportFile = file
        }

        /**
         * Enables the collection of the task executions metrics in order to be reported in the HTML report
         *
         * @return {@code true}
         */
        @Override
        boolean enableMetrics() {
            return true
        }

        /**
         * @return The {@link nextflow.script.WorkflowMetadata} object associated to this execution
         */
        protected WorkflowMetadata getWorkflowMetadata() {
            session.getWorkflowMetadata()
        }

        /**
         * @return The map of collected {@link TraceRecord}s
         */
        protected Map<TaskId,TraceRecord> getRecords() {
            records
        }

        /**
         * @return The map of collected {@link CO2Record}s
         */
        protected Map<TaskId,CO2Record> getCO2Records() {
            co2eRecords
        }

        /**
         * Set the number max allowed tasks. If this number is exceed the the tasks
         * json in not included in the final report
         *
         * @param value The number of max task record allowed to be included in the HTML report
         * @return The {@link CO2FootprintReportObserver} itself
         */
        CO2FootprintReportObserver setMaxTasks(int value ) {
            this.maxTasks = value
            return this
        }

        /**
         * Create the trace file, in file already existing with the same name it is
         * "rolled" to a new file
         */
        @Override
        void onFlowCreate(Session session) {
            this.session = session
            this.aggregator = new CO2FootprintResourcesAggregator(session)
        }

        /**
         * Save the pending processes and close the trace file
         */
        @Override
        void onFlowComplete() {
            log.debug "Workflow completed -- rendering CO2e footprint report"
            try {
                renderHtml()
            }
            catch (Exception e) {
                log.warn "Failed to render CO2e footprint report -- see the log file for details", e
            }
        }

        /**
         * This method is invoked when a process is created
         *
         * @param process A {@link TaskProcessor} object representing the process created
         */
        @Override
        void onProcessCreate(TaskProcessor process) { }


        /**
         * This method is invoked before a process run is going to be submitted
         *
         * @param handler A {@link TaskHandler} object representing the task submitted
         */
        @Override
        void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
            log.trace "Trace report - submit process > $handler"
            synchronized (records) {
                records[ trace.taskId ] = trace
            }
        }

        /**
         * This method is invoked when a process run is going to start
         *
         * @param handler  A {@link TaskHandler} object representing the task started
         */
        @Override
        void onProcessStart(TaskHandler handler, TraceRecord trace) {
            log.trace "Trace report - start process > $handler"
            synchronized (records) {
                records[ trace.taskId ] = trace
            }
        }

        /**
         * This method is invoked when a process run completes
         *
         * @param handler A {@link TaskHandler} object representing the task completed
         */
        @Override
        void onProcessComplete(TaskHandler handler, TraceRecord trace) {
            log.trace "Trace report - complete process > $handler"
            if( !trace ) {
                log.debug "WARN: Unable to find trace record for task id=${handler.task?.id}"
                return
            }

            synchronized (records) {
                records[ trace.taskId ] = trace
                aggregate(co2eRecords[ trace.taskId ], trace.getSimpleName())
            }
        }

        /**
         * This method is invoked when a process run cache is hit
         *
         * @param handler A {@link TaskHandler} object representing the task cached
         */
        @Override
        void onProcessCached(TaskHandler handler, TraceRecord trace) {
            log.trace "Trace report - cached process > $handler"

            // event was triggered by a stored task, ignore it
            if( trace == null ) {
                return
            }

            // remove the record from the current records
            synchronized (records) {
                records[ trace.taskId ] = trace
                aggregate(co2eRecords[ trace.taskId ], trace.getSimpleName())
            }
        }

        /**
         * Aggregates task record for each process in order to render the
         * final execution stats
         *
         * @param record A {@link TraceRecord} object representing a task executed
         */
        protected void aggregate(CO2Record co2record, String process) {
            aggregator.aggregate(co2record, process)
        }

        /**
         * @return The tasks json payload
         */
        protected String renderTasksJson() {
            final r = getRecords()
            final co2r = getCO2Records()
            co2r.size()<=maxTasks ? renderJsonData(r.values(), co2r) : 'null'
        }

        protected String renderSummaryJson() {
            final result = aggregator.renderSummaryJson()
            log.debug "Execution report summary data:\n  ${result}"
            return result
        }

        protected String renderPayloadJson() {
            "{ \"trace\":${renderTasksJson()}, \"summary\":${renderSummaryJson()} }"
        }

        /**
         * @return The options json payload
         */
        protected String renderOptionsJson() {
            final all_options = config.collectInputFileOptions() + config.collectOutputFileOptions() + config.collectCO2CalcOptions()
            def result = new StringBuilder()
            result << "["
            def fields = all_options.keySet() as List
            
            // Render JSON
            final QUOTE = '"'
            for( int i=0; i<fields.size(); i++ ) {
                if(i) result << ','
                String name = fields[i]
                String value = all_options[name].toString()
                result << "{" << QUOTE << "option" << QUOTE << ":" << QUOTE << name << QUOTE << ","
                result << QUOTE << "value" << QUOTE << ":" << QUOTE << value << QUOTE << "}"
            }
            result << "]"

            return result.toString()
        }

        /**
         * Render the total co2 footprint values for html report
         *
         * @param data A collection of {@link TraceRecord}s representing the tasks executed
         * @param dataCO2 A collection of {@link CO2Record}s representing the tasks executed
         * @return The rendered json
         */
        protected Map renderCO2TotalsJson() {
            CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(total_co2)
            [ co2:Converter.toReadableUnits(total_co2,3),
              energy:Converter.toReadableUnits(total_energy,3),
              car: equivalences.getCarKilometersReadable(),
              tree: equivalences.getTreeMonthsReadable(),
              plane_percent: equivalences.getPlanePercentReadable(),
              plane_flights: equivalences.getPlaneFlightsReadable()
            ]
        }

        /**
         * Render the report HTML document
         */
        protected void renderHtml() {
            // render HTML report template
            final tpl_fields = [
                    workflow : getWorkflowMetadata(),
                    payload : renderPayloadJson(),
                    co2_totals: renderCO2TotalsJson(),
                    plugin_version: version,
                    assets_css : [
                            readTemplate('nextflow/trace/assets/bootstrap.min.css'),
                            readTemplate('nextflow/trace/assets/datatables.min.css')
                    ],
                    assets_js : [
                            readTemplate('nextflow/trace/assets/jquery-3.2.1.min.js'),
                            readTemplate('nextflow/trace/assets/popper.min.js'),
                            readTemplate('nextflow/trace/assets/bootstrap.min.js'),
                            readTemplate('nextflow/trace/assets/datatables.min.js'),
                            readTemplate('nextflow/trace/assets/moment.min.js'),
                            readTemplate('nextflow/trace/assets/plotly.min.js'),
                            readTemplate('assets/CO2FootprintReportTemplate.js')
                    ],
                    options : renderOptionsJson()
            ]
            //log.info "${tpl_fields['payload']}"
            final tpl = readTemplate('CO2FootprintReportTemplate.html')
            def engine = new GStringTemplateEngine()
            def html_template = engine.createTemplate(tpl)
            def html_output = html_template.make(tpl_fields).toString()

            // make sure the parent path exists
            def parent = reportFile.getParent()
            if( parent )
                Files.createDirectories(parent)

            def writer = TraceHelper.newFileWriter(reportFile, overwrite, 'Report')
            writer.withWriter { w -> w << html_output }
            writer.close()
        }

        /**
         * Render the executed tasks json payload
         *
         * @param data A collection of {@link TraceRecord}s representing the tasks executed
         * @param dataCO2 A collection of {@link CO2Record}s representing the tasks executed
         * @return The rendered json payload
         */
        protected String renderJsonData(Collection<TraceRecord> data, Map<TaskId,CO2Record> dataCO2) {
            List<String> formats = null
            List<String> fields = null
            List<String> co2Formats = null
            List<String> co2Fields = null
            def result = new StringBuilder()
            result << '[\n'
            for (int i = 0; i < data.size(); i++) {
                if( i ) result << ','
                if( !formats ) formats = TraceRecord.FIELDS.values().collect { it!='str' ? 'num' : 'str' }
                if( !fields ) fields = TraceRecord.FIELDS.keySet() as List
                data[i].renderJson(result,fields,formats)
                if( !co2Formats ) co2Formats = CO2Record.FIELDS.values().collect { it!='str' ? 'num' : 'str' }
                if( !co2Fields ) co2Fields = CO2Record.FIELDS.keySet() as List
                dataCO2[data[i].getTaskId()].renderJson(result,co2Fields,co2Formats)
            }
            result << ']'
            return result.toString()
        }

        /**
         * Read the document HTML template from the application classpath
         *
         * @param path A resource path location
         * @return The loaded template as a string
         */
        private String readTemplate( String path ) {
            StringWriter writer = new StringWriter()
            def res =  this.class.getClassLoader().getResourceAsStream( path )
            int ch
            while( (ch=res.read()) != -1 ) {
                writer.append(ch as char)
            }
            writer.toString()
        }

    }

}
