package nextflow.co2footprint.Recorders

import nextflow.Session
import nextflow.trace.TraceRecord
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.software.os.OSProcess
import oshi.software.os.OperatingSystem

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

import com.sun.management.OperatingSystemMXBean

/**
 * A Recorder of trace values for a Nextflow session, which can be attached after startup
 * to capture timepoints before workflow invocation.
 */
class SessionTraceRecorder {
    // OSHI info handles
    private final RuntimeMXBean          runtimeBean = ManagementFactory.getRuntimeMXBean()
    private final OperatingSystemMXBean  osBean      = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    private final SystemInfo             systemInfo  = new SystemInfo()
    private final CentralProcessor       processor   = systemInfo.hardware.processor
    private final OperatingSystem        os          = systemInfo.operatingSystem

    // Sampling settings
    private final Timer timer = new Timer('session-trace-recorder', true)

    // Process information
    private int pid
    private OSProcess process
    private Map<Integer, OSProcess> descendantProcesses = [:].asSynchronized()

    // Aggregation
    final List<MemorySample> samples = [].asSynchronized() as List<MemorySample>
    final TraceRecord sessionRecord = new TraceRecord()

    /**
     * Start the recording of a session.
     */
    void start() {
        pid = runtimeBean.pid as int
        process = os.getProcess(pid)

        sessionRecord.putAll(
                [
                        container:      'JVM',
                        tag:            'Session',
                        status:         'SUBMITTED',
                        submit:         runtimeBean.startTime,
                        attempt:        0,
                        cpus:           osBean.availableProcessors,
                        cpu_model:      processor.processorIdentifier.name
                ]
        )
    }

    /**
     * Attach a Nextflow {@link Session} to the Recorder.
     *
     * @param session The current Nextflow session
     */
    void attachSession(Session session) {
        // Start sampling for memory
        timer.scheduleAtFixedRate(new TimerTask() { void run() { sample() } } , 0, 500)

        sessionRecord.putAll(
                [
                        task_id:        '-1',
                        hash:           session.hashCode(),
                        native_id:      pid as String,
                        process:        session.getScriptName(),
                        name:           session.getRunName(),
                        status:         'STARTED',
                        start:          System.currentTimeMillis(),
                        attempt:        sessionRecord.store.get('attempt', 0) + 1
                ]
        )
    }

    /**
     * Create a finalized session specific {@link TraceRecord} from the current samples.
     */
    TraceRecord report() {
        long endTime = System.currentTimeMillis()

        process.updateAttributes()
        double processLoad = process.getProcessCpuLoadCumulative()
        descendantProcesses.each { Integer pid, OSProcess child ->
            child.updateAttributes()
            processLoad += child.getProcessCpuLoadCumulative()
        }

        sessionRecord.putAll(
                [
                        status:         'COMPLETED',
                        complete:       endTime,
                        duration:       endTime - (sessionRecord.get('submit') as long),
                        realtime:       runtimeBean.uptime,
                        memory:         Runtime.getRuntime().maxMemory(),
                        '%cpu':         processLoad * 100,
                        read_bytes:     process.bytesRead,
                        write_bytes:    process.bytesWritten,
                        vol_ctxt:       process.minorFaults,
                        inv_ctxt:       process.majorFaults,
                ]
        )

        if (samples) {
            sessionRecord.putAll(
                    [
                            rss:            samples.collect({ MemorySample sample -> sample.rssBytes}).average() as Long,
                            vmem:           samples.collect({ MemorySample sample -> sample.virtualMemoryBytes}).average() as Long,
                            peak_rss:       samples.collect({ MemorySample sample -> sample.rssBytes}).max(),
                            peak_vmem:      samples.collect({ MemorySample sample -> sample.virtualMemoryBytes}).max(),
                    ]
            )
        }

        return sessionRecord
    }

    /**
     * Stop the sampling and finish accumulating the information in the TraceRecord.
     */
    void stop() {
        timer.cancel()
        timer.purge()
    }

    /**
     * Sample memory information.
     */
    void sample() {
        process.updateAttributes()
        os.getDescendantProcesses(process.processID, null, null, 0)?.each { child ->
            descendantProcesses[child.processID] = child
        }

        if (process != null) {
            MemorySample sample = new MemorySample(
                    timestamp: System.currentTimeMillis(),

                    // Memory
                    rssBytes: process.residentSetSize,
                    virtualMemoryBytes: process.virtualSize,
            )

            samples.add(sample)
        }
    }
}
