package nextflow.co2footprint

import nextflow.Session
import nextflow.executor.NopeExecutor
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CO2FootprintComputerTest extends Specification{

    private static BigDecimal round( double value ) {
        Math.round( value * 100 ) / 100
    }

    @Shared
    TDPDataMatrix tdpDataMatrix = TDPDataMatrix.loadCsv(
            Paths.get(this.class.getResource('/CPU_TDP.csv').toURI())
    )
    @Shared
    CIDataMatrix ciDataMatrix = null

    // ------ CO2 Calculation ------

    def "CO2e calculation for various configurations"() {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = cpuModel
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig(configMap, tdpDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        round(co2Record.getEnergyConsumption()/1000) == expectedEnergy
        round(co2Record.getCO2e()/1000) == expectedCO2

        where:
        cpuModel           | configMap                        || expectedEnergy | expectedCO2
        "Unknown model"    | [:]                              || 14.61         | 6.94
        "AMD EPYC 7251"    | [:]                              || 17.61         | 8.36
        "Unknown model"    | [pue: 1.4]                       || 20.45         | 9.71
        "Unknown model"    | [location: 'DE']                 || 14.61         | 4.95
        "Unknown model"    | [ci: 338.66]                     || 14.61         | 4.95
    }

    // ------ Equivalences Calculation ------

    def 'test co2e equivalences calculation' () {
        given:
        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix, [:])
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
}
