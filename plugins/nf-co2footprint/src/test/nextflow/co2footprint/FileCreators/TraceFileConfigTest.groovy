package nextflow.co2footprint.FileCreators

import nextflow.trace.TraceHelper
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class TraceFileConfigTest extends Specification {
    @Shared
    String now = TraceHelper.launchTimestampFmt()

    def 'Test initialization of Config' () {
        when:
        TraceFileConfig config = new TraceFileConfig(traceConfigMap)
        config.suffix >> now

        then:
        config.getEntries() == expectedTraceConfigMap

        where:
        traceConfigMap      || expectedTraceConfigMap
        [:]                 || [enabled: true, file: Path.of('pipeline_info', "co2footprint_trace_${now}.txt") as String]
        [enabled: false]    || [enabled: false, file: Path.of('pipeline_info', "co2footprint_trace_${now}.txt") as String]
        [file: 'a_file.md'] || [enabled: true, file: 'a_file.md']
    }
}
