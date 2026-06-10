package nextflow.co2footprint.Metrics

import spock.lang.Specification

class BytesTest extends Specification {
    def 'Should convert correct between bytes'() {
        when:
        Bytes out = new Bytes(value, scale).scale(targetScale)

        then:
        out.value == expected
        out.scale == expectedScale
        out.unit == 'B'

        where:
        value   || scale    || targetScale  || expected || expectedScale
        1000    || ''       || 'k'         ||  1       || 'k'
        1024    || ''       || 'Ki'         ||  1       || 'Ki'
    }

    def 'Should convert bytes to readable Strings'() {
        when:
        String out = new Bytes(value, scale).toReadable(targetScale, precision)

        then:
        out == expected

        where:
        value   || scale    || targetScale  || precision    || expected
        1000    || ''       || null         || 0            || '1 kB'
        1000**3 || ''       || null         || 0            || '1 GB'
    }
}
