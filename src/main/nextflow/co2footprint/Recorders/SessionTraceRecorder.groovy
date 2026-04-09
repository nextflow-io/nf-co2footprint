package nextflow.co2footprint.Recorders

import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceRecord
import oshi.SystemInfo
import oshi.driver.linux.proc.ProcessStat
import oshi.hardware.CentralProcessor
import oshi.software.os.OSProcess
import oshi.software.os.OperatingSystem
import oshi.util.GlobalConfig.PropertyException
import oshi.util.tuples.Triplet

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean

import com.sun.management.OperatingSystemMXBean

/**
 * A Recorder of trace values for a Nextflow session, which can be attached after startup
 * to capture timepoints before workflow invocation.
 */
@Slf4j
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
    private Map<Integer, OSProcess> allProcesses = [(pid): process].asSynchronized()

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
        long endTimestamp = System.currentTimeMillis()

        // Reduce the process to values
        List<OSProcess> processList = allProcesses.values() as List<OSProcess>
        processList.each({ OSProcess p -> p.updateAttributes() })

        Map<ProcessStat.PidStat, Long> pidStats = getPidStats()

        // Determine CPU usage
        Double cpuUsage
        if (pidStats != null) {

            Long cpuTime = pidStats.get(ProcessStat.PidStat.UTIME) + pidStats.get(ProcessStat.PidStat.STIME) +
                    pidStats.get(ProcessStat.PidStat.CUTIME) + pidStats.get(ProcessStat.PidStat.CSTIME)
            log.info("CPU time for PID ${pid}: ${cpuTime} jiffies")
            Long elapsedTime = processor.getSystemCpuLoadTicks().sum() - pidStats.get(ProcessStat.PidStat.STARTTIME)
            log.info("End CPU ticks: ${processor.getSystemCpuLoadTicks().sum()} ms")
            log.info("Elapsed time for PID ${pid}: ${elapsedTime} jiffies")
            cpuUsage = cpuTime / elapsedTime
            log.info("Calculated CPU usage for PID ${pid}: ${cpuUsage * 100}%")
        }
        else {
            cpuUsage = processList.sum({ OSProcess p -> p.getProcessCpuLoadCumulative() }) as double
        }

        sessionRecord.putAll(
                [
                        status:         'COMPLETED',
                        complete:       endTimestamp,
                        duration:       endTimestamp - (sessionRecord.get('submit') as long),
                        realtime:       runtimeBean.uptime,
                        memory:         Runtime.getRuntime().maxMemory(),
                        '%cpu':         cpuUsage * 100,
                        read_bytes:     processList.sum({ OSProcess p -> p.bytesRead}) as Long,
                        write_bytes:    processList.sum({ OSProcess p -> p.bytesWritten}) as Long,
                        vol_ctxt:       processList.sum({ OSProcess p -> p.minorFaults}) as Long,
                        inv_ctxt:       processList.sum({ OSProcess p -> p.majorFaults}) as Long,
                ] 
        )

        if (samples) {
            sessionRecord.putAll(
                    [
                            rss:            samples.average({ MemorySample sample -> sample.rssBytes}) as Long,
                            vmem:           samples.average({ MemorySample sample -> sample.virtualMemoryBytes}) as Long,
                            peak_rss:       samples.max({ MemorySample sample -> sample.rssBytes}),
                            peak_vmem:      samples.max({ MemorySample sample -> sample.virtualMemoryBytes}),
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
     * Attempts to fetch the PID stats for the current process.
     *
     * @return Map of PID stats, or null if the information could not be retrieved (e.g. due to permissions or OS)
     */
    Map<ProcessStat.PidStat, Long> getPidStats() {
        try {
            Triplet<String, Character, Map<ProcessStat.PidStat, Long>> stats = ProcessStat.getPidStats(pid)
            return stats.getC()
        }
        catch (PropertyException propertyException) {
            log.trace("Failed to get process stats for PID ${pid}: ${propertyException.message}")
        }

        return null
    }

    /**
     * Sample memory information and update children.
     */
    void sample() {
        // Update root process information
        process.updateAttributes()

        // Collect active descendant processes
        List<OSProcess> activeDescendants = os.getDescendantProcesses(pid, null, null, 0)
        List<OSProcess> activeProcesses = [process] + activeDescendants

        // Enter the active descendants into the map
        activeDescendants.each({ OSProcess p -> allProcesses[p.processID] = p })

        if (process != null) {
            MemorySample sample = new MemorySample(
                    timestamp: System.currentTimeMillis(),

                    // Memory
                    rssBytes: activeProcesses.sum({ OSProcess p -> p.residentSetSize}) as Long,
                    virtualMemoryBytes: activeProcesses.sum({ OSProcess p -> p.virtualSize}) as Long,
            )

            samples.add(sample)
        }
    }
}
