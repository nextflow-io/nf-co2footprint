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
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord
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
    FileChecker fileChecker = new FileChecker()

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
                 'memory': (7 as Long) * (1024**3 as Long), // 7 GB
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
    private Session mockSessionWithCI(Path tracePath, Path summaryPath, Path reportPath, double ciValue) {
        return Mock(Session) {
            getConfig() >> [
                co2footprint: [
                    'trace': ['enabled': true, 'file': tracePath],
                    'summary': ['enabled': true, 'file': summaryPath],
                    'report': ['enabled': true, 'file': reportPath],
                    'ci': ciValue
                ]
            ]
        }
    }

    // ------ BASIC FUNCTIONALITY TESTS ------

    def 'should return observer' () {
        when:
        Session session = Mock(Session) { getConfig() >> [:] }
        List<TraceObserver> result = new CO2FootprintFactory().create(session)

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

        // Use helper to mock session with CI value 475.0
        Session session = mockSessionWithCI(tracePath, summaryPath, reportPath, 475.0)

        // Create task and handler
        TaskRun task = new TaskRun(id: TaskId.of(111))
        task.processor = Mock(TaskProcessor)
        TaskHandler handler = new NopeExecutor().createTaskHandler(task)

        // Create observer
        CO2FootprintFactory factory = new CO2FootprintFactory()
        CO2FootprintObserver observer = factory.create(session)[0] as CO2FootprintObserver

        observer.onFlowCreate(session)
        observer.onProcessStart(handler, traceRecord)
        observer.onProcessComplete(handler, traceRecord)
        observer.onFlowComplete()

        expect:
        Double total_co2 =  observer.workflowStats.co2Record.store.co2e as Double
        Double total_energy =  observer.workflowStats.co2Record.store.energy as Double
        // With TDP = 11.45 (default global)
        // Energy consumption converted to Wh
        round(total_energy*1000) == 14.02
        // Total CO₂ in g (should reflect the CI value you set)
        round(total_co2) == 6.66
    }

    def 'test full run with co2e equivalences calculation and specific CI' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('report_test.html')

        // Use helper to mock session with CI value 475.0
        Session session = mockSessionWithCI(tracePath, summaryPath, reportPath, 475.0)

        // Create task and handler
        TaskRun task = new TaskRun(id: traceRecord.getTaskId())
        task.processor = Mock(TaskProcessor)
        TaskHandler handler = new NopeExecutor().createTaskHandler(task)

        // Create observer
        CO2FootprintFactory factory = new CO2FootprintFactory()
        CO2FootprintObserver observer = factory.create(session)[0] as CO2FootprintObserver

        observer.onFlowCreate(session)
        observer.onProcessStart(handler, traceRecord)
        observer.onProcessComplete(handler, traceRecord)
        observer.onFlowComplete()

        CO2EquivalencesRecord co2EquivalencesRecord = observer
            .getCO2FootprintComputer()
            .computeCO2footprintEquivalences(observer.workflowStats.co2Record.store.co2e as Double)

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

        // Mock Session
        Session session = Mock(Session)
        session.getConfig() >> [
            co2footprint:
                [
                    'trace': [enabled: true, file: tracePath],
                    'summary': [enabled: true, file: summaryPath],
                    'report': [enabled: true, file: reportPath]
                ]
        ]
        session.getExecService() >> Executors.newFixedThreadPool(1)
        WorkflowMetadata meta = Mock(WorkflowMetadata)
        meta.scriptId >> 'MOCK'
        meta.start >> time
        meta.complete >> time
        meta.nextflow >> NextflowMeta.instance
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
        observer.onProcessStart(taskHandler, traceRecord)
        observer.onProcessComplete(taskHandler, traceRecord)
        observer.onFlowComplete()

        then:
        //
        // Check Trace File
        //
        fileChecker.checkIsFile(tracePath)
        List<String> traceLines = tracePath.readLines()
        traceLines.size() == 2

        List<String> headers = traceLines[0].split('\t') as List<String>
        List<String> values = traceLines[1].split('\t') as List<String>
        headers.size() == values.size()

        headers == [
                'task_id', 'status', 'name', 'energy_consumption', 'CO2e', 'CO2e_market', 'carbon_intensity', '%cpu',
                'memory', 'realtime', 'cpus', 'powerdraw_cpu', 'cpu_model', 'rawEnergyProcessor', 'rawEnergyMemory',
        ]
        values == [
            '111', 'COMPLETED', '-', '14.02 Wh', '6.73 g', '-', '480 gCO₂e/kWh', '100 %',
            '7 GB', '1h', '1', '11.41 W', 'Unknown model', '11.41 Wh', '2.61 Wh',
        ] // GA: CO₂e is 6.94g with CI of 475 gCO₂eq/kWh

        fileChecker.compareChecksums(tracePath, 'a9face6b58356811b2f77c87f6542b70')


        // Check Summary File
        fileChecker.runChecks(
                summaryPath,
                [
                        27: "  reportFile: ${reportPath}",
                        28: "  summaryFile: ${summaryPath}",
                        29: "  traceFile: ${tracePath}"
                ]
        )

        // Check Report File
        fileChecker.runChecks(
            reportPath,
            [
                1005: '    window.options = [' +
                        '{"option":"ci","value":"480.0"},'+
                        '{"option":"ciMarket","value":null},' +
                        '{"option":"customCpuTdpFile","value":null},' +
                        '{"option":"ignoreCpuModel","value":"false"},' +
                        '{"option":"location","value":null},' +
                        '{"option":"machineType","value":null},' +
                        '{"option":"powerdrawCpuDefault","value":null},' +
                        '{"option":"powerdrawMem","value":"0.3725"},' +
                        '{"option":"pue","value":"1.0"},' +
                        "{\"option\":\"reportFile\",\"value\":\"${reportPath}\"}," +
                        "{\"option\":\"summaryFile\",\"value\":\"${summaryPath}\"}," +
                        "{\"option\":\"traceFile\",\"value\":\"${tracePath}\"}];",
                1056: '          ' +
                        "<span id=\"workflow_start\">${time.format('dd-MMM-YYYY HH:mm:ss')}</span>" +
                        " - <span id=\"workflow_complete\">${time.format('dd-MMM-YYYY HH:mm:ss')}</span>"
            ]
        )
    }
}
