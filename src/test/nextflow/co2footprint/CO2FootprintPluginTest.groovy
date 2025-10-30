package nextflow.co2footprint

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import nextflow.NextflowMeta
import nextflow.Session
import nextflow.executor.NopeExecutor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.Executors

class CO2FootprintPluginTest extends Specification{
    static LoggerContext lc = LoggerFactory.getILoggerFactory() as LoggerContext
    @Shared
    Logger logger
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>()

    @Shared
    CO2FootprintFactory factory = new CO2FootprintFactory()

    @Shared
    OffsetDateTime now = OffsetDateTime.now()
    @Shared
    TaskRun taskRun
    @Shared
    TaskHandler taskHandler
    @Shared
    def traceRecord = new TraceRecord()

    def setupSpec() {
        // Get Logger
        logger = lc.getLogger('ROOT')

        // Create task
        taskRun = new TaskRun(id: TaskId.of(111))
        taskRun.processor = Mock(TaskProcessor)
        taskHandler = new NopeExecutor().createTaskHandler(taskRun)

        // Make trace record
        traceRecord.putAll(
                [
                        'task_id': '111',
                        'process': 'observerTestProcess',
                        'realtime': (1 as Long) * (3600000 as Long), // 1 h
                        'cpus': 1,
                        'cpu_model': "Unknown model",
                        '%cpu': 100.0,
                        'memory': (7 as Long) * (1024**3 as Long), // 7 GB
                        'status': 'COMPLETED'
                ]
        )
    }

    def setup() {
        logger.setLevel(Level.INFO)
        listAppender.start()
        logger.addAppender(listAppender)
    }

    def cleanup() {
        listAppender.list.clear()
        logger.detachAndStopAllAppenders()
        listAppender.stop()
    }

    /**
     * Mock a session with given parameters.
     *
     * @param config Configuration of the session
     * @return A Session Mock
     */
    Session mockSession(Map config=[:]) {
        Session session = Mock(Session)
        session.getExecService() >> Executors.newFixedThreadPool(1)
        WorkflowMetadata meta = Mock(WorkflowMetadata)
        meta.scriptId >> 'MOCK'
        meta.start >> now
        meta.complete >> now
        meta.nextflow >> NextflowMeta.instance
        session.getWorkflowMetadata() >> meta
        session.config >> config

        return session
    }

    /**
     * @return A list with booleans indicating the existence of files
     */
    List<Boolean> filesExist(Path tracePath, Path summaryPath,Path reportPath) {
        return [tracePath, summaryPath, reportPath].collect({ Path path -> path.isFile() })
    }

    /**
     * Run all steps to create the out files.
     *
     * @return The list of observers
     */
    List<TraceObserver> createFiles(Session session) {
        List<TraceObserver> observers = factory.create(session)
        CO2FootprintObserver observer = observers[0] as CO2FootprintObserver

        // Run necessary observer steps
        observer.onFlowCreate(session)
        observer.onProcessComplete(taskHandler, traceRecord)
        observer.onFlowComplete()

        return observers
    }

    def 'Empty configuration'() {
        when:
        Session session = mockSession()
        Collection<TraceObserver> observers = factory.create(session)

        then:
        observers.size() == 1
    }

    def 'Creation of all files'() {
        when:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Map config = [
            co2footprint: [
                'trace': ['enabled': true, 'file': tracePath],
                'summary': ['enabled': true, 'file': summaryPath],
                'report': ['enabled': true, 'file': reportPath]
            ]
        ]
        Session session = mockSession(config)

        Collection<TraceObserver> observers = createFiles(session)

        then:
        observers.size() == 1
        filesExist(tracePath, summaryPath, reportPath) == [true, true, true]
    }

    def 'Creation of some files'() {
        when:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Map config = [
            co2footprint: [
                'trace': ['enabled': true, 'file': tracePath],
                'summary': ['enabled': false, 'file': summaryPath],
                'report': ['enabled': true, 'file': reportPath]
            ]
        ]
        Session session = mockSession(config)

        Collection<TraceObserver> observers = createFiles(session)

        then:
        observers.size() == 1
        filesExist(tracePath, summaryPath, reportPath) == [true, false, true]
    }

    def 'Creation of no files'() {
        when:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Map config = [
            co2footprint: [
                'trace': ['enabled': false, 'file': tracePath],
                'summary': ['enabled': false, 'file': summaryPath],
                'report': ['enabled': false, 'file': reportPath]
            ]
        ]
        Session session = mockSession(config)

        Collection<TraceObserver> observers = createFiles(session)

        then:
        observers.size() == 1
        filesExist(tracePath, summaryPath, reportPath) == [false, false, false]
        listAppender.list.size() == 1
        listAppender.list[0] == (
            "No output files are enabled - set 'enabled: true' in the sections 'trace', 'summary' and 'report' to " +
            "turn these on"
        )
    }
}