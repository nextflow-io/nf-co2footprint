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
        1024    || ''       || null         ||  1       || 'k'
    }

    def 'Should convert bytes to readable Strings'() {
        when:
        String out = new Bytes(value, scale).toReadable(targetScale, precision)

        then:
        out == expected

        where:
        value   || scale    || targetScale  || precision    || expected
        1024    || ''       || null         || 0            || '1 kB'
        1024**3 || ''       || null         || 0            || '1 GB'
    }
}
