package nextflow.co2footprint.utils

import spock.lang.Specification

class HelperFunctionsTest extends Specification  {
    def 'Should convert time to readable Strings'() {
        when:
        String out = HelperFunctions.convertTimeToReadableUnits(
                value, unit,
                smallestUnit, largestUnit,
                smallestValue, maximumSteps
        )

        then:
        out == expected

        where:
        value   || unit     || smallestUnit     || largestUnit  || smallestValue    || maximumSteps     || expected
        1.0     || 'days'   ||  'h'             ||  'days'      || 0.0              || null             || '1day'
        2.1     || 'days'   ||  'min'           ||  'days'      || null             || null             || '2days 2h 24.000min'
        3600.0  || 's'      ||  's'             ||  'min'       || null             || 1                || '60min'
        3602.1  || 's'      ||  's'             ||  'min'       || null             || null             || '60min 2.1000s'
    }
}
