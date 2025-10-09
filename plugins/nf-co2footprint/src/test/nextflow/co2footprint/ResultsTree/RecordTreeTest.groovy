package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Records.CO2Record
import nextflow.trace.TraceRecord
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Stream

class RecordTreeTest extends Specification {
    @Shared
    TraceRecord traceRecord = new TraceRecord()

    def setupSpec() {
        traceRecord.putAll([
                'task_id'  : '111',
                'process'  : 'observerTestProcess',
                'realtime' : (1 as Long) * (3600000 as Long), // 1 h
                'cpus'     : 1,
                'cpu_model': "Unknown model",
                '%cpu'     : 100.0,
                'memory'   : (7 as Long) * (1024**3 as Long), // 7 GB
                'status'   : 'COMPLETED'
        ])
    }

    def 'Should construct a valid tree node'() {
        setup:
        Yaml yaml = new Yaml()

        CO2Record co2Record = new CO2Record(
                traceRecord, 2.0, 200.0, null, 100.0,
                1.0, 10, 60*60*1000, 1, 7.0, 'Some model'
        )

        when:
        RecordTree parentNode = new RecordTree('parent', [level: 'parent'])
        parentNode.addChild(new RecordTree('child1', [level: 'child'], co2Record))
        parentNode.addChild(new RecordTree('child2', [level: 'child'], co2Record))
        parentNode.summarize()

        then:
        Map parentMap = parentNode.toMap()
        String yamlString = yaml.dump(parentMap)
        String expectedYamlString = 'name: parent\n' +
                'attributes: {level: parent}\n' +
                'values:\n' +
                '  task_id:\n' +
                '    raw:\n' +
                '      value: !!set {\'111\': null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: \'[111]\'\n' +
                '  process:\n' +
                '    raw:\n' +
                '      value: !!set {observerTestProcess: null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: \'[observerTestProcess]\'\n' +
                '  realtime:\n' +
                '    raw: {value: 7200000, type: Long}\n' +
                '    readable: 2h\n' +
                '  cpus:\n' +
                '    raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '    readable: \'1\'\n' +
                '  cpu_model:\n' +
                '    raw:\n' +
                '      value: !!set {Some model: null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: \'[Some model]\'\n' +
                '  \'%cpu\':\n' +
                '    raw: {value: 200.0, type: BigDecimal, scale: \'%\', unit: \'\'}\n' +
                '    readable: 200.0%\n' +
                '  memory:\n' +
                '    raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '    readable: 10 GB\n' +
                '  status:\n' +
                '    raw:\n' +
                '      value: !!set {COMPLETED: null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: <span class="badge badge-null">[COMPLETED]</span>\n' +
                '  energy:\n' +
                '    raw: {value: 4000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '    readable: 4 kWh\n' +
                '  co2e:\n' +
                '    raw: {value: 400.0, type: Double, scale: \'\', unit: g}\n' +
                '    readable: 400 g\n' +
                '  co2eMarket:\n' +
                '    raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '    readable: \'-\'\n' +
                '  ci:\n' +
                '    raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '    readable: 100 gCO₂e/kWh\n' +
                '  cpuUsage:\n' +
                '    raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '    readable: 1 %\n' +
                '  time:\n' +
                '    raw: {value: 25920000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '    readable: 25920000000s\n' +
                '  powerdrawCPU:\n' +
                '    raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '    readable: 7 W\n' +
                'children:\n' +
                '- name: child1\n' +
                '  attributes: {level: child}\n' +
                '  values:\n' +
                '    task_id:\n' +
                '      raw: {value: \'111\', type: String}\n' +
                '      readable: \'111\'\n' +
                '    process:\n' +
                '      raw: {value: observerTestProcess, type: String}\n' +
                '      readable: observerTestProcess\n' +
                '    realtime:\n' +
                '      raw: {value: 3600000, type: Long}\n' +
                '      readable: 1h\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '      readable: \'1\'\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
                '    \'%cpu\':\n' +
                '      raw: {value: 100.0, type: BigDecimal, scale: \'%\', unit: \'\'}\n' +
                '      readable: 100.0%\n' +
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    status:\n' +
                '      raw: {value: COMPLETED, type: String}\n' +
                '      readable: <span class="badge badge-success">COMPLETED</span>\n' +
                '    energy:\n' +
                '      raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '      readable: 2 kWh\n' +
                '    co2e:\n' +
                '      raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '      readable: 200 g\n' +
                '    co2eMarket:\n' +
                '      raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '      readable: \'-\'\n' +
                '    ci:\n' +
                '      raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '      readable: 100 gCO₂e/kWh\n' +
                '    cpuUsage:\n' +
                '      raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '      readable: 1 %\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '  children: []\n' +
                '- name: child2\n' +
                '  attributes: {level: child}\n' +
                '  values:\n' +
                '    task_id:\n' +
                '      raw: {value: \'111\', type: String}\n' +
                '      readable: \'111\'\n' +
                '    process:\n' +
                '      raw: {value: observerTestProcess, type: String}\n' +
                '      readable: observerTestProcess\n' +
                '    realtime:\n' +
                '      raw: {value: 3600000, type: Long}\n' +
                '      readable: 1h\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '      readable: \'1\'\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
                '    \'%cpu\':\n' +
                '      raw: {value: 100.0, type: BigDecimal, scale: \'%\', unit: \'\'}\n' +
                '      readable: 100.0%\n' +
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    status:\n' +
                '      raw: {value: COMPLETED, type: String}\n' +
                '      readable: <span class="badge badge-success">COMPLETED</span>\n' +
                '    energy:\n' +
                '      raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '      readable: 2 kWh\n' +
                '    co2e:\n' +
                '      raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '      readable: 200 g\n' +
                '    co2eMarket:\n' +
                '      raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '      readable: \'-\'\n' +
                '    ci:\n' +
                '      raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '      readable: 100 gCO₂e/kWh\n' +
                '    cpuUsage:\n' +
                '      raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '      readable: 1 %\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '  children: []\n'
        List<String> lines = yamlString.readLines()
        List<String> expectedLines = expectedYamlString.readLines()
        int lineCounter = 1
        while (lines) {
            String line = lines.pop()
            String expectedLine = expectedLines.pop()
            assert line == expectedLine, "Mismatch in line ${lineCounter}:\nActual  : ${line}\nExpected: ${expectedLine}\n\nComplete:\n${yamlString}"
            lineCounter += 1
        }
    }
}
