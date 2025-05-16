package nextflow.co2footprint.FileCreators

import nextflow.co2footprint.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.CO2FootprintResourcesAggregator
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
    private CO2FootprintResourcesAggregator aggregator
    private CO2FootprintConfig config
    private String version
    private Session session
    private Map<TaskId, TraceRecord> traceRecords
    private Map<TaskId, CO2Record> co2eRecords

    // Writer
    private BufferedWriter writer = TraceHelper.newFileWriter(path, overwrite, 'Report')

    CO2FootprintReport(Path path, boolean overwrite, int maxTasks) {
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
    void write(
            Double total_energy,
            Double total_co2,
            CO2EquivalencesRecord equivalences,
            CO2FootprintResourcesAggregator aggregator,
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

        try {
            String html_output = renderHtml()
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
                options : renderOptionsJson()
        ]
        final String tpl = readTemplate('CO2FootprintReportTemplate.html')
        GStringTemplateEngine engine = new GStringTemplateEngine()
        Template html_template = engine.createTemplate(tpl)
        String html_output = html_template.make(tpl_fields) as String

        return html_output
    }

    /**
     * @return The tasks json payload
     */
    protected String renderTasksJson() {
        co2eRecords.size()<= maxTasks ? renderJsonData(traceRecords.values(), co2eRecords) : 'null'
    }

    protected String renderPayloadJson() {
        "{ \"trace\":${renderTasksJson()}, \"summary\":${aggregator.renderSummaryJson()} }"
    }

    /**
     * @return The options json payload
     */
    protected String renderOptionsJson() {
        Map all_options = config.collectInputFileOptions() + config.collectOutputFileOptions() + config.collectCO2CalcOptions()

        // Render JSON
        List<String> options = all_options.collect { name, value ->
            def valueStr = value as String
            // If value is a Closure (e.g. in case API calls are being made for each task), replace with 'dynamic'
            if (value instanceof Closure) {
                valueStr = 'dynamic'
            }
            "{ \"option\":\"${name}\", \"value\":\"${valueStr}\" }"
        }

        return "[${String.join(',', options)}]"
    }

    /**
     * Render the total co2 footprint values for html report
     *
     * @param data A collection of {@link nextflow.trace.TraceRecord}s representing the tasks executed
     * @param dataCO2 A collection of {@link nextflow.co2footprint.CO2Record}s representing the tasks executed
     * @return The rendered json
     */
    protected Map renderCO2TotalsJson() {
        [ co2: Converter.toReadableUnits(total_co2,'m', ''),        // TODO: unit (g) could be given here and removed from HTML template
          energy:Converter.toReadableUnits(total_energy,'m',''),    // TODO: unit (Wh) could be given here and removed from HTML template
          car: equivalences.getCarKilometersReadable(),
          tree: equivalences.getTreeMonthsReadable(),
          plane_percent: equivalences.getPlanePercentReadable(),
          plane_flights: equivalences.getPlaneFlightsReadable()
        ]
    }

    // TODO: Simplification through TraceRecord.renderJSON() without arguments ? (also relevant for CO2Record)
    /**
     * Render the executed tasks json payload
     *
     * @param data A collection of {@link nextflow.trace.TraceRecord}s representing the tasks executed
     * @param dataCO2 A collection of {@link nextflow.co2footprint.CO2Record}s representing the tasks executed
     * @return The rendered json payload
     */
    protected static String renderJsonData(Collection<TraceRecord> data, Map<TaskId, CO2Record> dataCO2) {
        List<String> formats = null
        List<String> fields = null
        List<String> co2Formats = null
        List<String> co2Fields = null
        StringBuilder result = new StringBuilder()
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

        return result as String
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
