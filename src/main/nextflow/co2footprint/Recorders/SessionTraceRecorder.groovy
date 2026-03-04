package nextflow.co2footprint.Recorders

import nextflow.Session
import nextflow.processor.TaskRun
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

    // CPU ticks snapshot
    private long[] ticks_t0 = processor.systemCpuLoadTicks

    // Sampling settings
    private final Timer timer = new Timer('session-trace-recorder', true)

    // Process information
    private int pid
    private OSProcess process_t0

    // Aggregation
    final List<RecordSample> samples = [].asSynchronized() as List<RecordSample>
    final TraceRecord sessionRecord = new TraceRecord()

    /**
     * Start the recording of a session.
     */
    void start() {
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
        pid = runtimeBean.pid as int

        timer.scheduleAtFixedRate(new TimerTask() { void run() { sample(pid) } } , 0, 500)

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
        long endTimestamp = System.currentTimeMillis()
        sessionRecord.putAll(
                [
                        status:         'COMPLETED',
                        complete:       endTimestamp,
                        duration:       endTimestamp - (sessionRecord.get('submit') as long),
                        realtime:       runtimeBean.uptime,
                ]
        )
        if (samples) {
            sessionRecord.putAll(
                    [
                            memory:         Runtime.getRuntime().maxMemory(),
                            '%cpu':         samples.collect({RecordSample sample -> sample.cpuUsage}).average() * 100,
                            rss:            samples.collect({RecordSample sample -> sample.rssBytes}).average() as Long,
                            vmem:           samples.collect({RecordSample sample -> sample.virtualMemoryBytes}).average() as Long,
                            peak_rss:       samples.collect({RecordSample sample -> sample.rssBytes}).max(),
                            peak_vmem:      samples.collect({RecordSample sample -> sample.virtualMemoryBytes}).max(),
                            read_bytes:     samples.collect({RecordSample sample -> sample.readBytes}).sum(),
                            write_bytes:    samples.collect({RecordSample sample -> sample.writeBytes}).sum(),
                            vol_ctxt:       samples.last().voluntaryContextSwitches - samples.first().voluntaryContextSwitches,
                            inv_ctxt:       samples.last().involuntaryContextSwitches - samples.first().involuntaryContextSwitches,
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
     * Start sampling utilization information samples about a process in a fixed interval.
     *
     * @param pid Process ID of the surveiled process
     */
    void sample(int pid) {
        process_t0 = os.getProcess(pid)

        long[] ticks_t1 = processor.systemCpuLoadTicks
        OSProcess process_t1 = os.getProcess(pid)

        if (process_t1 != null) {
            RecordSample sample = new RecordSample(
                    timestamp: System.currentTimeMillis(),

                    // CPU — delta between two ticks
                    cpuUsage:   process_t1.getProcessCpuLoadBetweenTicks(process_t0),
                    systemCpu:  processor.getSystemCpuLoadBetweenTicks(ticks_t0),

                    // Memory
                    rssBytes: process_t1.residentSetSize,
                    virtualMemoryBytes: process_t1.virtualSize,

                    // I/O (cumulative — diff yourself if you want per-interval)
                    readBytes:    process_t1.bytesRead,
                    writeBytes:   process_t1.bytesWritten,

                    // Context switches (cumulative)
                    voluntaryContextSwitches:   process_t1.minorFaults,
                    involuntaryContextSwitches:  process_t1.majorFaults,
            )

            samples.add(sample)
            ticks_t0 = ticks_t1
            process_t0 = process_t1
        }
    }
}
