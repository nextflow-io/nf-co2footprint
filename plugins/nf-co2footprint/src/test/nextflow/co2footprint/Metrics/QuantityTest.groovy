package nextflow.co2footprint.Metrics

import spock.lang.Specification

class QuantityTest  extends Specification {

    def 'test creation' () {
        when:
        Quantity quantity = new Quantity(value, scale, unit, separator)

        then:
        quantity.getReadable() == expectedReadable

        where:
        value   || scale    || unit     || separator|| expectedReadable
        0       || ''       || ''       || ' '      || '0'
        1.0     || ''       || ''       || ' '      || '1'
        60.0    || 'm'      || 'W'      || ' '      || '60 mW'
        1.1     || ''       || 'W'      || ''       || '1.1W'
    }

    def 'test flooring' () {
        when:
        Quantity quantity = new Quantity(value)

        then:
        quantity.floor(precision).value == expectedFloor

        where:
        value   || precision || expectedFloor
        0.0     || 0         || 0
        1.0     || 0         || 1
        1.49    || 0         || 1
        1.49    || 1         || 1.4
    }


    def 'test rounding' () {
        when:
        Quantity quantity = new Quantity(value)

        then:
        quantity.round(precision).value == expectedRound

        where:
        value   || precision || expectedRound
        0.0     || 0         || 0
        1.0     || 0         || 1
        1.491   || 2         || 1.49
        1.5     || 0         || 2
    }
}
