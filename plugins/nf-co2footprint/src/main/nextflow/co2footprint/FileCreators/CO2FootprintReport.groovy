package nextflow.co2footprint.FileCreators

import groovy.json.JsonOutput
import nextflow.co2footprint.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
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


/**
 * Generates the HTML CO₂ footprint report file.
 *
 * Collects all summary and per-task data, renders the HTML template,
 * and writes the final report to disk.
 */
@Slf4j
class CO2FootprintReport extends CO2FootprintFile{

    // Maximum number of tasks to include in the report table
    private int maxTasks

    // Information for final report
    private CO2EquivalencesRecord equivalences
    private Map<String, Double> totalStats
    private Map<String, Map<String, Map<String, ?>>> processStats
    private CO2FootprintConfig config
    private String version
    private Session session
    private Map<TaskId, TraceRecord> traceRecords
    private Map<TaskId, CO2Record> co2eRecords

    // Writer for the HTML file
    private BufferedWriter writer = TraceHelper.newFileWriter(path, overwrite, 'Report')

    /**
     * Constructor for the HTML report file.
     *
     * @param path      Path to the report file
     * @param overwrite Whether to overwrite existing files
     * @param maxTasks  Maximum number of tasks to include in the report table
     */
    CO2FootprintReport(Path path, boolean overwrite=false, int maxTasks=10_000) {
        super(path, overwrite)
        this.maxTasks = maxTasks
    }

    /**
     * Store all data needed for the report.
     *
     * @param totalStats Total energy used (Wh) & Total CO₂ emissions (g)
     * @param processStats Statistics (quantiles, ...) per process
     * @param equivalences  CO₂ equivalence calculations
     * @param config        Plugin configuration
     * @param version       Plugin version
     * @param session       Nextflow session
     * @param traceRecords  Map of TaskId to TraceRecord
     * @param co2eRecords   Map of TaskId to CO2Record
     */
    void addEntries(
            Map<String, Double> totalStats,
            Map<String, Map<String, Map<String, ?>>> processStats,
            CO2EquivalencesRecord equivalences,
            CO2FootprintConfig config,
            String version,
            Session session,
            Map<TaskId, TraceRecord> traceRecords,
            Map<TaskId, CO2Record> co2eRecords
    ) {
        this.totalStats = totalStats
        this.processStats = processStats
        this.equivalences = equivalences
        this.config = config
        this.version = version
        this.session = session
        this.traceRecords = traceRecords
        this.co2eRecords = co2eRecords
    }

    /**
     * Write the HTML report to disk.
     */
    void write() {
        try {
            final String html_output = renderHtml()
            writer.withWriter { w -> w << html_output }
        }
        catch (Exception e) {
            log.warn("Failed to render CO2e footprint report -- see the log file for details", e)
        }
    }

    /**
     * Close the report file writer.
     */
    void close() {
        writer.close()
    }

    // ---- RENDERING METHODS -----

    /**
     * Render the report HTML document.
     *
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
                        readTemplate('nextflow/trace/assets/datatables.min.css'),
                        readTemplate('assets/CO2FootprintReportTemplate.css')
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
     * Render the payload JSON for the report.
     *
     * @return Rendered JSON as a String
     */
    protected String renderPayloadJson() {
        return "{" +
            "\"trace\":${renderTasksJson(traceRecords, co2eRecords)}," +
            "\"summary\":${JsonOutput.toJson(processStats)}" +
        "}"
    }

    /**
     * Render the entered options / config as a JSON String.
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
     * Render the total CO₂ footprint values for the HTML report.
     *
     * @return The rendered JSON map
     */
    protected Map<String, String> renderCO2TotalsJson() {
        return [
            co2e: Converter.toReadableUnits(totalStats['co2e'],'m', 'g'),
            energy:Converter.toReadableUnits(totalStats['energy'],'m','Wh'),
            co2e_non_cached: Converter.toReadableUnits(totalStats['co2e_non_cached'],'m', 'g'),
            energy_non_cached:Converter.toReadableUnits(totalStats['energy_non_cached'],'m','Wh'),
            car: equivalences.getCarKilometersReadable(),
            tree: equivalences.getTreeMonthsReadable(),
            plane_percent: equivalences.getPlanePercent() < 100.0 ? equivalences.getPlanePercentReadable() : null,
            plane_flights: equivalences.getPlaneFlights() >= 1 ? equivalences.getPlaneFlightsReadable() : null
        ]
    }

    /**
     * Render the executed tasks as a JSON list.
     *
     * @param traceRecords Map of TaskId to TraceRecord
     * @param co2Records   Map of TaskId to CO2Record
     * @return             List of JSON entries as strings
     */
    protected List<String> renderTasksJson(
            Map<TaskId, TraceRecord> traceRecords, Map<TaskId, CO2Record> co2Records
    ){
        // Limit to maxTasks
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
     * Read the document HTML template from the application classpath.
     *
     * @param path A resource path location
     * @return     The loaded template as a string
     */
    private String readTemplate( String path ) {
        InputStream res = this.class.getClassLoader().getResourceAsStream(path)
        if (res == null) {
            throw new FileNotFoundException("Template not found at path: $path")
        }
        return new BufferedReader(new InputStreamReader(res, "UTF-8")).text
    }
}
