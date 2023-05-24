package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.trace.ReportSummary

/**
 * Model a process summary data used to render box-plots in the execution HTML report
 *
 * Extends ReportSummary
 */

@CompileStatic
class CO2FootprintReportSummary extends ReportSummary {

    CO2FootprintReportSummary() {
        super()
    }

}