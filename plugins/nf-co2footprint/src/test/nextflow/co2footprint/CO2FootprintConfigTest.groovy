package nextflow.co2footprint

import spock.lang.Specification
import groovy.util.logging.Slf4j
import java.util.concurrent.ConcurrentHashMap

@Slf4j
class CO2FootprintConfigTest extends Specification {
    def 'config should be rejected for several cases' () {
        when:
        new CO2FootprintConfig(input, new TDPDataMatrix(), [:])

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
        CIDataMatrix ci = new CIDataMatrix(
                [['DE', 300]],
                ['Zone id', 'Carbon intensity gCO₂eq/kWh (Life cycle)'] as LinkedHashSet,
                [1] as LinkedHashSet
        )

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(input, tdp, ci)

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
        CO2FootprintConfig config = new CO2FootprintConfig(pluginConfig, tdp, processConfig)

        then:
        keys.each({property ->
            config.getProperty(property) == pluginConfig.get(property)
        }).every()
        tdp.fallbackModel == "default ${config.getProperty('machineType')}"
        config.getPue() == pue

        where:
        pluginConfig                            || processConfig            || keys                     || pue
        ['machineType': 'compute cluster']      || [:]                      || ['machineType']          || 1.67
        ['machineType': 'local']                || [:]                      || ['machineType']          || 1.0
        [:]                                     || ['executor': 'local']    || ['machineType']          || 1.0
        [:]                                     || ['executor': 'awsbatch'] || ['machineType']          || 1.67
        ['machineType': 'local', 'pue': 2.0]    || [:]                      || ['machineType', 'pue']   || 2.0
        ['pue': 2.0]                            || ['executor': 'awsbatch'] || ['machineType', 'pue']   || 2.0
    }

    def 'test dynamic ci computation with GLOBAL fallback'() {
        setup:
        // Create a TDPDataMatrix
        TDPDataMatrix tdp = new TDPDataMatrix(
                [[1]],
                ['tdp (W)'] as LinkedHashSet,
                ['default'] as LinkedHashSet
        )

        // Create a CIDataMatrix with multiple zones, including GLOBAL
        CIDataMatrix ci = new CIDataMatrix(
                [['DE', 300], ['US', 400], ['FR', 250], ['GLOBAL', 400]],
                ['Zone id', 'Carbon intensity gCO₂eq/kWh (Life cycle)'] as LinkedHashSet,
                [1, 2, 3, 4] as LinkedHashSet
        )

        when:
        // Create a config with valid locations
        CO2FootprintConfig configDE = new CO2FootprintConfig(['location': 'DE'], tdp, ci)
        CO2FootprintConfig configUS = new CO2FootprintConfig(['location': 'US'], tdp, ci)
        CO2FootprintConfig configFR = new CO2FootprintConfig(['location': 'FR'], tdp, ci)

        // Create a config with an invalid location
        CO2FootprintConfig configInvalid = new CO2FootprintConfig(['location': 'INVALID'], tdp, ci)

        then:
        // Ensure 'ci' is dynamically computed for valid locations
        assert configDE.ci instanceof Closure
        assert configDE.ci() == 300.0 // Matches the value in the CIDataMatrix for 'DE'

        assert configUS.ci instanceof Closure
        assert configUS.ci() == 400.0 // Matches the value in the CIDataMatrix for 'US'

        assert configFR.ci instanceof Closure
        assert configFR.ci() == 250.0 // Matches the value in the CIDataMatrix for 'FR'

        // Ensure 'ci' falls back to GLOBAL for invalid locations
        assert configInvalid.ci instanceof Closure
        assert configInvalid.ci() == 400.0 // Falls back to the GLOBAL value

        // Verify other properties are not affected
        assert configDE.location == 'DE'
        assert configUS.location == 'US'
        assert configFR.location == 'FR'
        assert configInvalid.location == 'INVALID'

        // Ensure default values for other properties
        validateDefaultProperties(configDE)
    }

    // Helper method to validate default properties
    private void validateDefaultProperties(CO2FootprintConfig config) {
        assert config.powerdrawMem == 0.3725
        assert config.pue == 1.67
    }
}
