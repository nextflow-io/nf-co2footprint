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

import nextflow.co2footprint.utils.HelperFunctions

import groovy.text.GStringTemplateEngine
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.utils.DataMatrix

import nextflow.processor.TaskHandler
import nextflow.processor.TaskProcessor
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceHelper
import nextflow.trace.TraceRecord

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import nextflow.processor.TaskId

import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean

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
    // Handle logging messages
    private List<String> warnings = []

    boolean hasWarnings() { warnings.size() > 0 }
    List<String> getWarnings() { warnings }

    private CO2FootprintConfig config
    private Session session
    final private Map<TaskId,CO2Record> co2eRecords = new ConcurrentHashMap<>()
    // TODO make sure for key value can be set only once?

    private Map<String, Double> cpuData = [:]
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

    // Load file containing TDP values for different CPU models
    protected TDPDataMatrix loadTDPData() {
        DataMatrix dm = DataMatrix.loadCsv(
                Paths.get(this.class.getResource('/CPU_TDP.csv').toURI()),
                ',', 0, null, 'name'
        )
        return new TDPDataMatrix(
                dm.getData(), dm.getOrderedColumnKeys(), dm.getOrderedRowKeys(),
                'default', null, null, null
        )
    }
    TDPDataMatrix tdpDataMatrix = loadTDPData()

    @Override
    Collection<TraceObserver> create(Session session) {
        getPluginVersion()
        log.info "nf-co2footprint plugin  ~  version ${this.version}"

        this.session = session
        this.config = new CO2FootprintConfig(session.config.navigate('co2footprint') as Map, this.cpuData)

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

    
    Double getCPUCoreTDP(TraceRecord trace, String cpu_model=null) {
        cpu_model = cpu_model ?: trace.get('cpu_model').toString()

        TDPDataMatrix modelDataMatrix
        if ( cpu_model == null || cpu_model == "null" ) {
            warnings << "The CPU model could not be detected for at least one task. Using default CPU power draw value!"
            modelDataMatrix = tdpDataMatrix.matchModel('default')
        } else {
            modelDataMatrix = tdpDataMatrix.matchModel(cpu_model)
        }
        return modelDataMatrix.getCoreTDP()
    }


    // Core function to compute CO2 emissions for each task
    List<Double> computeTaskCO2footprint(TraceRecord trace) {
        // C = t * (nc * Pc * uc + nm * Pm) * PUE * CI * 0.001
        // as in https://doi.org/10.1002/advs.202100707
        // PSF: pragmatic scaling factor -> not used here since we aim at the CO2e of one pipeline run
        // Factor 0.001 needed to convert Pc and Pm from W to kW


        // Detect OS
        OperatingSystemMXBean OS = { (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean() }()
        // Total Memory
        Double max_memory = OS.getTotalMemorySize() as Double

        // t: runtime in hours
        Double realtime = trace.get('realtime') as Double
        Double t = realtime/3600000 as Double

        /**
         * Factors of core power usage
         */
        // nc: number of cores
        Double nc = trace.get('cpus') as Integer

        // Pc: power draw of a computing core  [W]
        Double pc = config.getIgnoreCpuModel() ? getCPUCoreTDP(null, 'default') : getCPUCoreTDP(trace)

        // uc: core usage factor (between 0 and 1)
        // TODO if requested more than used, this is not taken into account, right?
        Double cpu_usage = trace.get('%cpu') as Double
        if ( cpu_usage == null ) {
            warnings << "The reported CPU usage is null for at least one task. Assuming 100% usage for each requested CPU!"
            // TODO why is value null, because task was finished so fast that it was not captured? Or are there other reasons?
            // Assuming requested cpus were used with 100%
            cpu_usage = nc * 100
        }
        // TODO how to handle double, Double datatypes for ceiling?
        if ( cpu_usage == 0.0 ) {
            warnings << "The reported CPU usage is 0.0 for at least one task!"
        }
        Double uc = cpu_usage / (100.0 * nc) as Double

        /**
         * Factors of memory power usage
         */
        // nm: size of memory available [GB] -> requested memory
        Double memory = trace.get('memory') as Double
        if ( memory == null || trace.get('peak_rss') as Double > memory) {
            warnings << "The required memory exceeds user requested memory, therefore setting to maximum available memory!"
            memory = max_memory
        }

        Double nm = memory/1000000000 as Double
        // TODO handle if more memory/cpus used than requested?

        // Pm: power draw of memory [W per GB]
        Double pm  = config.getPowerdrawMem()

        /**
         * Remaining factors
         */
        // PUE: efficiency coefficient of the data centre
        Double pue = config.getPue()
        // CI: carbon intensity [gCO2e kWh−1]
        Double ci  = config.getCi()

        /**
         * Calculate energy consumption [kWh]
         */
        Double e = (t * (nc * pc * uc + nm * pm) * pue * 0.001) as Double

        /*
         * Resulting CO2 emission [gCO2e]
         */
        Double c = (e * ci)

        // Return values in mWh and mg
        e = e * 1000000
        c = c * 1000

        // TODO: Only a workaround. Like this the memory is only full precision until 999TB of GB.
        // The cast is still necessary, as the output expects a List<Double> which worked with Groovy3 but not Groovy4
        Double mem_double = memory as Double

        return [e, c, realtime, nc, pc, uc, mem_double]
    }


    // Compute CO2 footprint equivalences
    List<Double> computeCO2footprintEquivalences() {
        /*
         * The following values were taken from the Green Algorithms publication (https://doi.org/10.1002/advs.202100707):
         * The estimated emission of the average passenger car is 175 gCO2e/Km in Europe and 251 gCO2/Km in the US
         * The estimated emission of flying on a jet aircraft in economy class is between 139 and 244 gCO2e/Km
         * The estimated sequestered CO2 of a mature tree is ~1 Kg per month (917 g)
         * A reference flight Paris to London spends 50000 gCO2
         */
        def gCO2 = total_co2 / 1000 as Double
        String location = config.getLocation()
        Double car = gCO2 / 175 as Double
        if (location && (location != 'US' || !location.startsWith('US-'))) {
            car = gCO2 / 251 as Double
        }
        Double tree = gCO2 / 917 as Double
        Double plane_percent = null
        Double plane_flights = null
        if (gCO2 <= 50000) {
            plane_percent = gCO2 * 100 / 50000 as Double
        } else {
            plane_flights = gCO2 / 50000 as Double
        }

        return [car, tree, plane_percent, plane_flights]
    }


    /**
     * Class to generate text output
     */
    class CO2FootprintTextFileObserver implements TraceObserver {

        // TODO which files should we generate here?
        public static final String DEF_TRACE_FILE_NAME = "co2footprint_trace_${TraceHelper.launchTimestampFmt()}.txt"
        public static final String DEF_SUMMARY_FILE_NAME = "co2footprint_summary_${TraceHelper.launchTimestampFmt()}.txt"

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

        /** ONLY FOR TESTING PURPOSE */
        protected CO2FootprintTextFileObserver() {}


        /**
         * Create the trace file, in file already existing with the same name it is
         * "rolled" to a new file
         */
        @Override
        void onFlowCreate(Session session) {
            log.debug "Workflow started -- co2e traceFile: ${co2eTracePath.toUriString()}"

            // make sure parent path exists
            def parent = co2eTracePath.getParent()
            if (parent)
                Files.createDirectories(parent)

            def summaryParent = co2eSummaryPath.getParent()
            if (summaryParent)
                Files.createDirectories(summaryParent)

            // create a new trace file
            co2eTraceFile = new PrintWriter(TraceHelper.newFileWriter(co2eTracePath, overwrite, 'co2footprint'))

            // launch the agent
            traceWriter = new Agent<PrintWriter>(co2eTraceFile)

            String cpu_model_string = config.getIgnoreCpuModel()? "" : "cpu_model\t"
            traceWriter.send { co2eTraceFile.println(
                    "task_id\t"
                    + "name\t"
                    + "status\t"
                    + "energy_consumption\t"
                    + "CO2e\t"
                    + "time\t"
                    + "cpus\t"
                    + "powerdraw_cpu\t"
                    + cpu_model_string
                    + "cpu_usage\t"
                    + "requested_memory"
                ); co2eTraceFile.flush()
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
            co2eSummaryFile.println("CO2e emissions: ${HelperFunctions.convertToReadableUnits(total_co2,3)}g")
            co2eSummaryFile.println("Energy consumption: ${HelperFunctions.convertToReadableUnits(total_energy,3)}Wh")

            List equivalences = computeCO2footprintEquivalences()
            List<GString> readableEquivalences = new ArrayList<GString>()
            if (equivalences[0]){
                readableEquivalences.add("- ${HelperFunctions.convertToScientificNotation(equivalences[0])} km travelled by car")
            }
            if (equivalences[1]){
                readableEquivalences.add("- Monthly co2 absorption of ${HelperFunctions.convertToScientificNotation(equivalences[1])} trees")
            }
            if (equivalences[2]){
                readableEquivalences.add("- ${HelperFunctions.convertToScientificNotation(equivalences[2])}% of a flight from paris to london")
            }
            if (equivalences[3]){
                readableEquivalences.add("- ${HelperFunctions.convertToScientificNotation(equivalences[3])} flights from paris to london")
            }
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

            // Log warnings
            if( hasWarnings() ) {
                def filteredWarnings = getWarnings().unique( false )
                def msg = "\033[0;33mThe nf-co2footprint plugin generated the following warnings during the execution of the workflow:\n\t- " + filteredWarnings.join('\n\t- ').trim() + "\n\033[0m"
                log.warn(msg)
            }
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
        // TODO write footprint for each process?
        @Override
        void onProcessComplete(TaskHandler handler, TraceRecord trace) {
            final taskId = handler.task.id
            if (!trace) {
                log.debug "[WARN] Unable to find record for task run with id: ${taskId}"
                return
            }

            // remove the record from the current records
            current.remove(taskId)

            // compute the CO2 footprint
            def computation_results = computeTaskCO2footprint(trace)
            def eConsumption = computation_results[0]
            def co2 = computation_results[1]
            def time = computation_results[2]
            def cpus = computation_results[3] as Integer
            def powerdrawCPU = computation_results[4]
            def cpu_usage = computation_results[5]
            def memory = computation_results[6]

            co2eRecords[taskId] = new CO2Record(
                    (Double) eConsumption,
                    (Double) co2,
                    (Double) time,
                    cpus,
                    (Double) powerdrawCPU,
                    (Double) cpu_usage,
                    (Long) memory,
                    trace.get('name').toString(),
                    config.getIgnoreCpuModel() ? "" : trace.get('cpu_model').toString()
            )
            total_energy += eConsumption
            total_co2 += co2

            // save to the file
            String cpu_model_string = config.getIgnoreCpuModel()? "" : "${trace.get('cpu_model').toString()}\t"
            traceWriter.send {
                PrintWriter it -> it.println(
                        "${taskId}\t"
                        + "${trace.get('name').toString()}\t"
                        + "${trace.get('status').toString()}\t"
                        + "${HelperFunctions.convertToReadableUnits(eConsumption,3)}Wh\t"
                        + "${HelperFunctions.convertToReadableUnits(co2,3)}g\t"
                        + "${HelperFunctions.convertMillisecondsToReadableUnits(time)}\t"
                        + "${cpus}\t"
                        + "${powerdrawCPU}\t"
                        + cpu_model_string
                        + "${cpu_usage}\t"
                        + "${HelperFunctions.convertBytesToReadableUnits(memory)}"
                )
                it.flush()
            }
        }


        @Override
        void onProcessCached(TaskHandler handler, TraceRecord trace) {
            def taskId = handler.task.id    // TODO "final" or "def"?
            // event was triggered by a stored task, ignore it
            if (trace == null) {
                return
            }

            // compute the CO2 footprint
            def computation_results = computeTaskCO2footprint(trace)
            def eConsumption = computation_results[0]
            def co2 = computation_results[1]
            def time = computation_results[2]
            def cpus = computation_results[3] as Integer
            def powerdrawCPU = computation_results[4]
            def cpu_usage = computation_results[5]
            def memory = computation_results[6]

            co2eRecords[taskId] = new CO2Record(
                    (Double) eConsumption,
                    (Double) co2,
                    (Double) time,
                    cpus,
                    (Double) powerdrawCPU,
                    (Double) cpu_usage,
                    (Long) memory,
                    trace.get('name').toString(),
                    config.getIgnoreCpuModel() ? "" : trace.get('cpu_model').toString()
            )
            total_energy += eConsumption
            total_co2 += co2

            // save to the file
            String cpu_model_string = config.getIgnoreCpuModel()? "" : "${trace.get('cpu_model').toString()}\t"
            traceWriter.send {
                PrintWriter it -> it.println(
                        "${taskId}\t"
                        + "${trace.get('name').toString()}\t"
                        + "${trace.get('status').toString()}\t"
                        + "${HelperFunctions.convertToReadableUnits(eConsumption,3)}Wh\t"
                        + "${HelperFunctions.convertToReadableUnits(co2,3)}g\t"
                        + "${HelperFunctions.convertMillisecondsToReadableUnits(time)}\t"
                        + "${cpus}\t"
                        + "${powerdrawCPU}\t"
                        + cpu_model_string
                        + "${cpu_usage}\t"
                        + "${HelperFunctions.convertBytesToReadableUnits(memory)}"
                )
                it.flush()
            }
        }
    }


    /**
     * Class to generate HTML report with box-plots
     */
    class CO2FootprintReportObserver implements TraceObserver {

        static final public String DEF_REPORT_FILE_NAME = "co2footprint_report_${TraceHelper.launchTimestampFmt()}.html"

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
            List equivalences = computeCO2footprintEquivalences()
            [ co2:HelperFunctions.convertToReadableUnits(total_co2,3), 
              energy:HelperFunctions.convertToReadableUnits(total_energy,3),
              car: equivalences[0]?HelperFunctions.convertToScientificNotation(equivalences[0]):equivalences[0],
              tree: equivalences[1]?HelperFunctions.convertToScientificNotation(equivalences[1]):equivalences[1],
              plane_percent: equivalences[2]?HelperFunctions.convertToScientificNotation(equivalences[2]):equivalences[2],
              plane_flights: equivalences[3]?HelperFunctions.convertToScientificNotation(equivalences[3]):equivalences[3]
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
