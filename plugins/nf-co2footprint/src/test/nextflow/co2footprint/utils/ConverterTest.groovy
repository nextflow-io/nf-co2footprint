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
                smallestValue, maximumSteps
        )

        then:
        out == expected

        where:
        value   || unit     || smallestUnit     || largestUnit  || smallestValue    || maximumSteps     || expected
        1.0     || 'days'   ||  'h'             ||  'days'      || 0.0              || null             || '1day'
        2.1     || 'days'   ||  'min'           ||  'days'      || null             || null             || '2days 2h 24min'
        3600.0  || 's'      ||  's'             ||  'min'       || null             || 1                || '60min'
        3602.1  || 's'      ||  's'             ||  'min'       || null             || null             || '60min 2s'
        3602.1  || 's'      ||  's'             ||  'min'       || null             || 1                || '60min'
    }
}
