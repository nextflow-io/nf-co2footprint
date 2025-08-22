package nextflow.co2footprint.Metrics

import spock.lang.Specification

class ConverterTest extends Specification  {
    def 'Should convert correct between units'() {
        when:
        Quantity out = Converter.scaleUnits(value as Double, scale, unit, targetScale)

        then:
        out.value == expected
        out.unit == unit

        where:
        value   || scale    || unit     || targetScale  || expected
        1.0     || ''       || 'g'      || 'k'          ||  0.001
        1.0     || 'M'      || 't'      || ''           ||  1000000.0
        1024    || ''       || 'B'      || null         ||  1
    }

    def 'Should convert time to readable Strings'() {
        when:
        String out = Converter.toReadableUnits(value as Double, scale, unit, targetScale, precision)

        then:
        out == expected

        where:
        value   || scale    || unit     || targetScale  || precision    || expected
        1.0     || ''       || 'g'      || 'k'          || 3            || '0.001 kg'
        1.0     || 'M'      || 't'      || ''           || 2            || '1000000 t'
        1.11    || ''       || ''       || 'k'          || 4            || '0.0011 k'
        1024    || ''       || 'B'      || null         || 0            || '1 kB'
        1024**3 || ''       || 'B'      || null         || 0            || '1 GB'
    }

    def 'Should convert correct between times'() {
        when:
        Quantity out = Converter.scaleTime(value, unit, targetUnit)

        then:
        out.value == expected

        where:
        value   || unit         || targetUnit   || expected
        1.0     || 'years'      || 'months'     ||  12.0
        24.0    || 'months'     || 'years'      ||  2.0
        1.0     || 'days'       || 's'          ||  86400.0
        3600.0  || 's'          || 'h'          ||  1.0

    }

    def 'Should convert time to readable Strings'() {
        when:
        String out = Converter.toReadableTimeUnits(
                value, unit,
                smallestUnit, largestUnit,
                threshold
        )

        then:
        out == expected

        where:
        value   || unit     || smallestUnit     || largestUnit  || threshold    || expected
        1.0     || 'days'   ||  'h'             ||  'days'      || 0.0          || '1day'
        2.1     || 'days'   ||  'min'           ||  'days'      || 0.0          || '2days 2h 24min'
        2.52    || 'days'   ||  'days'          ||  'days'      || 0.0          || '2.52days'
        3600.0  || 's'      ||  's'             ||  'min'       || 0.0          || '60min'
        7000    || 'ms'     ||  'ms'            ||  's'         || 0.0          || '7s'
        7500    || 'ms'     ||  'ms'            ||  's'         || 0.0          || '7s 500ms'
        3602.1  || 's'      ||  's'             ||  'min'       || 0.0          || '60min 2.1s'
    }
}
