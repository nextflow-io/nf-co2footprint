package nextflow.co2footprint

import nextflow.co2footprint.CO2RecordAggregator.QuantileItem

import spock.lang.Shared
import spock.lang.Specification
import nextflow.trace.TraceRecord

class CO2ReportAggregatorTest extends Specification{
    @Shared
    CO2RecordAggregator co2RecordAggregator = new CO2RecordAggregator()

    @Shared
    TraceRecord traceRecord = new TraceRecord()

    @Shared
    List<Map<String, TraceRecord>> records

    def setupSpec() {
        traceRecord.putAll(
                [
                        'task_id': '111',
                        'process': 'observerTestProcess',
                        'realtime': (1 as Long) * (3600000 as Long), // 1 h
                        'cpus': 1,
                        'cpu_model': "Unknown model",
                        '%cpu': 100.0,
                        'memory': (7 as Long) * (1024**3 as Long) // 7 GB
                ]
        )
        records = [0.0d, 1.0d, 2.0d].collect { Double value ->
            [
                    traceRecord: traceRecord,
                    co2Record: new CO2Record(
                            value, value, 1.0d, 475.0, 1, 12, 100.0, 1024**3, 'testTask', 'Unknown model'
                    )
            ]
        }
    }

    def 'Test quantile computation' () {
        when:
        List<QuantileItem> quantiles = ['min': 0d, 'q1': .25d, 'q2': .50d, 'q3': .75d, 'max': 1d].collect { String key, double q ->
            co2RecordAggregator.getQuantile(
                    records,
                    q,
                    { TraceRecord traceRecord, CO2Record co2Record -> co2Record.getCO2e() }
            )
        }

        then:
        quantiles.collect {QuantileItem it -> it.getValue()} == [0.0, 0.5, 1.0, 1.5, 2.0]
    }

}
