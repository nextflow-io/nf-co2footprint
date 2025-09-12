package nextflow.co2footprint.Records


import nextflow.co2footprint.Records.CO2RecordAggregator.QuantileItem

import spock.lang.Shared
import spock.lang.Specification
import nextflow.trace.TraceRecord

class CO2RecordAggregatorTest extends Specification{
    @Shared
    CO2RecordAggregator co2RecordAggregator = new CO2RecordAggregator()

    @Shared
    TraceRecord traceRecord = new TraceRecord()

    @Shared
    List<Map<String, TraceRecord>> records

    def setupSpec() {
        traceRecord.putAll(
                [
                        'task_id'  : '111',
                        'process'  : 'observerTestProcess',
                        'realtime' : (1 as Long) * (3600000 as Long), // 1 h
                        'cpus'     : 1,
                        'cpu_model': "Unknown model",
                        '%cpu'     : 100.0,
                        'memory'   : (7 as Long) * (1024**3 as Long) // 7 GB
                ]
        )
        int counter = 0
        records = [[0.0d, 'COMPLETED'], [1.0d, 'COMPLETED'], [2.0d, 'CACHED']].collect { Double value, String status ->
            TraceRecord tr = new TraceRecord()
            tr.putAll(traceRecord.getStore())
            tr.put('status', status)
            counter += 1
            [
                    traceRecord:tr,
                    co2Record  : new CO2Record(
                            "testTask_${counter}", value, value, null, 475.0, 100.0, 1024**3, 1.0d, 1, 12, 'Unknown model', 0.5d, 0.5d
                    )
            ]
        }
    }

    def 'Test quantile computation'() {
        when:
        List<QuantileItem> quantiles = ['min': 0d, 'q1': .25d, 'q2': .50d, 'q3': .75d, 'max': 1d].collect { String key, double q ->
            co2RecordAggregator.getQuantile(
                    records,
                    q,
                    { TraceRecord traceRecord, CO2Record co2Record -> co2Record.co2e }
            )
        }

        then:
        quantiles.collect { QuantileItem it -> it.getValue() } == [0.0, 0.5, 1.0, 1.5, 2.0]
    }

    def 'Test stat computation'() {
        when:
        Map<String, ?> stats = co2RecordAggregator.computeStats(records)

        then:
        stats == [
                co2e:               [all: [0.0, 1.0, 2.0], total: 3.0, mean: 1.0, minLabel: 'testTask_1', min: 0.0, q1Label: 'testTask_1', q1: 0.5, q2Label: 'testTask_2', q2: 1.0, q3Label: 'testTask_2', q3: 1.5, maxLabel: 'testTask_3', max: 2.0],
                energy:             [all: [0.0, 1.0, 2.0], total: 3.0, mean: 1.0, minLabel: 'testTask_1', min: 0.0, q1Label: 'testTask_1', q1: 0.5, q2Label: 'testTask_2', q2: 1.0, q3Label: 'testTask_2', q3: 1.5, maxLabel: 'testTask_3', max: 2.0],
                co2e_non_cached:    [all: [0.0, 1.0], total: 1.0, mean: 0.5, minLabel: 'testTask_1', min: 0.0, q1Label: 'testTask_1', q1: 0.25, q2Label: 'testTask_1', q2: 0.5, q3Label: 'testTask_1', q3: 0.75, maxLabel: 'testTask_2', max: 1.0],
                energy_non_cached:  [all: [0.0, 1.0], total: 1.0, mean: 0.5, minLabel: 'testTask_1', min: 0.0, q1Label: 'testTask_1', q1: 0.25, q2Label: 'testTask_1', q2: 0.5, q3Label: 'testTask_1', q3: 0.75, maxLabel: 'testTask_2', max: 1.0],
                co2e_market:        [:],
                energy_market:      [all: [0.0, 1.0, 2.0], total: 3.0, mean: 1.0, minLabel: 'testTask_1', min: 0.0, q1Label: 'testTask_1', q1: 0.5, q2Label: 'testTask_2', q2: 1.0, q3Label: 'testTask_2', q3: 1.5, maxLabel: 'testTask_3', max: 2.0],
        ]
    }
}
