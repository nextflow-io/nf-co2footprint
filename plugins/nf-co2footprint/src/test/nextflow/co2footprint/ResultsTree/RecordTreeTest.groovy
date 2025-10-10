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
    @Shared
    CO2Record co2Record

    @Shared
    RecordTree recordsTree = new RecordTree('workflow', [level: 'workflow'])

    @Shared
    Yaml yaml = new Yaml()

    def setupSpec() {
        traceRecord.putAll([
            'task_id'  : '111',
            'process'  : 'testProcess',
            'realtime' : (1 as Long) * (3600000 as Long), // 1 h
            'cpus'     : 1,
            'cpu_model': "Unknown model",
            '%cpu'     : 100.0,
            'memory'   : (7 as Long) * (1024**3 as Long), // 7 GB
            'status'   : 'COMPLETED'
        ])

        co2Record = new CO2Record(
                traceRecord, 2.0, 200.0, null, 100.0,
                1.0, 10, 60*60*1000, 1, 7.0, 'Some model'
        )

        RecordTree process1 = recordsTree.addChild(new RecordTree('process1', [level: 'process']))
        RecordTree process2 = recordsTree.addChild(new RecordTree('process2', [level: 'process']))

        int counter = 0
        [[0.0d, 'COMPLETED'], [1.0d, 'COMPLETED'], [2.0d, 'CACHED']].each { Double value, String status ->
            counter += 1
            TraceRecord traceRecord2 = new TraceRecord()
            traceRecord2.putAll(traceRecord.store)
            traceRecord2.putAll([name: "task_${counter}", status: status])

            RecordTree process = counter > 2 ? process2 : process1
            process.addChild(new RecordTree("task_${counter}", [level: 'task'],
                new CO2Record(
                    traceRecord2, value, value, null, 475.0,
                    100.0, 1024**3, 1.0d, 1, 12, 'Unknown model'
                )
            ))
        }
    }

    def 'Test level collection'() {
        when:
        Map<String, Map<String, Object>> recordsMap = recordsTree.collectByLevel(level, valueKeys)

        then:
        recordsMap == expectedRecordsMap

        where:
        level       || valueKeys            || expectedRecordsMap
        'workflow'  || ['co2e']             || [workflow: [co2e: [0.0, 1.0, 2.0]]]
        'process'   || ['co2e']             || [process1: [co2e:[0.0, 1.0]], process2: [co2e:[2.0]]]
        'process'   || ['co2e_non_cached']  || [process1: [co2e_non_cached:[0.0, 1.0]]]
        'workflow'  || null                 || [workflow: [
                task_id:['111', '111', '111'],
                process:['testProcess', 'testProcess', 'testProcess'],
                realtime:[3600000, 3600000, 3600000],
                cpus:[1, 1, 1],
                cpu_model:['Unknown model', 'Unknown model', 'Unknown model'],
                '%cpu':[100.0, 100.0, 100.0],
                memory:[1073741824, 1073741824, 1073741824],
                status:['COMPLETED', 'COMPLETED', 'CACHED'],
                name:['task_1', 'task_2', 'task_3'],
                energy:[0.0, 1.0, 2.0],
                co2e:[0.0, 1.0, 2.0],
                co2eMarket:[null, null, null],
                ci:[475.0, 475.0, 475.0],
                cpuUsage:[100.0, 100.0, 100.0],
                time:['0', '0', '0'],
                powerdrawCPU:[12.0, 12.0, 12.0]
        ]]
    }

    def 'Should construct a valid tree node'() {
        when:
        RecordTree parentNode = new RecordTree('parent', [level: 'parent'])
        parentNode.addChild(new RecordTree('child1', [level: 'child'], co2Record))
        parentNode.addChild(new RecordTree('child2', [level: 'child'], co2Record))
        parentNode.summarize()

        then:
        Map parentMap = parentNode.toMap(true)
        String yamlString = yaml.dump(parentMap)
        String expectedYamlString = 'name: parent\n' +
                'attributes: {level: parent}\n' +
                'values:\n' +
                '  name:\n' +
                '    raw: {value: null, type: str}\n' +
                '    readable: \'-\'\n' +
                '  cpus:\n' +
                '    raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '    readable: \'1\'\n' +
                '  memory:\n' +
                '    raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '    readable: 10 GB\n' +
                '  time:\n' +
                '    raw: {value: 25920000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '    readable: 25920000000s\n' +
                '  cpu_model:\n' +
                '    raw:\n' +
                '      value: !!set {Some model: null}\n' +
                '      type: LinkedHashSet\n' +
                '    readable: \'[Some model]\'\n' +
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
                '  powerdrawCPU:\n' +
                '    raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '    readable: 7 W\n' +
                'children:\n' +
                '- name: child1\n' +
                '  attributes: {level: child}\n' +
                '  values:\n' +
                '    name:\n' +
                '      raw: {value: null, type: str}\n' +
                '      readable: \'-\'\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '      readable: \'1\'\n' +
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
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
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '  children: []\n' +
                '- name: child2\n' +
                '  attributes: {level: child}\n' +
                '  values:\n' +
                '    name:\n' +
                '      raw: {value: null, type: str}\n' +
                '      readable: \'-\'\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer, scale: \'\', unit: \'\'}\n' +
                '      readable: \'1\'\n' +
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
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
