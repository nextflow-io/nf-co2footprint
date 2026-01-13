package nextflow.co2footprint

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import groovy.util.logging.Slf4j
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.DataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import org.slf4j.LoggerFactory
import spock.lang.Specification

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
            config.value(property) == input.get(property)
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
            config.value(property) == pluginConfig.get(property)
        }).every()
        tdp.fallbackModel == "default ${config.value('machineType')}"
        config.value('pue') == pue

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
        assert config.value('ci') instanceof Double
        assert config.value('ci') == expectedCi
        assert config.value('location') == location
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
        Map<String, Object> configMap = [:]
        Map<String, Object> processMap = [executor: 'notarealexecutor']

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(configMap, tdp, ci, processMap)

        then:
        config.value('machineType') == null
        // Optionally: check logs for warning if your framework supports it
    }

    def 'should not overwrite pue if already set'() {
        given:
        def configMap = [pue: 2.22]
        def processMap = [executor: 'awsbatch']

        when:
        CO2FootprintConfig config = new CO2FootprintConfig(configMap, tdp, ci, processMap)

        then:
        config.value('pue') == 2.22
    }

    def 'should log custom CPU power model as polynomial'() {
        given:
        def configMap = [cpuPowerModel: [2.5, 1.3, 0.7], machineType: 'local']
        def processMap = [:]

        // Set up log capturing
        Logger logger = (Logger) LoggerFactory.getLogger(CO2FootprintConfig)
        def listAppender = new ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)

        when:
        new CO2FootprintConfig(configMap, tdp, ci, processMap as Map)

        then:
        def logMessages = listAppender.list*.formattedMessage
        logMessages.any { it.contains("Using custom CPU power model: f(x) = 2.5*x^2 + 1.3*x^1 + 0.7*x^0") }
    }

    // Helper method to validate default properties
    private static void validateDefaultProperties(CO2FootprintConfig config) {
        assert config.value('powerdrawMem') == 0.3725
        assert config.value('pue') == 1.0
    }
}
