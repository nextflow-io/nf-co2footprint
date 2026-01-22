package nextflow.co2footprint

import nextflow.NextflowMeta
import nextflow.Session
import nextflow.co2footprint.TestHelpers.LogChecker
import nextflow.executor.NopeExecutor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.WorkflowMetadata
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.Executors

class CO2FootprintPluginTest extends Specification{
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
        observer.onProcessStart(taskHandler, traceRecord)
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
        setup:
        LogChecker logChecker = new LogChecker(CO2FootprintObserver)

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
        logChecker.checkLogs(null, [
            'No output files are enabled - to enable, set `enabled: true` in the sections `trace`, `summary` or `report`.'
        ])
    }
}