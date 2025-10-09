package nextflow.co2footprint.FileCreation

import groovy.json.JsonOutput
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Metrics.Converter
import nextflow.co2footprint.Records.CiRecordCollector

import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.ResultsTree.RecordTree
import nextflow.trace.TraceHelper

import java.nio.file.Path


/**
 * Generates the HTML CO₂ footprint report file.
 *
 * Collects all summary and per-task data, renders the HTML template,
 * and writes the final report to disk.
 */
@Slf4j
class ReportFileCreator extends BaseFileCreator{

    // Maximum number of tasks to include in the report table
    private int maxTasks

    // Information for final report
    private RecordTree workflowStats
    private CO2FootprintComputer co2FootprintComputer
    private CO2FootprintConfig config
    private String version
    private Session session
    private CiRecordCollector timeCiRecordCollector

    // Writer for the HTML file
    private BufferedWriter writer

    /**
     * Constructor for the HTML report file.
     *
     * @param path      Path to the report file
     * @param overwrite Whether to overwrite existing files
     * @param maxTasks  Maximum number of tasks to include in the report table
     */
    ReportFileCreator(Path path, boolean overwrite=false, int maxTasks=10_000) {
        super(path, overwrite)
        this.maxTasks = maxTasks
    }

    /**
     * Store all data needed for the report.
     *
     * @param processStats Statistics (quantiles, ...) per process
     * @param totalStats Total energy used (Wh) & Total CO₂ emissions (g)
     * @param equivalences  CO₂ equivalence calculations
     * @param config        Plugin configuration
     * @param version       Plugin version
     * @param session       Nextflow session
     * @param traceRecords  Map of TaskId to TraceRecord
     * @param co2eRecords   Map of TaskId to CO2Record
     * @param timeCiRecordCollector   Time & CI Record collector that contains a map of all carbon intensities at different times
     */
    void addEntries(
            RecordTree workflowStats,
            CO2FootprintComputer co2FootprintComputer,
            CO2FootprintConfig config,
            String version,
            Session session,
            CiRecordCollector timeCiRecordCollector
    ) {
        this.workflowStats = workflowStats
        this.co2FootprintComputer = co2FootprintComputer
        this.config = config
        this.version = version
        this.session = session
        this.timeCiRecordCollector = timeCiRecordCollector
    }

    /**
     * Create the report file.
     */
    void create() {
        super.create()

        writer = TraceHelper.newFileWriter(path, overwrite, 'Report')
    }

    /**
     * Write the HTML report to disk.
     */
    void write() {
        if (!created) { return }

        try {
            final String html_output = renderHtml()
            writer.withWriter { w -> w << html_output }
        }
        catch (Exception e) {
            log.warn("Failed to render CO2e footprint report -- see the log file for details", e)
        }
    }

    // ---- RENDERING METHODS -----

    /**
     * Render the report HTML document.
     *
     * @return Rendered HTML String
     */
    protected String renderHtml() {
        // render HTML report template
        final templateFields = [
                // Plugin information
                // Metadata
                plugin_version: version,
                workflow : session.getWorkflowMetadata(),
                options : renderOptionsJson(),

                // Data
                data : renderDataJson(),
                co2_totals: renderCO2TotalsJson(),
                used_EM_api: config.usesAPI(),
                timeCiRecords: JsonOutput.toJson(timeCiRecordCollector.getTimeCIs()),

                // Assets for rendering
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
                        readTemplate('assets/plotly-custom-3.1.2.min.js'),
                        readTemplate('assets/CO2FootprintReportTemplate.js')
                ]
        ]
        final String template = readTemplate('assets/CO2FootprintReportTemplate.html')
        final GStringTemplateEngine engine = new GStringTemplateEngine()
        final Template htmlTemplate = engine.createTemplate(template)
        final String htmlFile = htmlTemplate.make(templateFields) as String

