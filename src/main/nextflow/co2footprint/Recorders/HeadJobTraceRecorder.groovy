package nextflow.co2footprint.Recorders

import com.sun.management.OperatingSystemMXBean
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskRun
import nextflow.trace.TraceRecord
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.software.os.OSProcess
import oshi.software.os.OperatingSystem

import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import java.util.concurrent.ConcurrentHashMap

/**
 * A Recorder of trace values for a Nextflow head job, which can be attached after startup
 * to capture timepoints before workflow invocation.
 */
@Slf4j
class HeadJobTraceRecorder {
    // Constants
    static final String headJobSuffix = 'head_job'
    
    // OSHI info handles
    private final RuntimeMXBean          runtimeBean = ManagementFactory.getRuntimeMXBean()
    private final OperatingSystemMXBean  osBean      = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    private final SystemInfo             systemInfo  = new SystemInfo()
    private final CentralProcessor       processor   = systemInfo.hardware.processor
    private final OperatingSystem        os          = systemInfo.operatingSystem

    // Sampling settings
    private final Timer timer = new Timer('head-job-trace-recorder', true)

    // Process information
    private int pid
    private OSProcess rootProcess
    private Set<OSProcess> headProcesses = ConcurrentHashMap.newKeySet()

    // Aggregation
    final List<MemorySample> samples = [].asSynchronized() as List<MemorySample>
    final TraceRecord headJobRecord = new TraceRecord()

    /**
     * Start the recording of a head job.
     */
    void start() {
        pid = runtimeBean.pid as int
        rootProcess = os.getProcess(pid)
        headProcesses.add(rootProcess)

        headJobRecord.putAll(
                [
                        task_id:        '-1',
                        container:      'JVM',
                        tag:            'Head job',
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

        headJobRecord.putAll(
                [
                        hash:           session.hashCode(),
                        native_id:      pid as String,
                        process:        'head job',
                        name:           session.getRunName() + '-' + headJobSuffix,
                        status:         'STARTED',
                        start:          System.currentTimeMillis(),
                        attempt:        headJobRecord.store.get('attempt', 0) + 1
                ]
        )
    }

    /**
     * Create a finalized head job specific {@link TraceRecord} from the current samples.
     */
    TraceRecord report() {
        long endTimestamp = System.currentTimeMillis()

        // Reduce the process to values
        headProcesses.each({ OSProcess p -> p.updateAttributes() })

        headJobRecord.putAll(
                [
                        status:         'COMPLETED',
                        complete:       endTimestamp,
                        duration:       endTimestamp - (headJobRecord.get('submit') as long),
                        realtime:       runtimeBean.uptime,
                        memory:         Runtime.getRuntime().maxMemory(),
                        '%cpu':         headProcesses.sum({OSProcess p -> p.getProcessCpuLoadCumulative()}) as double * 100,
                        read_bytes:     headProcesses.sum({ OSProcess p -> p.bytesRead}) as Long,
                        write_bytes:    headProcesses.sum({ OSProcess p -> p.bytesWritten}) as Long,
                        vol_ctxt:       headProcesses.sum({ OSProcess p -> p.minorFaults}) as Long,
                        inv_ctxt:       headProcesses.sum({ OSProcess p -> p.majorFaults}) as Long,
                ] 
        )

        if (samples) {
            List<Long> rss = samples.collect({ MemorySample sample -> sample.rssBytes})
            List<Long> vmem = samples.collect({ MemorySample sample -> sample.virtualMemoryBytes})
            headJobRecord.putAll(
                    [
                            memory:         rss.average(),
                            rss:            rss.average(),
                            vmem:           vmem.average(),
                            peak_rss:       rss.max(),
                            peak_vmem:      vmem.max(),
                    ]
            )
        }

        return headJobRecord
    }

    /**
     * Stop the sampling and finish accumulating the information in the TraceRecord.
     */
    void stop() {
        timer.cancel()
        timer.purge()
    }

    /**
     * A predicate to exclude Nextflow Task Runs
     */
    Closure<OSProcess> noNextflowTaskRuns = { OSProcess process -> !process.commandLine.contains(TaskRun.CMD_RUN) }

    /**
     * Collect all descendants that are part of the head job recursively by excluding Nextflow Task Runs
     * 
     * @param process The root process for which the head job descendents are searched
     * @return All descendent processes that are not associated with tasks
     */
    List<OSProcess> collectHeadDescendants(OSProcess process) {
        List<OSProcess> allChildren = os.getChildProcesses(process.processID, null, null, 0)
        
        // Collect information on process child relationship
        headProcesses.add(process)
        
        List<OSProcess> headChildren = allChildren.findAll( noNextflowTaskRuns )
        List<OSProcess> headDescendents = [process]
        
        headChildren.each { OSProcess childProcess ->
            headDescendents.addAll( collectHeadDescendants(childProcess) )
        }
        
        return headDescendents
    }

    /**
     * Sample memory information and update children.
     */
    void sample(OSProcess process=rootProcess) {
        // Update root process information
        process.updateAttributes()
        
        // Collect active descendant head job processes
        List<OSProcess> activeProcesses = collectHeadDescendants(process)

        if (process != null) {
            MemorySample sample = new MemorySample(
                    timestamp: System.currentTimeMillis(),
                    
                    // Memory - The RSS value gives the best approximation for allocated resources by the head job
                    rssBytes: activeProcesses.sum({ OSProcess p -> p.residentSetSize}) as Long,
                    virtualMemoryBytes: activeProcesses.sum({ OSProcess p -> p.virtualSize}) as Long,
            )

            samples.add(sample)
        }
    }
}
