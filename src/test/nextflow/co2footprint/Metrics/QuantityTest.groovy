package nextflow.co2footprint.Metrics

import spock.lang.Specification

class QuantityTest  extends Specification {

    def 'test creation' () {
        when:
        Quantity quantity = new Quantity(value, scale, unit)
        quantity.separator = separator

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

    def 'Should convert correct between units'() {
        when:
        Quantity out = new Quantity(value, scale, unit).scale(targetScale)

        then:
        out.value == expected
        out.scale == targetScale

        where:
        value   || scale    || unit     || targetScale  || expected
        1.0     || ''       || 'g'      || 'k'          ||  0.001
        1.0     || 'M'      || 't'      || ''           ||  1000000.0
    }

    def 'Should convert quantities to readable Strings'() {
        when:
        String out = new Quantity(value, scale, unit).toReadable(targetScale, precision)

        then:
        out == expected

        where:
        value   || scale    || unit     || targetScale  || precision    || expected
        1.0     || ''       || 'g'      || 'k'          || 3            || '0.001 kg'
        1.0     || 'M'      || 't'      || ''           || 2            || '1000000 t'
        1.11    || ''       || ''       || 'k'          || 4            || '0.0011 k'
    }
}
