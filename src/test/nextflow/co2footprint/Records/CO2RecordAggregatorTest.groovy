package nextflow.co2footprint.Records

import nextflow.co2footprint.ResultsTree.RecordTree
import spock.lang.Shared
import spock.lang.Specification
import nextflow.trace.TraceRecord

class CO2RecordAggregatorTest extends Specification{
    @Shared
    RecordTree recordsTree = new RecordTree('workflow', [level: 'workflow'])

    def setupSpec() {
        RecordTree process1 = recordsTree.addChild(new RecordTree('process1', [level: 'process']))
        RecordTree process2 = recordsTree.addChild(new RecordTree('process2', [level: 'process']))

        TraceRecord traceRecord = new TraceRecord()
        traceRecord.putAll(
                [
                    'task_id'  : 't1',
                    'process'  : 'testProcess',
                    'realtime' : (1 as Long) * (3600000 as Long), // 1 h
                    'cpus'     : 1,
                    'cpu_model': "Unknown model",
                    '%cpu'     : 100.0,
                    'memory'   : (7 as Long) * (1024**3 as Long) // 7 GB
                ]
        )

        int counter = 0
        [[0.0d, 'COMPLETED'], [1.0d, 'COMPLETED'], [2.0d, 'CACHED']].each { Double value, String status ->
            TraceRecord tr = new TraceRecord()
            tr.putAll(traceRecord.store)
            tr.put('status', status)

            counter += 1
            RecordTree process = counter > 2 ? process2 : process1
            RecordTree taskTree = process.addChild(new RecordTree("testTask_${counter}", [level: 'task']))
            taskTree.addChild(new RecordTree('trace', [level: 'record'], tr))
            taskTree.addChild(new RecordTree('co2e', [level: 'record'],
                new CO2Record(
                    "testTask_${counter}", value, value, null, 475.0,
                    100.0, 1024**3, 1.0d, 1, 12, 'Unknown model', status, 0.5d, 0.5d
                )
            ))
        }

    }

    def 'Test level collection'() {
        when:
        Map<String, Map<String, Object>> recordsMap = CO2RecordAggregator.collectByLevel(recordsTree, level, valueKeys)

        then:
        recordsMap == expectedRecordsMap

        where:
        level       || valueKeys            || expectedRecordsMap
        'workflow'  || ['co2e']             || [workflow: [co2e: [0.0, 1.0, 2.0]]]
        'process'   || ['co2e']             || [process1: [co2e:[0.0, 1.0]], process2: [co2e:[2.0]]]
        'process'   || ['co2e_non_cached']  || [process1: [co2e_non_cached:[0.0, 1.0]]]
        'workflow'  || null                 || [workflow: [
                task_id:['t1', 't1', 't1'],
                process:['testProcess', 'testProcess', 'testProcess'],
                realtime:[3600000, 3600000, 3600000],
                cpus:[1, 1, 1],
                cpu_model:['Unknown model', 'Unknown model', 'Unknown model'],
                '%cpu':[100.0, 100.0, 100.0],
                memory:[1073741824, 1073741824, 1073741824],
                status:['COMPLETED', 'COMPLETED', 'CACHED'],
                name:['testTask_1', 'testTask_2', 'testTask_3'],
                energy:[0.0, 1.0, 2.0],
                co2e:[0.0, 1.0, 2.0],
                co2eMarket:[null, null, null],
                ci:[475.0, 475.0, 475.0],
                cpuUsage:[100.0, 100.0, 100.0],
                time:['0', '0', '0'],
                powerdrawCPU:[12.0, 12.0, 12.0]
        ]]
    }
}
