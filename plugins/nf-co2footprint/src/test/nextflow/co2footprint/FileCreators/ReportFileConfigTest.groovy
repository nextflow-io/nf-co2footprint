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
        ReportFileConfig config = new ReportFileConfig(reportConfigMap, "_${now}")

        then:
        config.getEntries() == expectedReportConfigMap

        where:
        reportConfigMap     || expectedReportConfigMap
        [:]                 || [enabled: true, file: Path.of('pipeline_info', "co2footprint_report_${now}.html") as String, overwrite:false, maxTasks:10000]
        [enabled: false]    || [enabled: false, file: Path.of('pipeline_info', "co2footprint_report_${now}.html") as String, overwrite:false, maxTasks:10000]
        [file: 'a_file.md'] || [enabled: true, file: 'a_file.md', overwrite:false, maxTasks:10000]
        [file: 'a_file.md', overwrite: true]   || [enabled: true, file: 'a_file.md', overwrite:true, maxTasks:10000]
    }
}
