package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.text.GStringTemplateEngine
import groovy.transform.InheritConstructors
import nextflow.trace.ReportObserver
import nextflow.trace.TraceHelper
import nextflow.Session

import java.nio.file.Files
import java.nio.file.Path

/**
 * Render pipeline report processes execution.
 * Extends ReportObserver
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>
 */
@Slf4j
@CompileStatic
@InheritConstructors
class CO2FootprintReportObserver extends ReportObserver {

    static final public String DEF_FILE_NAME = "CO2Footprint-report-${TraceHelper.launchTimestampFmt()}.html"


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
     * Render the report HTML document
     */
    @Override
    protected void renderHtml() {

        // render HTML report template
        final tpl_fields = [
            workflow : getWorkflowMetadata(),
            payload : renderPayloadJson(),
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
                readTemplate('nextflow/trace/assets/ReportTemplate.js')
            ]
        ]
        final tpl = readTemplate('nextflow/trace/ReportTemplate.html')
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
     * Read the document HTML template from the application classpath
     *
     * @param path A resource path location
     * @return The loaded template as a string
     */
    @Override
    private String readTemplate( String path ) {
        StringWriter writer = new StringWriter();
        def res =  this.class.getClassLoader().getResourceAsStream( path )
        int ch
        while( (ch=res.read()) != -1 ) {
            writer.append(ch as char);
        }
        writer.toString();
    }
}