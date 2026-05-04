package nextflow.co2footprint.Records

import nextflow.co2footprint.TestHelpers.FileChecker
import nextflow.trace.TraceRecord
import org.yaml.snakeyaml.Yaml
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class CO2RecordTreeTest extends Specification {
    @Shared
    TraceRecord traceRecord = new TraceRecord()
    @Shared
    CO2Record co2Record

    @Shared
    CO2RecordTree recordsTree = new CO2RecordTree('workflow', [level: 'workflow'])

    @Shared
    Yaml yaml = new Yaml()

    FileChecker fileChecker = new FileChecker('/tree')

    def setupSpec() {
        traceRecord.putAll([
            'task_id'  : '111',
            'process'  : 'testProcess',
            'realtime' : (1 as Long) * (3600000 as Long), // 1 h
            'cpus'     : 1,
            'cpu_model': "Unknown model",
            '%cpu'     : 100.0,
            'memory'   : (7 as Long) * (1000**3 as Long), // 7 GB
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
                    100.0, 1000**3, 1.0d, 1, 12, 'Unknown model', 5.0d, 5.0d
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
        'workflow'  || ['CO2e']             || [workflow: [CO2e: [0.0, 1.0, 2.0]]]
        'process'   || ['CO2e']             || [process1: [CO2e:[0.0, 1.0]], process2: [CO2e:[2.0]]]
        'process'   || ['CO2e_non_cached']  || [process1: [CO2e_non_cached:[0.0, 1.0]]]
        'workflow'  || null                 || [workflow: [
                task_id:['111', '111', '111'],
                process:['testProcess', 'testProcess', 'testProcess'],
                realtime:[3600000.0000, 3600000.0000, 3600000.0000],
                cpus:[1, 1, 1],
                cpu_model:['Unknown model', 'Unknown model', 'Unknown model'],
                '%cpu':[100.0, 100.0, 100.0],
                memory:[1000000000, 1000000000, 1000000000],
                status:['COMPLETED', 'COMPLETED', 'CACHED'],
                name:['task_1', 'task_2', 'task_3'],
                energy_consumption:[0.0, 1.0, 2.0],
                CO2e:[0.0, 1.0, 2.0],
                CO2e_market:[null, null, null],
                carbon_intensity:[475.0, 475.0, 475.0],
                powerdraw_cpu:[12.0, 12.0, 12.0],
                raw_energy_processor:[5.0, 5.0, 5.0],
                raw_energy_memory:[5.0, 5.0, 5.0],
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

        Map parentMap = parentNode.toMap(true, false, false)
        String yamlLines = yaml.dump(parentMap)
        Path outPath = Path.of(this.class.getResource('.').toURI()).complete().resolve('tree').resolve('out')
        outPath.createParentDirectories()
        outPath.createDirIfNotExists()
        Path treePath = outPath.resolve('test_tree.yaml')
        treePath.write(yamlLines)

        then:
        fileChecker.runChecks(treePath)
    }
}
