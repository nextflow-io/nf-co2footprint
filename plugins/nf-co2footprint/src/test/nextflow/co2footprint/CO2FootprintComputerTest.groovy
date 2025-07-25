package nextflow.co2footprint

import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2Record
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Paths

import groovy.util.logging.Slf4j
import nextflow.co2footprint.utils.HelperFunctions   

@Slf4j
class CO2FootprintComputerTest extends Specification{

    private static BigDecimal round( double value ) {
        Math.round( value * 100 ) / 100
    }

    @Shared
    TDPDataMatrix tdpDataMatrix = TDPDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/cpu_tdp_data/CPU_TDP.csv').toURI())
    )
    @Shared
    CIDataMatrix ciDataMatrix = CIDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/ci_data/ci_yearly_2024_by_location.csv').toURI())
    )

    // ------ CO2 Calculation ------

    def "CO2e calculation for various configurations"() {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = cpuModel
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig(configMap, tdpDataMatrix, ciDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        round(co2Record.getEnergyConsumption()/1000) == expectedEnergy
        round(co2Record.getCO2e()/1000) == expectedCO2

        where:
        cpuModel           | configMap                        || expectedEnergy | expectedCO2
        "Unknown model"    | [:]                              || 13.61          | 6.53
        "AMD EPYC 7251"    | [:]                              || 17.61          | 8.45
        "Unknown model"    | [pue: 1.4]                       || 19.05          | 9.14
        "Unknown model"    | [location: 'DE']                 || 13.61          | 4.54
        "Unknown model"    | [ci: 338.66]                     || 13.61          | 4.61
    }

    // ------ Equivalences Calculation ------

    def 'test co2e equivalences calculation' () {
        given:
        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix, ciDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2EquivalencesRecord co2EquivalencesRecord = co2FootprintComputer.computeCO2footprintEquivalences(co2e)

        expect:
        co2EquivalencesRecord.getCarKilometers().round(4) ==  carKm
        co2EquivalencesRecord.getTreeMonths().round(4) ==  treeMonths
        co2EquivalencesRecord.getPlanePercent().round(7) ==  planePercent
        co2EquivalencesRecord.getPlaneFlightsReadable() == planeFlights

        where:
        co2e                || carKm        || treeMonths       || planePercent || planeFlights
        10**8 + 500000.0    || 574.2857d    ||  109.5965d       || 201.000000d  || '2.0'
        11587.399           || 6.62e-02d    ||  1.26e-02        || 2.317480E-2d || '0.0'
    }

    // ------ Test Missing/Null Value Handling ------
    def "memory assignment logic covers all cases"() {
        given:
        // Mock the static HelperFunctions.getAvailableSystemMemory method for this test case
        HelperFunctions.metaClass.static.getAvailableSystemMemory = { TaskId taskID ->
            if (throwError) throw new IllegalStateException("No memory info")
            else return availableMemory
        }

        // Prepare a TraceRecord with test parameters for each case
        def traceRecord = new TraceRecord()
        traceRecord.realtime = 3600000L
        traceRecord.cpus = 1
        traceRecord.cpu_model = "TestCPU"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = memory
        traceRecord.peak_rss = peak_rss

        // Create config and the CO2FootprintComputer under test
        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix, ciDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)

        when:
        // Try to compute the CO2 footprint, catching any exceptions
        def result = null
        def caught = null
        try {
            result = co2FootprintComputer.computeTaskCO2footprint(new TaskId(1), traceRecord)
        } catch (Exception e) {
            caught = e
        }

        then:
        // If we expect an exception, assert it was thrown
        if (expectException) {
            assert caught instanceof IllegalStateException
        } else {
            // Otherwise, check that the computed memory matches the expected value (in GB)
            assert result.memory == expectedMemory
        }

        cleanup:
        // Remove the metaClass override to avoid side effects on other tests
        GroovySystem.metaClassRegistry.removeMetaClass(HelperFunctions)

        where:
        memory             | peak_rss           | availableMemory     | throwError | expectException | expectedMemory
        8L*1024**3         | 4L*1024**3         | 64L*1024**3         | false      | false           | 8L              // requested used
        null               | 4L*1024**3         | 64L*1024**3         | false      | false           | 64L             // available used (requested null)
        4L*1024**3         | 8L*1024**3         | 64L*1024**3         | false      | false           | 64L             // available used (required > requested)
        null               | null               | 32L*1024**3         | false      | false           | 32L             // available used (both null)
        4L*1024**3         | null               | 64L*1024**3         | false      | false           | 4L              // requested used (required null)
        null               | 4L*1024**3         | null                | true       | true            | null            // error thrown (available memory error)
    }
}
