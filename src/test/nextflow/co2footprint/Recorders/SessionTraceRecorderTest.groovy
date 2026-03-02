package nextflow.co2footprint.Recorders

import nextflow.trace.TraceRecord
import spock.lang.Specification

class SessionTraceRecorderTest extends Specification{
    def 'test running' () {
        setup:
        SessionTraceRecorder sessionTraceRecorder = new SessionTraceRecorder()

        when:
        sessionTraceRecorder.start()
        sleep(1000)
        sessionTraceRecorder.stop()

        TraceRecord record = sessionTraceRecorder.record

        then:
        [
            container:      'JVM',
            tag:            'Session',
            attempt:        0,
            status:         'COMPLETED',
        ].each { String key, Object value ->
            record.get(key) == value
        }

        sessionTraceRecorder.samples == []
    }

    def 'test accumulation'() {
        setup:
        SessionTraceRecorder sessionTraceRecorder = new SessionTraceRecorder()
        RecordSample sample1 = new RecordSample(
                timestamp: System.currentTimeMillis(),
                cpuUsage:   1.0, systemCpu:  0.5,
                rssBytes: 1024, virtualMemoryBytes: 1024,
                readBytes:    0, writeBytes:   0,
                voluntaryContextSwitches:   0, involuntaryContextSwitches:  0
        )
        RecordSample sample2 = new RecordSample(
                timestamp: System.currentTimeMillis(),
                cpuUsage:   1.0, systemCpu:  0.5,
                rssBytes: 1024, virtualMemoryBytes: 3072,
                readBytes:    0, writeBytes:   0,
                voluntaryContextSwitches:   0, involuntaryContextSwitches:  0
        )

        when:
        sessionTraceRecorder.start()
        sessionTraceRecorder.samples.add(sample1)
        sleep(1000)
        sessionTraceRecorder.samples.add(sample2)
        sessionTraceRecorder.stop()

        TraceRecord record = sessionTraceRecorder.record

        then:
        sessionTraceRecorder.samples == [sample1, sample2]
        [
                '%cpu':         100.0,
                rss:            1024,
                vmem:           2048,
                peak_rss:       1024,
                peak_vmem:      3072,
                read_bytes:     1,
                write_bytes:    0,
                vol_ctxt:       0,
                inv_ctxt:       0,
        ].each { String key, Object value ->
            record.get(key) == value
        }
    }
}
