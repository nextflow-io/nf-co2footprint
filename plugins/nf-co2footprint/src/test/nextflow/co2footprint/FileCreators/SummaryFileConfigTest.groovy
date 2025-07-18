package nextflow.co2footprint.FileCreators

import nextflow.trace.TraceHelper
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class SummaryFileConfigTest extends  Specification{
    @Shared
    String now = TraceHelper.launchTimestampFmt()

    def 'Test initialization of Config' () {
        when:
        SummaryFileConfig config = new SummaryFileConfig(summaryConfigMap)
        config.suffix >> now

        then:
        config.getEntries() == expectedSummaryConfigMap

        where:
        summaryConfigMap    || expectedSummaryConfigMap
        [:]                 || [enabled: true, file: Path.of('pipeline_info', "co2footprint_summary_${now}.txt") as String]
        [enabled: false]    || [enabled: false, file: Path.of('pipeline_info', "co2footprint_summary_${now}.txt") as String]
        [file: 'a_file.md'] || [enabled: true, file: 'a_file.md']
    }
}
