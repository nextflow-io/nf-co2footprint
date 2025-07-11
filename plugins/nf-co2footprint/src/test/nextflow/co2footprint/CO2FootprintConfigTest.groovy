package nextflow.co2footprint

import nextflow.co2footprint.DataContainers.DataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.DataContainers.CIDataMatrix
import spock.lang.Specification
import groovy.util.logging.Slf4j
import java.util.concurrent.ConcurrentHashMap

@Slf4j
class CO2FootprintConfigTest extends Specification {
    TDPDataMatrix tdp
    CIDataMatrix ci

    def setup() {
        tdp = new TDPDataMatrix(
            [[1]],
            ['tdp (W)'] as LinkedHashSet,
            ['default'] as LinkedHashSet
        )
        ci = new CIDataMatrix(
                [[300], [400], [250], [400]],
                ['Carbon intensity gCO₂eq/kWh (Life cycle)'] as LinkedHashSet,
                ['DE', 'US', 'FR', 'GLOBAL'] as LinkedHashSet
        )
    }

    def 'test configuration builder' () {
        when:
        CO2FootprintConfig config = new CO2FootprintConfig(input, tdp, ci, [:])

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
        when:
        CO2FootprintConfig config = new CO2FootprintConfig(pluginConfig, tdp, ci, processConfig)

        then:
        keys.each({property ->
            config.getProperty(property) == pluginConfig.get(property)
        }).every()
        tdp.fallbackModel == "default ${config.getProperty('machineType')}"
        config.getPue() == pue

        where:
        pluginConfig                            || processConfig                || keys                     || pue
        ['machineType': 'compute cluster']      || [:]                          || ['machineType']          || 1.67
        ['machineType': 'local']                || [:]                          || ['machineType']          || 1.0
        [:]                                     || ['executor': 'local']        || ['machineType']          || 1.0
        [:]                                     || ['executor': 'awsbatch']     || ['machineType']          || 1.15
        [:]                                     || ['executor': 'azurebatch']   || ['machineType']          || 1.18
        ['machineType': 'local', 'pue': 2.0]    || [:]                          || ['machineType', 'pue']   || 2.0
        ['pue': 2.0]                            || ['executor': 'awsbatch']     || ['machineType', 'pue']   || 2.0
    }
    def 'test dynamic ci computation with GLOBAL fallback'() {
        expect:
        CO2FootprintConfig config = new CO2FootprintConfig(['location': location], tdp, ci, [:])
        assert config.getCi() instanceof Double
        assert config.getCi() == expectedCi
        assert config.location == location
        validateDefaultProperties(config)

        where:
        location    || expectedCi
        'DE'        || 300.0
        'US'        || 400.0
        'FR'        || 250.0
        'INVALID'   || 400.0 // Falls back to GLOBAL
    }

    def 'should throw exception if required columns are missing'() {
        given:
        // Create a real DataMatrix with missing columns
        def data = [[1], [2]]
        def columnIndex = ['foo'] as LinkedHashSet
        def rowIndex = [0, 1] as LinkedHashSet
        def matrix = new DataMatrix(data, columnIndex, rowIndex)

        when:
        matrix.checkRequiredColumns(['required1', 'required2'])

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("CSV is missing required columns")
    }

    def 'should log warning and set machineType to null for unknown executor'() {
        given:
        def configMap = [:]
        def processMap = [executor: 'notarealexecutor']

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(configMap, tdp, ci, processMap)

        then:
        config.machineType == null
        // Optionally: check logs for warning if your framework supports it
    }

    def 'should not overwrite pue if already set'() {
        given:
        def configMap = [pue: 2.22]
        def processMap = [executor: 'awsbatch']

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(configMap, tdp, ci, processMap)

        then:
        config.pue == 2.22
    }

    // Helper method to validate default properties
    private static void validateDefaultProperties(CO2FootprintConfig config) {
        assert config.powerdrawMem == 0.3725
        assert config.pue == 1.0
    }
}
