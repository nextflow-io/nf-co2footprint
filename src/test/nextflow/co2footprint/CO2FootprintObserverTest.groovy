package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.NextflowMeta
import nextflow.Session
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.TestHelpers.FileChecker
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

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.Executors

@Slf4j
class CO2FootprintObserverTest extends Specification{

    // ------ TEST UTILITY METHODS ------
    @Shared
    FileChecker fileChecker = new FileChecker('/observer')

    @Shared
    def traceRecord = new TraceRecord()

    def setupSpec() {
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

    private static BigDecimal round( double value ) {
        Math.round( value * 100 ) / 100
    }

    /**
     * Helper to create a mock session with a specific CI value.
     */
    private Session mockSessionWithCI(Path tracePath, Path summaryPath, Path reportPath, Path provenancePath, double ciValue) {
        return Mock(Session) {
            getConfig() >> [
                co2footprint: [
                    'trace': ['enabled': true, 'file': tracePath],
                    'summary': ['enabled': true, 'file': summaryPath],
                    'report': ['enabled': true, 'file': reportPath],
                    'provenance': [enabled: true, file: provenancePath],
                    'ci': ciValue
                ]
            ]
        }
    }

    // ------ BASIC FUNCTIONALITY TESTS ------

    def 'should return observer' () {
        when:
        Session session = Mock(Session) { getConfig() >> [:] }
        List<TraceObserverV2> result = new CO2FootprintFactory().create(session)

        then:
        result.size() == 1
        result[0] instanceof CO2FootprintObserver
    }

    // ------ FULL RUN CALCULATION TESTS ------
    // The expected results were compared with the results from https://calculator.green-algorithms.org (v2.2), where the following values were used:
    // - Running time: 1h
    // - Type of cores: CPU
    // - Number of cores: 1
    // - Model: Any
    // - Memory available: 7 GB
    // - Platform used: Personal computer
    // - Location: world
    // - Usage factor: 1

    def 'test full run calculation of total CO2e and energy consumption with specific CI' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Path provenancePath = tempPath.resolve('provenance_test.json')

        // Use helper to mock session with CI value 475.0
        Session session = mockSessionWithCI(tracePath, summaryPath, reportPath, provenancePath, 475.0)

        // Create task and handler
        TaskRun task = new TaskRun(id: TaskId.of(111))
        task.processor = Mock(TaskProcessor)
        TaskHandler handler = new NopeExecutor().createTaskHandler(task)

        // Create observer
        CO2FootprintFactory factory = new CO2FootprintFactory()
        CO2FootprintObserver observer = factory.create(session)[0] as CO2FootprintObserver

        observer.onFlowCreate(session)
        observer.onTaskStart(new TaskEvent(handler, traceRecord))
        observer.onTaskComplete(new TaskEvent(handler, traceRecord))
        observer.onFlowComplete()

        expect:
        Double total_co2 =  observer.workflowStats.co2Record.store.CO2e as Double
        Double total_energy =  observer.workflowStats.co2Record.store.energy_consumption as Double
        // With TDP = 11.45 (default global)
        // Energy consumption converted to Wh
        round(total_energy*1000) == 14.02
        // Total CO₂ in g (should reflect the CI value you set)
        round(total_co2) == 6.66
    }

    def 'test full run with CO2e equivalences calculation and specific CI' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Path provenancePath = tempPath.resolve('provenance_test.json')

        // Use helper to mock session with CI value 475.0
        Session session = mockSessionWithCI(tracePath, summaryPath, reportPath, provenancePath, 475.0)

        // Create task and handler
        TaskRun task = new TaskRun(id: traceRecord.getTaskId())
        task.processor = Mock(TaskProcessor)
        TaskHandler handler = new NopeExecutor().createTaskHandler(task)

        // Create observer
        CO2FootprintFactory factory = new CO2FootprintFactory()
        CO2FootprintObserver observer = factory.create(session)[0] as CO2FootprintObserver

        observer.onFlowCreate(session)
        observer.onTaskStart(new TaskEvent(handler, traceRecord))
        observer.onTaskComplete(new TaskEvent(handler, traceRecord))
        observer.onFlowComplete()

        CO2EquivalencesRecord co2EquivalencesRecord = observer
            .getCO2FootprintCalculator()
            .computeCO2footprintEquivalences(observer.workflowStats.co2Record.store.CO2e as BigDecimal)

        expect:
        // Values compared to result from www.green-algorithms.org (1h, 1core, TDP=11.45, CI:475)
        co2EquivalencesRecord.getCarKilometers().round(7) == 0.0380475 as Double
        co2EquivalencesRecord.getTreeMonths().round(7) == 0.007261 as Double
        co2EquivalencesRecord.getPlanePercent().round(7) == 0.0133166 as Double
    }


    // ------ FILE CREATION TESTS ------

    def 'Should create correct trace, summary and report files' () {
        given:
        // Define temporary variables
        OffsetDateTime time = OffsetDateTime.now()
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')
        Path provenancePath = tempPath.resolve('provenance_test.json')

        // Mock Session
        Session session = Mock(Session)
        session.getConfig() >> [
            co2footprint:
                [
                    'trace': [enabled: true, file: tracePath],
                    'summary': [enabled: true, file: summaryPath],
                    'report': [enabled: true, file: reportPath],
                    'provenance': [enabled: true, file: provenancePath]
                ]
        ]
        session.getExecService() >> Executors.newFixedThreadPool(1)
        WorkflowMetadata meta = Mock(WorkflowMetadata)
        meta.toMap() >> [
                scriptId: 'MOCK',
                start: time,
                complete: time,
                nextflow: NextflowMeta.instance
        ]
        session.getWorkflowMetadata() >> meta

        // Create task
        TaskRun task = new TaskRun(id: TaskId.of(111))
        task.processor = Mock(TaskProcessor)
        TaskHandler taskHandler = new NopeExecutor().createTaskHandler(task)

        // Create Observer
        CO2FootprintObserver observer = new CO2FootprintFactory().create(session)[0] as CO2FootprintObserver

        when:
        // Run necessary observer steps
        observer.onFlowCreate(session)
        observer.onTaskStart(new TaskEvent(taskHandler, traceRecord))
        observer.onTaskComplete(new TaskEvent(taskHandler, traceRecord))
        observer.onFlowComplete()
        observer.renderFiles()

        then:
        // Check Trace File
        fileChecker.runChecks(tracePath)
        
        // Check Summary File
        fileChecker.runChecks(summaryPath, [
                'provenanceFile: (.+)$': [provenancePath.toString()],
                'reportFile: (.+)$': [reportPath.toString()],
                'summaryFile: (.+)$': [summaryPath.toString()],
                'traceFile: (.+)$': [tracePath.toString()],
        ])

        // Check Report File
        fileChecker.runChecks(reportPath, [
                ('\\{"option":"provenanceFile","value":"([^"]*)"\\}.*?\\{"option":"reportFile","value":"([^"]*)"\\}.*?' + 
                 '\\{"option":"summaryFile","value":"([^"]*)"\\}.*?\\{"option":"traceFile","value":"([^"]*)"\\}'): [
                        provenancePath.toString(), reportPath.toString(), summaryPath.toString(), tracePath.toString()
                ],
                '<span id="workflow_start">(.*?)</span> - <span id="workflow_complete">(.*?)</span>' : [
                        time.format('dd-MMM-YYYY HH:mm:ss'), time.format('dd-MMM-YYYY HH:mm:ss')
                ]
        ])
        
        // Check provenance file
        fileChecker.runChecks(provenancePath)
    }
}
