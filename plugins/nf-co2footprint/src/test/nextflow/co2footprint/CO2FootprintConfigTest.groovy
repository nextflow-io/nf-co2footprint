package nextflow.co2footprint

import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap

class CO2FootprintConfigTest extends Specification {
    def 'config should be rejected for several cases' () {
        when:
        new CO2FootprintConfig(input, new TDPDataMatrix())

        then:
        Exception e = thrown(expectedException)
        e.getMessage().contains(message)

        where:
        input                                                   || expectedException        || message
        ['location': 'Tuebingen']  as ConcurrentHashMap         || IllegalArgumentException || 'Invalid \'location\' parameter: Tuebingen.'
    }

    def 'test configuration builder' () {
        setup:
        TDPDataMatrix tdp = new TDPDataMatrix(
                [[1]],
                ['tdp (W)'] as LinkedHashSet,
                ['default'] as LinkedHashSet
        )

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(input, tdp)

        then:
        keys.each({property ->
            config.getProperty(property) == input.get(property)
        }).every()

        where:
        input                                               || keys
        ['ci': 100.0] as ConcurrentHashMap                  || ['ci']
        ['location': 'DE', 'pue': 2.0] as ConcurrentHashMap || ['location', 'pue']

    }

    def 'fallback value should change after configuring valid machine type' () {
        setup:
        TDPDataMatrix tdp = new TDPDataMatrix(
                [[1]],
                ['tdp (W)'] as LinkedHashSet,
                ['default'] as LinkedHashSet
        )

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(input, tdp)

        then:
        keys.each({property ->
            config.getProperty(property) == input.get(property)
        }).every()
        tdp.fallbackModel == "default ${config.getProperty('machineType')}"
        config.getPue() == pue

        where:
        input                                                       || keys                     || pue
        ['machineType': 'server'] as ConcurrentHashMap              || ['machineType']          || 1.67
        ['machineType': 'local'] as ConcurrentHashMap               || ['machineType']          || 1.0
        ['machineType': 'local', 'pue': 2.0] as ConcurrentHashMap   || ['machineType', 'pue']   || 2.0
    }
}
