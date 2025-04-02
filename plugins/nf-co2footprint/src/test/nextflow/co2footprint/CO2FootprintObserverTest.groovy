package nextflow.co2footprint

import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CO2FootprintObserverTest extends Specification{

    @Shared
    def traceRecord = new TraceRecord()

    @Shared
    TaskHandler taskHandler = Mock(TaskHandler) {task >> ['id': new TaskId(0)] }

    def setupSpec() {
        traceRecord.realtime = 1 as Long
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Example model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = 1024 as Long
    }

    def 'Should create correct trace file' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('summary_test.txt')
        Session session = Mock(Session) { getConfig() >>
                [co2footprint:
                         [
                                 'traceFile': tracePath,
                                 'summaryFile': summaryPath,
                                 'reportFile': reportPath
                         ]
                ]
        }
        List<TraceObserver> observers = new CO2FootprintFactory().create(session)
        TraceObserver textFileObserver = observers[0]
        TraceObserver reportFileObserver = observers[1]

        when:
        textFileObserver.onFlowCreate(session)
        textFileObserver.onProcessComplete(taskHandler, traceRecord)
        textFileObserver.onFlowComplete()

        then:
        Files.isRegularFile(tracePath)
        List<String> traceLines = Files.readAllLines(tracePath)
        traceLines.size() == 2
        traceLines[0].split('\t') as List<String> == [
                'task_id', 'status', 'name', 'energy_consumption', 'CO2e', 'time', 'cpus', 'powerdraw_cpu',
                'cpu_model', 'cpu_usage', 'requested_memory'
        ]
        traceLines[1].split('\t') as List<String> == [
                '0', 'null', 'null', '5.57 uWh', '2.64 ug', '2.778E-7ms', '1', '12.0', 'Example model', '100.0', '0.0 B'
        ]
    }

    def 'Should create correct txt file' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('summary_test.txt')
        Session session = Mock(Session) { getConfig() >>
                [co2footprint:
                         [
                                 'traceFile': tracePath,
                                 'summaryFile': summaryPath,
                                 'reportFile': reportPath
                         ]
                ]
        }
        List<TraceObserver> observers = new CO2FootprintFactory().create(session)
        TraceObserver textFileObserver = observers[0]

        when:
        textFileObserver.onFlowCreate(session)
        textFileObserver.onProcessComplete(taskHandler, traceRecord)
        textFileObserver.onFlowComplete()

        then:
        Files.isRegularFile(summaryPath)
        List<String> summaryLines = Files.readAllLines(summaryPath)
        summaryLines.size() == 25
        summaryLines[2] == 'Energy consumption: 5.57 uWh'
        summaryLines[5] == '- 1.51E-8 km travelled by car'
        summaryLines[17] == "summaryFile: ${summaryPath}"
        summaryLines[24] == 'pue: 1.67'
    }

    def 'Should create a html file' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')
        Path summaryPath = tempPath.resolve('summary_test.txt')
        Path reportPath = tempPath.resolve('summary_test.txt')
        Session session = Mock(Session) { getConfig() >>
                [co2footprint:
                         [
                                 'traceFile': tracePath,
                                 'summaryFile': summaryPath,
                                 'reportFile': reportPath
                         ]
                ]
        }
        List<TraceObserver> observers = new CO2FootprintFactory().create(session)
        TraceObserver reportFileObserver = observers[1]

        when:
        // TODO

        then:
        // TODO

    }
}
