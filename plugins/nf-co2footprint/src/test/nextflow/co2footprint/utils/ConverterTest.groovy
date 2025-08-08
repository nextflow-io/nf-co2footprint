package nextflow.co2footprint.utils

import spock.lang.Specification

class ConverterTest extends Specification  {
    def 'Should convert correct between times'() {
        when:
        BigDecimal out = Converter.convertTime(value, unit, targetUnit)

        then:
        out == expected

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
        value   || unit     || smallestUnit     || largestUnit  || threshold          || expected
        1.0     || 'days'   ||  'h'             ||  'days'      || 0.0                || '1day'
        2.1     || 'days'   ||  'min'           ||  'days'      || null               || '2days 2h 24min'
        2.52    || 'days'   ||  'days'          ||  'days'      || null               || '2.52days'
        3600.0  || 's'      ||  's'             ||  'min'       || null               || '60min'
        7000    || 'ms'     ||  'ms'            ||  's'         || null               || '7s'
        7500    || 'ms'     ||  'ms'            ||  's'         || null               || '7s 500ms'
        3602.1  || 's'      ||  's'             ||  'min'       || null               || '60min 2.1s'
        
    }

}
