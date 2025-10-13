package nextflow.co2footprint.Metrics

import spock.lang.Specification

class CalculatorTest extends Specification {
    def 'Should calculate weighted averages' () {
        expect:
        Calculator.weightedAverage(values, weights) == weightedAverage

        where:
        values          || weights          || weightedAverage
        [1.0]           || [1.0]            || 1.0
        [1.0]           || [0.1]            || 1.0
        [1.0, 3.0]      || [1.0, 1.0]       || 2.0
        [1.0, null]     || [1.0, null]      || 1.0
        [null, 2]       || [null, 2]        || 2.0
        [null, null]    || [null, null]     || null
        [1.0, 1.0, 1.0] || [0.1, 0.5, 0.4]  || 1.0
    }
}
