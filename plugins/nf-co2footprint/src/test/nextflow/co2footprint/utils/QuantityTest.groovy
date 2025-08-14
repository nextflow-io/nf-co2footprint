package nextflow.co2footprint.utils

import spock.lang.Specification

class QuantityTest  extends Specification {

    def 'test creation' () {
        when:
        Quantity quantity = new Quantity(value, scale, unit, separator)

        then:
        quantity.getReadable(keepDecimal) == expectedReadable

        where:
        value   || scale    || unit     || separator|| keepDecimal  || expectedReadable
        0       || ''       || ''       || ' '      || false        || '0'
        0       || ''       || ''       || ' '      || true         || '0.0'
        1.0     || ''       || ''       || ' '      || false        || '1'
        1.0     || ''       || ''       || ' '      || true         || '1.0'
        60.0    || 'm'      || 'W'      || ' '      || false        || '60 mW'
        60.0    || 'm'      || 'W'      || ' '      || true         || '60.0 mW'
        60.0    || 'm'      || 'W'      || ''       || true         || '60.0mW'
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
