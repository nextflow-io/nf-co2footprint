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
import nextflow.trace.TraceObserverV2
import nextflow.trace.TraceRecord
import nextflow.trace.event.TaskEvent
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.Executors

@Stepwise
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
                        'memory': (7 as Long) * (1000**3 as Long), // 7 GB
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
    List<Boolean> filesExist(Path tracePath, Path summaryPath,Path reportPath, Path provenancePath) {
        return [tracePath, summaryPath, reportPath, provenancePath].collect({ Path path -> path.isFile() })
    }

    /**
     * Run all steps to create the out files.
     *
     * @return The list of observers
     */
    List<TraceObserverV2> createFiles(Session session) {
        List<TraceObserverV2> observers = factory.create(session)
        CO2FootprintObserver observer = observers[0] as CO2FootprintObserver

        // Run necessary observer steps
        observer.onFlowCreate(session)
        observer.onTaskStart(new TaskEvent(taskHandler, traceRecord))
        observer.onTaskComplete(new TaskEvent(taskHandler, traceRecord))
        observer.onFlowComplete()
        observer.renderFiles()

        return observers
    }

    def 'check version' () {
        when:
        String pluginVersion = CO2FootprintPlugin.readPluginVersion()

        then:
        pluginVersion == "1.2.1"
    }

    def 'Empty configuration'() {
        when:
        Session session = mockSession()
        Collection<TraceObserverV2> observers = factory.create(session)

        then:
        observers.size() == 1
    }

    def 'Creation of all files'() {
        when:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Path provenancePath = tempPath.resolve('provenance_test.json')
        Map config = [
            co2footprint: [
                'trace': ['enabled': true, 'file': tracePath],
                'summary': ['enabled': true, 'file': summaryPath],
                'report': ['enabled': true, 'file': reportPath],
                'provenance': [enabled: true, file: provenancePath]
            ]
        ]
        Session session = mockSession(config)

        Collection<TraceObserverV2> observers = createFiles(session)

        then:
        observers.size() == 1
        filesExist(tracePath, summaryPath, reportPath, provenancePath) == [true, true, true, true]
    }

    def 'Creation of some files'() {
        when:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Path provenancePath = tempPath.resolve('provenance_test.json')
        Map config = [
                co2footprint: [
                        'trace': ['enabled': true, 'file': tracePath],
                        'summary': ['enabled': false, 'file': summaryPath],
                        'report': ['enabled': true, 'file': reportPath],
                        'provenance': [enabled: false, file: provenancePath]
                ]
        ]
        Session session = mockSession(config)

        Collection<TraceObserverV2> observers = createFiles(session)

        then:
        observers.size() == 1
        filesExist(tracePath, summaryPath, reportPath, provenancePath) == [true, false, true, false]
    }

    def 'Creation of no files'() {
        setup:
        LogChecker logChecker = new LogChecker(CO2FootprintObserver)

        when:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Path provenancePath = tempPath.resolve('provenance_test.json')
        Map config = [
                co2footprint: [
                        'trace': ['enabled': false, 'file': tracePath],
                        'summary': ['enabled': false, 'file': summaryPath],
                        'report': ['enabled': false, 'file': reportPath],
                        'provenance': [enabled: false, file: provenancePath]
                ]
        ]
        Session session = mockSession(config)

        Collection<TraceObserverV2> observers = createFiles(session)

        then:
        observers.size() == 1
        filesExist(tracePath, summaryPath, reportPath, provenancePath) == [false, false, false, false]
        logChecker.checkLogs(null, [
            'No output files are enabled - to enable, set `enabled: true` in the sections `trace`, `summary` or `report`.'
        ])
    }
}