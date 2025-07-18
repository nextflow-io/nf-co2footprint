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
        TraceFileConfig config = new TraceFileConfig(traceConfigMap, "_${now}")

        then:
        config.getEntries() == expectedTraceConfigMap

        where:
        traceConfigMap      || expectedTraceConfigMap
        [:]                 || [enabled: true, file: Path.of('pipeline_info', "co2footprint_trace_${now}.txt") as String, overwrite:false]
        [enabled: false]    || [enabled: false, file: Path.of('pipeline_info', "co2footprint_trace_${now}.txt") as String, overwrite:false]
        [file: 'a_file.md'] || [enabled: true, file: 'a_file.md', overwrite:false]
        [file: 'a_file.md', overwrite: true]   || [enabled: true, file: 'a_file.md', overwrite:true]
    }
}
