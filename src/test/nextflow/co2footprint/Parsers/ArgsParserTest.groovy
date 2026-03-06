package nextflow.co2footprint.Parsers

import spock.lang.Specification

class ArgsParserTest extends Specification {
    def 'Test arguments parsing'() {
        when:
        Map<String, Object> args = ArgsParser.parse(['--a', '1', '2', '--b', '3', '--c'])

        then:
        args == [a: ['1', '2'], b: '3', c: true]
    }
}