        return htmlFile
    }

    /**
     * Render the payload JSON for the report.
     *
     * @return Rendered JSON as a String
     */
    protected String renderDataJson() {
        return "{" +
            "\"trace\":${JsonOutput.toJson(collectTasks(workflowStats))}," +
            "\"summary\":${JsonOutput.toJson(collectSummary(workflowStats))}" +
        "}"
    }

    // TODO: Docstring
    protected Map<String, Object> collectSummary(RecordTree workflowStats=this.workflowStats) {
        // Add an empty map if the process is not already present
        return CO2RecordAggregator.collectByLevel(
                workflowStats,
                'process',
                ['co2e', 'energy', 'co2e_non_cached', 'energy_non_cached'],
        )
    }
    // TODO: Docstring
    protected Map<String, Object> collectTrace(RecordTree workflowStats=this.workflowStats) {
        // Add an empty map if the process is not already present
        return CO2RecordAggregator.collectByLevel(
                workflowStats, 'process', ['co2e', 'energy', 'co2e_non_cached', 'energy_non_cached'],
        )
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
            [option: name, value: value as String]
        }
        
        return JsonOutput.toJson(options)
    }

    /**
    * Builds a map summarizing total CO₂ emissions and energy consumption for a specific category.
    *
    * For the given suffix (e.g., '', '_non_cached', '_market'), this method:
    *   - Retrieves the total CO₂ emissions and energy consumption from the stats map.
    *   - Converts these values to human-readable units.
    *   - Calculates equivalence values (e.g., car kilometers, tree months, plane flights).
    *   - Returns a map with all these values, using keys that include the suffix.
    * If no CO₂ value is available for the suffix, returns an empty map.
    *
    * @param suffix Suffix identifying the summary category (e.g., '', '_non_cached', '_market')
    * @return Map<String, String> containing formatted totals and equivalences for the given suffix
    */
    private Map<String, String> makeCO2Total(suffix) {
        // Retrieve total CO₂ emissions and energy consumption for the given suffix
        Double co2e = workflowStats.attributes["co2e${suffix}" as String] as Double
        Double energy = workflowStats.attributes["energy${suffix}" as String] as Double

        if (co2e != null) {
            CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(co2e)
            return [
                ("co2e${suffix}" as String): Converter.toReadableUnits(co2e,'', 'g'),
                ("energy${suffix}" as String): Converter.toReadableUnits(energy,'k','Wh'),
                ("car${suffix}" as String): equivalences.getCarKilometersReadable(),
                ("tree${suffix}" as String): equivalences.getTreeMonthsReadable(),
                ("plane_percent${suffix}" as String): equivalences.getPlanePercent() < 100.0 ? equivalences.getPlanePercentReadable() : null,
                ("plane_flights${suffix}" as String): equivalences.getPlaneFlights() >= 1 ? equivalences.getPlaneFlightsReadable() : null,
            ]
        }
        else {
            return [:]
        }
    }
    //TODO: Make card writing from within Groovy (currently: written multiple times with same structure into HTML report)

    /**
     * Render the total CO₂ footprint values for the HTML report.
     *
     * @return The JSON map
     */
    protected Map<String, String> renderCO2TotalsJson() {
        Map<String, String> totalsMap = [:]

        ['', '_non_cached', '_market'].each { String suffix ->
            totalsMap.putAll(makeCO2Total(suffix))
        }

        return totalsMap
    }

    /**
     * Render the executed tasks as a JSON list.
     *
     * Each list item is a merged map of trace- and CO2-fields:
     *   <fieldName> -> [ raw: <original>, readable: <formatted> ]
     * Missing trace fields are included with [raw: null, readable: "-"].
     * The number of tasks is limited by `maxTasks`.
     *
     * @param traceRecords Map of {@link nextflow.processor.TaskId} -> {@link nextflow.trace.TraceRecord}
     * @param co2Records   Map of {@link nextflow.processor.TaskId} -> {@link CO2Record}
     * @return             List of per-task maps combining trace and CO2 entries
     */
    protected List<Map<String, Map<String, Object>>> collectTasks(RecordTree workflowStats=this.workflowStats){
        List<Map<String, Map<String, Object>>> results = []
        for (RecordTree processes : workflowStats.children) {
            for (RecordTree tasks : processes.children) {
                for (RecordTree records : tasks.children) {
                    if (results.size() < maxTasks) {
                        results.add(records.toMap().get('values') as Map<String, Map<String, Object>>)
                    }
                    else {
                        break
                    }
                }
            }
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
