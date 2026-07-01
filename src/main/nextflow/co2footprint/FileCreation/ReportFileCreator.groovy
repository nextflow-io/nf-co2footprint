package nextflow.co2footprint.FileCreation

import groovy.json.JsonOutput
import groovy.text.GStringTemplateEngine
import groovy.text.Template
import groovy.util.logging.Slf4j
import nextflow.co2footprint.CO2FootprintCalculator
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.CO2FootprintPlugin
import nextflow.co2footprint.Config.ReportFileConfig
import nextflow.co2footprint.Metrics.Quantity
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceHelper

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
    private CO2RecordTree stats
    private CO2FootprintCalculator co2FootprintComputer
    private CO2FootprintConfig config
    private CiRecordCollector timeCiRecordCollector
    Map workflowMetadata

    // Writer for the HTML file
    private BufferedWriter writer

    /**
     * Constructor for the HTML report file.
     *
     * @param config A {@link ReportFileConfig} that defines the created file.
     */
    ReportFileCreator(ReportFileConfig config) {
        super(config)
        this.maxTasks = config.maxTasks

        if(!config.enabled) {
            this.metaClass.create = { -> null }
            this.metaClass.write = { -> null }
            this.metaClass.close = { -> null }
            this.metaClass.addEntries = {
                CO2RecordTree X, CO2FootprintCalculator Y, CO2FootprintConfig Z, WorkflowMetadata A, CiRecordCollector B -> null
            }
        }
    }

    /**
     * Store all data needed for the report.
     *
     * @param stats         The {@link CO2RecordTree} with all stats.
     * @param config        Plugin configuration
     * @param timeCiRecordCollector   Time & CI Record collector that contains a map of all carbon intensities at different times
     * @param workflowMetadata       Nextflow session workflow metadata
     */
    void addEntries(
            CO2RecordTree stats,
            CO2FootprintCalculator co2FootprintComputer,
            CO2FootprintConfig config,
            CiRecordCollector timeCiRecordCollector,
            WorkflowMetadata workflowMetadata = null
    ) {
        this.stats = stats
        this.co2FootprintComputer = co2FootprintComputer
        this.config = config
        this.workflowMetadata = workflowMetadata ? workflowMetadata.toMap() : [:]
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
                plugin_version: CO2FootprintPlugin.version,
                workflow : workflowMetadata,
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
        // Get workflow level stats
        CO2Record workflowRecord = stats.descentTo('workflow').collect(
                {CO2RecordTree workflowTree -> workflowTree.co2Record}
        ).sum() as CO2Record

        // Retrieve total CO₂ emissions and energy consumption for the given suffix
        BigDecimal co2e = workflowRecord.get("CO2e${suffix}") as Double
        BigDecimal energy = workflowRecord.get("energy_consumption${suffix}") as Double

        if (co2e != null) {
            CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(co2e)
            return [
                ("CO2e${suffix}" as String): new Quantity(co2e,'', 'g').toReadable(),
                ("energy_consumption${suffix}" as String): new Quantity(energy,'k','Wh').toReadable(),
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
            "\"trace\":${JsonOutput.toJson(collectTasks(stats))}," +
            "\"summary\":${JsonOutput.toJson(collectSummary(stats))}" +
        "}"
    }

    /**
     * Collects statistics at the process level
     *
     * @param stats CO2RecordTree representation of workflow stats with marked levels
     * @return Map of the process-specific statistics
     */
    protected Map<String, Object> collectSummary(CO2RecordTree stats=this.stats) {
        // Add an empty map if the process is not already present
        return stats.collectByLevel('process', ['CO2e', 'energy_consumption', 'CO2e_non_cached', 'energy_consumption_non_cached'])
    }


    /**
     * Collect task-level metrics from the RecordTree up to a maximum number of tasks.
     *
     * @param stats CO2RecordTree with workflow, process, and task metrics
     * @return List of task value maps
     */
    protected List<Map<String, Map<String, Object>>> collectTasks(CO2RecordTree stats=this.stats){
        List<Map<String, Map<String, Object>>> results = []
        List<CO2RecordTree> taskRecordTrees = stats.descentTo('task')
        for(int i = 0; i < Math.min(taskRecordTrees.size(), maxTasks); i++) {
            results.add(taskRecordTrees[i].toMap().get('values') as Map<String, Map<String, Object>>)
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

        List<String> lines = new BufferedReader(new InputStreamReader(res, "UTF-8")).readLines()

        // Filter out lines starting with "//"
        List<String> cleanLines = lines.findAll { line -> !line.trim().startsWith("//") }

        // Join back into a single string
        return cleanLines.join("\n")
    }
}
