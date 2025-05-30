package nextflow.co2footprint.FileCreators

import groovy.json.JsonOutput
import nextflow.co2footprint.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.CO2RecordAggregator
import nextflow.co2footprint.CO2Record
import nextflow.co2footprint.utils.Converter

import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.processor.TaskId
import nextflow.trace.TraceHelper
import nextflow.trace.TraceRecord

import java.nio.file.Path


@Slf4j
/**
 * Class to generate the HTML report file
 */
class CO2FootprintReport extends CO2FootprintFile{

    /**
     * Maximum number of tasks until table is dropped
     */
    private int maxTasks

    // Information for final report
    private Double total_energy
    private Double total_co2
    private CO2EquivalencesRecord equivalences
    private CO2RecordAggregator aggregator
    private CO2FootprintConfig config
    private String version
    private Session session
    private Map<TaskId, TraceRecord> traceRecords
    private Map<TaskId, CO2Record> co2eRecords

    // Writer
    private BufferedWriter writer = TraceHelper.newFileWriter(path, overwrite, 'Report')

    CO2FootprintReport(Path path, boolean overwrite=false, int maxTasks=10_000) {
        super(path, overwrite)
        this.maxTasks = maxTasks
    }

    /**
     * Write the final report and close the file
     *
     * @param total_energy
     * @param total_co2
     * @param equivalences
     * @param aggregator
     * @param config
     * @param version
     * @param session
     * @param traceRecords
     * @param co2eRecords
     */
    void addEntries(
            Double total_energy,
            Double total_co2,
            CO2EquivalencesRecord equivalences,
            CO2RecordAggregator aggregator,
            CO2FootprintConfig config,
            String version,
            Session session,
            Map<TaskId, TraceRecord> traceRecords,
            Map<TaskId, CO2Record> co2eRecords
    ) {
        this.total_energy = total_energy
        this.total_co2 = total_co2
        this.equivalences = equivalences
        this.aggregator = aggregator
        this.config = config
        this.version = version
        this.session = session
        this.traceRecords = traceRecords
        this.co2eRecords = co2eRecords
    }

    void write() {
        try {
            final String html_output = renderHtml()
            writer.withWriter { w -> w << html_output }
        }
        catch (Exception e) {
            log.warn("Failed to render CO2e footprint report -- see the log file for details", e)
        }
    }

    void close() {
        writer.close()
    }


    // ---- RENDERING METHODS -----

    /**
     * Render the report HTML document
     *
     * @param total_energy
     * @param total_co2
     * @param equivalences
     * @param config
     * @param version
     * @param session
     * @return Rendered HTML String
     */
    protected String renderHtml() {
        Map co2Options = config.collectCO2CalcOptions()
        // render HTML report template
        final tpl_fields = [
                workflow : session.getWorkflowMetadata(),
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
                options : renderOptionsJson(),
                used_EM_api: co2Options.ci instanceof Closure // true if the CI value is calculated using the electricityMaps API
        ]
        final String template = readTemplate('assets/CO2FootprintReportTemplate.html')
        final GStringTemplateEngine engine = new GStringTemplateEngine()
        final Template htmlTemplate = engine.createTemplate(template)

        return htmlTemplate.make(tpl_fields) as String
    }

    /**
     * Render the Payload Json
     *
     * @return Rendered JSON as a String
     */
    protected String renderPayloadJson() {
        return "{" +
            "\"trace\":${renderTasksJson(traceRecords, co2eRecords)}," +
            "\"summary\":${JsonOutput.toJson(aggregator.computeProcessStats())}" +
        "}"
    }

    /**
     * Render the entered options / config as a JSON String
     *
     * @return The options json payload
     */
    protected String renderOptionsJson() {
        Map<String,?> all_options = config.collectInputFileOptions() + config.collectOutputFileOptions() + config.collectCO2CalcOptions()

        // Render JSON
        List<Map<String, String>> options = all_options.collect { String name, value ->
            [option: name, value: (value instanceof Closure) ? '"dynamic"' : value as String]
        }

        return JsonOutput.toJson(options)
    }

    /**
     * Render the total co2 footprint values for html report
     *
     * @return The rendered json
     */
    protected Map<String, String> renderCO2TotalsJson() {
        return [
            co2: Converter.toReadableUnits(total_co2,'m', 'g'),
            energy:Converter.toReadableUnits(total_energy,'m','Wh'),
            car: equivalences.getCarKilometersReadable(),
            tree: equivalences.getTreeMonthsReadable(),
            plane_percent: equivalences.getPlanePercent() < 100.0 ? equivalences.getPlanePercentReadable() : null,
            plane_flights: equivalences.getPlaneFlights() >= 1 ? equivalences.getPlaneFlightsReadable() : null
        ]
    }

    /**
     * Render the executed tasks JSON
     *
     * @param data A Map of {@link nextflow.processor.TaskId}s and {@link nextflow.trace.TraceRecord}s representing the tasks executed
     * @param dataCO2 A Map of {@link nextflow.processor.TaskId}s and {@link nextflow.co2footprint.CO2Record}s representing the co2Record traces
     * @return The collected List of JSON entries
     */
    protected List<String> renderTasksJson(
            Map<TaskId, TraceRecord> traceRecords, Map<TaskId, CO2Record> co2Records
    ){
        // Select maximum number of Records (limits also co2Records by only using the limited TaskIds)
        traceRecords = traceRecords.take(maxTasks)

        final List<String> results = []
        traceRecords.each { TaskId taskId ,TraceRecord traceRecord ->

            CharSequence traceRecordJson = traceRecord.renderJson()
            traceRecordJson = traceRecordJson.dropRight(1)
            traceRecordJson = traceRecordJson + ','

            CharSequence co2RecordJson = co2Records[taskId].renderJson()
            co2RecordJson = co2RecordJson.drop(1)

            results.add( (traceRecordJson + co2RecordJson).toString() )
        }

        return results
    }

    /**
     * Read the document HTML template from the application classpath
     *
     * @param path A resource path location
     * @return The loaded template as a string
     */
    private String readTemplate( String path ) {
        StringWriter writer = new StringWriter()
        InputStream res = this.class.getClassLoader().getResourceAsStream( path )
        int ch
        while( (ch=res.read()) != -1 ) {
            writer.append(ch as char)
        }
        writer.toString()
    }
}
