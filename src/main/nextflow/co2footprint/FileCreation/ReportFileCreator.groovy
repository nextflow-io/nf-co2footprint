package nextflow.co2footprint.FileCreation

import groovy.json.JsonOutput
import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Metrics.Quantity
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.co2footprint.Records.CiRecordCollector
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
    private CO2RecordTree workflowStats
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
            CO2RecordTree workflowStats,
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
        Double co2e = workflowStats.co2Record.get("co2e${suffix}") as Double
        Double energy = workflowStats.co2Record.get("energy${suffix}") as Double

        if (co2e != null) {
            CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(co2e)
            return [
                ("co2e${suffix}" as String): new Quantity(co2e,'', 'g').toReadable(),
                ("energy${suffix}" as String): new Quantity(energy,'k','Wh').toReadable(),
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

    /**
     * Render the total CO₂ footprint values for the HTML report.
     *
     * @return The totals JSON map as a String
     */
    protected Map<String, String> renderCO2TotalsJson() {
        Map<String, String> totalsMap = [:]

        ['', '_non_cached', '_market'].each { String suffix -> totalsMap.putAll(makeCO2Total(suffix)) }

        return totalsMap
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

    /**
     * Collects statistics at the process level
     *
     * @param workflowStats CO2RecordTree representation of workflow stats with marked levels
     * @return Map of the process-specific statistics
     */
    protected Map<String, Object> collectSummary(CO2RecordTree workflowStats=this.workflowStats) {
        // Add an empty map if the process is not already present
        return workflowStats.collectByLevel('process', ['co2e', 'energy', 'co2e_non_cached', 'energy_non_cached'])
    }


    /**
     * Collect task-level metrics from the RecordTree up to a maximum number of tasks.
     *
     * @param workflowStats CO2RecordTree with workflow, process, and task metrics
     * @return List of task value maps
     */
    protected List<Map<String, Map<String, Object>>> collectTasks(CO2RecordTree workflowStats=this.workflowStats){
        List<Map<String, Map<String, Object>>> results = []
        for (CO2RecordTree processes : workflowStats.children) {
            for (CO2RecordTree tasks : processes.children) {
                if (results.size() < maxTasks) {
                    results.add(tasks.toMap().get('values') as Map<String, Map<String, Object>>)
                }
                else {
                    break
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
