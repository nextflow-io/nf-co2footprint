package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.transform.InheritConstructors
import nextflow.trace.ReportSummary

/**
 * Model a process summary data used to render box-plots in the execution HTML report
 *
 * Extends ReportSummary
 */

@CompileStatic
@InheritConstructors
class CO2FootprintReportSummary extends ReportSummary {

}