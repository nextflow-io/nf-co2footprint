package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.trace.ResourcesAggregator
import nextflow.trace.TraceRecord
import nextflow.Session


/**
 * Collect and aggregate execution metrics used by execution report
 * 
 * Extends ResourcesAggregator
 */
@Slf4j
@CompileStatic
class CO2FootprintResourcesAggregator extends ResourcesAggregator {

    final private Map<String, CO2FootprintReportSummary> summaries = new LinkedHashMap<>()

    private CO2FootprintResourcesAggregator aggregator

    CO2FootprintResourcesAggregator(Session session) {
        super(session)
    }

    @Override
    void aggregate(TraceRecord record) {
        // aggregate on the process simple name
        // therefore all nested process are kept together
        def process = record.getSimpleName()
        def summary = summaries.get(process)
        if( !summary ) {
            summaries.put(process, summary=new CO2FootprintReportSummary())
        }
        summary.add(record)
    }
}