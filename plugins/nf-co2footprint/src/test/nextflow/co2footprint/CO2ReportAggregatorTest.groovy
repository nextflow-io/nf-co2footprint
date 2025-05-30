package nextflow.co2footprint

import nextflow.co2footprint.CO2RecordAggregator.QuantileItem

import spock.lang.Shared
import spock.lang.Specification

class CO2ReportAggregatorTest extends Specification{
    @Shared
    CO2RecordAggregator co2RecordAggregator = new CO2RecordAggregator()

    @Shared
    List<CO2Record> co2Records = [0.0d, 1.0d, 2.0d].collect { Double value ->
        new CO2Record(
                value, value, 1.0d, 475.0, 1, 12, 100.0, 1024**3, 'testTask', 'Unknown model'
    )}

    def 'Test quantile computation' () {
        when:
        List<QuantileItem> quantiles = ['min': 0d, 'q1': .25d, 'q2': .50d, 'q3': .75d, 'max': 1d].collect { String key, double q ->
            co2RecordAggregator.getQuantile(
                    co2Records,
                    q,
                    { CO2Record co2Record -> co2Record.getCO2e() }
            )
        }

        then:
        quantiles.collect {QuantileItem it -> it.getValue()} == [0.0, 0.5, 1.0, 1.5, 2.0]
    }

}
