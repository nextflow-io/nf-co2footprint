package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Records.CO2Record
import nextflow.trace.TraceRecord
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification

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
                'test co2', 2.0, 200.0, null, 100.0,
                1.0, 10, 60*60*1000, 1, 7.0, 'Some model'
        )

        when:
        RecordTree parentNode = new RecordTree('parent', [level: 'root'])
        RecordTree childNode1 = parentNode.addChild(new RecordTree('child', [level: 'child']))
        RecordTree childNode2 = parentNode.addChild(new RecordTree('child', [level: 'child']))
        childNode1.addChild(new RecordTree('co2', [level: 'record'], co2Record))
        childNode1.addChild(new RecordTree('trace', [level: 'record'], traceRecord))
        childNode2.addChild(new RecordTree('co2', [level: 'record'], co2Record))
        childNode2.addChild(new RecordTree('trace', [level: 'record'], traceRecord))
        parentNode.summarize()

        then:
        Map parentMap = parentNode.toMap()
        String yamlString = yaml.dump(parentMap)
        yamlString == 'name: parent\n' +
                'attributes: {level: root}\n' +
                'values:\n' +
                '  name:\n' +
                '    raw:\n' +
                '      value: !!set {test co2: null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: \'[test co2]\'\n' +
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
                '  memory:\n' +
                '    raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '    readable: 10 GB\n' +
                '  time:\n' +
                '    raw: {value: 25920000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '    readable: 25920000000s\n' +
                '  cpus:\n' +
                '    raw: {value: 1, type: Integer}\n' +
                '    readable: \'1\'\n' +
                '  powerdrawCPU:\n' +
                '    raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '    readable: 7 W\n' +
                '  cpu_model:\n' +
                '    raw:\n' +
                '      value: !!set {Some model: null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: \'[Some model]\'\n' +
                'children:\n' +
                '- name: child\n' +
                '  attributes: {level: child}\n' +
                '  values:\n' +
                '    name:\n' +
                '      raw: {value: test co2, type: String}\n' +
                '      readable: test co2\n' +
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
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer}\n' +
                '      readable: \'1\'\n' +
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
                '  children:\n' +
                '  - name: co2\n' +
                '    attributes: {level: record}\n' +
                '    values:\n' +
                '      name:\n' +
                '        raw: {value: test co2, type: String}\n' +
                '        readable: test co2\n' +
                '      energy:\n' +
                '        raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '        readable: 2 kWh\n' +
                '      co2e:\n' +
                '        raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '        readable: 200 g\n' +
                '      co2eMarket:\n' +
                '        raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '        readable: \'-\'\n' +
                '      ci:\n' +
                '        raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '        readable: 100 gCO₂e/kWh\n' +
                '      cpuUsage:\n' +
                '        raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '        readable: 1 %\n' +
                '      memory:\n' +
                '        raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '        readable: 10 GB\n' +
                '      time:\n' +
                '        raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '        readable: 12960000000s\n' +
                '      cpus:\n' +
                '        raw: {value: 1, type: Integer}\n' +
                '        readable: \'1\'\n' +
                '      powerdrawCPU:\n' +
                '        raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '        readable: 7 W\n' +
                '      cpu_model:\n' +
                '        raw: {value: Some model, type: String}\n' +
                '        readable: Some model\n' +
                '    children: []\n' +
                '  - name: trace\n' +
                '    attributes: {level: record}\n' +
                '    values:\n' +
                '      task_id:\n' +
                '        raw: {value: \'111\', type: String}\n' +
                '        readable: \'111\'\n' +
                '      process:\n' +
                '        raw: {value: observerTestProcess, type: String}\n' +
                '        readable: observerTestProcess\n' +
                '      realtime:\n' +
                '        raw: {value: 3600000, type: Long, scale: \'\', unit: ms}\n' +
                '        readable: 1h\n' +
                '      cpus:\n' +
                '        raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '        readable: \'1\'\n' +
                '      cpu_model:\n' +
                '        raw: {value: Unknown model, type: String}\n' +
                '        readable: Unknown model\n' +
                '      \'%cpu\':\n' +
                '        raw: {value: 100.0, type: BigDecimal, scale: \'%\', unit: \'\'}\n' +
                '        readable: 100.0%\n' +
                '      memory:\n' +
                '        raw: {value: 7516192768, type: Long, scale: \'\', unit: B}\n' +
                '        readable: 7 GB\n' +
                '      status:\n' +
                '        raw: {value: COMPLETED, type: String}\n' +
                '        readable: <span class="badge badge-success">COMPLETED</span>\n' +
                '    children: []\n' +
                '- name: child\n' +
                '  attributes: {level: child}\n' +
                '  values:\n' +
                '    name:\n' +
                '      raw: {value: test co2, type: String}\n' +
                '      readable: test co2\n' +
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
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer}\n' +
                '      readable: \'1\'\n' +
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
                '  children:\n' +
                '  - name: co2\n' +
                '    attributes: {level: record}\n' +
                '    values:\n' +
                '      name:\n' +
                '        raw: {value: test co2, type: String}\n' +
                '        readable: test co2\n' +
                '      energy:\n' +
                '        raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '        readable: 2 kWh\n' +
                '      co2e:\n' +
                '        raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '        readable: 200 g\n' +
                '      co2eMarket:\n' +
                '        raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '        readable: \'-\'\n' +
                '      ci:\n' +
                '        raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '        readable: 100 gCO₂e/kWh\n' +
                '      cpuUsage:\n' +
                '        raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '        readable: 1 %\n' +
                '      memory:\n' +
                '        raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '        readable: 10 GB\n' +
                '      time:\n' +
                '        raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '        readable: 12960000000s\n' +
                '      cpus:\n' +
                '        raw: {value: 1, type: Integer}\n' +
                '        readable: \'1\'\n' +
                '      powerdrawCPU:\n' +
                '        raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '        readable: 7 W\n' +
                '      cpu_model:\n' +
                '        raw: {value: Some model, type: String}\n' +
                '        readable: Some model\n' +
                '    children: []\n' +
                '  - name: trace\n' +
                '    attributes: {level: record}\n' +
                '    values:\n' +
                '      task_id:\n' +
                '        raw: {value: \'111\', type: String}\n' +
                '        readable: \'111\'\n' +
                '      process:\n' +
                '        raw: {value: observerTestProcess, type: String}\n' +
                '        readable: observerTestProcess\n' +
                '      realtime:\n' +
                '        raw: {value: 3600000, type: Long, scale: \'\', unit: ms}\n' +
                '        readable: 1h\n' +
                '      cpus:\n' +
                '        raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '        readable: \'1\'\n' +
                '      cpu_model:\n' +
                '        raw: {value: Unknown model, type: String}\n' +
                '        readable: Unknown model\n' +
                '      \'%cpu\':\n' +
                '        raw: {value: 100.0, type: BigDecimal, scale: \'%\', unit: \'\'}\n' +
                '        readable: 100.0%\n' +
                '      memory:\n' +
                '        raw: {value: 7516192768, type: Long, scale: \'\', unit: B}\n' +
                '        readable: 7 GB\n' +
                '      status:\n' +
                '        raw: {value: COMPLETED, type: String}\n' +
                '        readable: <span class="badge badge-success">COMPLETED</span>\n' +
                '    children: []\n'
    }
}
