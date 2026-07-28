package nextflow.co2footprint.Recorders

import nextflow.trace.TraceRecord
import spock.lang.Specification

class HeadJobTraceRecorderTest extends Specification{
    def 'test running' () {
        setup:
        HeadJobTraceRecorder headJobTraceRecorder = new HeadJobTraceRecorder()

        when:
        headJobTraceRecorder.start()
        sleep(1000)
        headJobTraceRecorder.stop()

        TraceRecord record = headJobTraceRecorder.headJobRecord

        then:
        [
            container:      'JVM',
            tag:            'Head job',
            attempt:        0,
            status:         'COMPLETED',
        ].each { String key, Object value ->
            record.get(key) == value
        }

        headJobTraceRecorder.samples == []
    }

    def 'test accumulation'() {
        setup:
        HeadJobTraceRecorder headJobTraceRecorder = new HeadJobTraceRecorder()
        MemorySample sample1 = new MemorySample(
                timestamp: System.currentTimeMillis(),
                rssBytes: 1000, virtualMemoryBytes: 1000,
        )
        MemorySample sample2 = new MemorySample(
                timestamp: System.currentTimeMillis(),
                rssBytes: 1000, virtualMemoryBytes: 3000,
        )

        when:
        headJobTraceRecorder.start()
        headJobTraceRecorder.samples.add(sample1)
        sleep(1000)
        headJobTraceRecorder.samples.add(sample2)
        headJobTraceRecorder.stop()

        TraceRecord record = headJobTraceRecorder.headJobRecord

        then:
        headJobTraceRecorder.samples == [sample1, sample2]
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
