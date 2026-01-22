package nextflow.co2footprint.Records


import nextflow.trace.TraceRecord
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification

class CO2RecordTreeTest extends Specification {
    @Shared
    TraceRecord traceRecord = new TraceRecord()
    @Shared
    CO2Record co2Record

    @Shared
    CO2RecordTree recordsTree = new CO2RecordTree('workflow', [level: 'workflow'])

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
                1.0, 10, 1.0, 1, 7.0, 'Some model', 5.0d, 5.0d
        )

        CO2RecordTree process1 = recordsTree.addChild(new CO2RecordTree('process1', [level: 'process']))
        CO2RecordTree process2 = recordsTree.addChild(new CO2RecordTree('process2', [level: 'process']))

        int counter = 0
        [[0.0d, 'COMPLETED'], [1.0d, 'COMPLETED'], [2.0d, 'CACHED']].each { Double value, String status ->
            counter += 1
            TraceRecord traceRecord2 = new TraceRecord()
            traceRecord2.putAll(traceRecord.store)
            traceRecord2.putAll([name: "task_${counter}", status: status])

            CO2RecordTree process = counter > 2 ? process2 : process1
            process.addChild(new CO2RecordTree("task_${counter}", [level: 'task'],
                new CO2Record(
                    traceRecord2, value, value, null, 475.0,
                    100.0, 1024**3, 1.0d, 1, 12, 'Unknown model', 5.0d, 5.0d
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
                time:[1.0, 1.0, 1.0],
                powerdrawCPU:[12.0, 12.0, 12.0],
                rawEnergyProcessor:[5.0, 5.0, 5.0],
                rawEnergyMemory:[5.0, 5.0, 5.0],
        ]]
    }

    def 'Should construct a valid tree node'() {
        when:
        CO2RecordTree parentNode = new CO2RecordTree('crazy_tesla', [level: 'workflow'])
        CO2RecordTree processNode1 = parentNode.addChild(new CO2RecordTree('test1', [level: 'process']))
        CO2RecordTree processNode2 = parentNode.addChild(new CO2RecordTree('test2', [level: 'process']))
        processNode1.addChild(new CO2RecordTree('1', [level: 'task'], co2Record))
        processNode2.addChild(new CO2RecordTree('2', [level: 'task'], co2Record))
        parentNode.summarize()

        then:
        Map parentMap = parentNode.toMap(true)
        String yamlString = yaml.dump(parentMap)
        String expectedYamlString = this.class.getResource('/test_tree.yaml').text
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
