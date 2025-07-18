package nextflow.co2footprint.FileCreators

import nextflow.trace.TraceHelper
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class ReportFileConfigTest extends Specification {
    @Shared
    String now = TraceHelper.launchTimestampFmt()

    def 'Test initialization of Config' () {
        when:
        ReportFileConfig config = new ReportFileConfig(reportConfigMap)
        config.suffix >> now

        then:
        config.getEntries() == expectedReportConfigMap

        where:
        reportConfigMap     || expectedReportConfigMap
        [:]                 || [enabled: true, file: Path.of('pipeline_info', "co2footprint_report_${now}.html") as String]
        [enabled: false]    || [enabled: false, file: Path.of('pipeline_info', "co2footprint_report_${now}.html") as String]
        [file: 'a_file.md'] || [enabled: true, file: 'a_file.md']
    }
}
