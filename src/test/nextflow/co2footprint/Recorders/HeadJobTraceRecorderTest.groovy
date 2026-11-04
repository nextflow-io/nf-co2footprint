package nextflow.co2footprint.Recorders

import nextflow.Session
import nextflow.exception.UnexpectedException
import nextflow.trace.TraceRecord
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

class HeadJobTraceRecorderTest extends Specification{
    def 'test running' () {
        setup:
        HeadJobTraceRecorder headJobTraceRecorder = new HeadJobTraceRecorder()

        when:
        headJobTraceRecorder.start()
        sleep(1000)
        headJobTraceRecorder.stop()
        headJobTraceRecorder.report()

        TraceRecord record = headJobTraceRecorder.headJobRecord

        then:
        [
            container:      'JVM',
            tag:            'Head job',
            attempt:        0,
            status:         'COMPLETED',
        ].every { String key, Object value ->
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
        headJobTraceRecorder.report()

        TraceRecord record = headJobTraceRecorder.headJobRecord

        then:
        headJobTraceRecorder.samples == [sample1, sample2]
        [
                rss:            1000.0,
                vmem:           2000.0,
                peak_rss:       1000L,
                peak_vmem:      3000L,
        ].every { String key, Object value ->
            record.get(key) == value
        }
    }

    def 'test error on multiple sessions'() {
        setup:
        HeadJobTraceRecorder headJobTraceRecorder = new HeadJobTraceRecorder()
        Session session = new Session()

        when:
        headJobTraceRecorder.start()
        headJobTraceRecorder.attachSession(session)
        headJobTraceRecorder.attachSession(session)


        then:
        thrown(UnexpectedException)
    }

    def 'report survives concurrent modification of samples'() {
        setup:
        HeadJobTraceRecorder headJobTraceRecorder = new HeadJobTraceRecorder()
        headJobTraceRecorder.start()

        AtomicReference<Throwable> failure = new AtomicReference<>()
        AtomicBoolean running = new AtomicBoolean(true)
        CountDownLatch started = new CountDownLatch(1)

        // The real sampler fires every 500ms; this one appends continuously for a
        // bounded time, throttled so the list (and memory) stays bounded while the
        // writer is still adding across every report() iteration.
        Thread writer = new Thread({
            started.countDown()
            while (running.get()) {
                headJobTraceRecorder.samples.add(new MemorySample(
                        timestamp: System.currentTimeMillis(),
                        rssBytes: 1,
                        virtualMemoryBytes: 1,
                ))
                LockSupport.parkNanos(10_000)
            }
        }, 'samples-writer')
        writer.start()
        started.await()

        when:
        // Collect concurrently with an actively-appending list. Without the atomic
        // snapshot in report() the collect iterator throws ConcurrentModificationException.
        long deadline = System.currentTimeMillis() + 3000
        try {
            while (System.currentTimeMillis() < deadline) {
                headJobTraceRecorder.report()
            }
        } catch (Throwable t) {
            failure.set(t)
        } finally {
            running.set(false)
            writer.join(5000)
        }

        then:
        failure.get() == null
    }
}
