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

    def 'test co2e calculation' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 14.61
        // CO2 converted to g
        round(co2Record.getCO2e()/1000) == 6.94
    }

    def 'test co2e calculation for specific cpu_model' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "AMD EPYC 7251"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 17.61
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 8.36
    }

    def 'test co2e calculation with non-default pue' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([pue: 1.4], tdpDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 20.45
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 9.71
    }

    def 'test co2e calculation with CI value retrieved for Germany' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([location: 'DE'], tdpDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        round(co2Record.getEnergyConsumption()/1000) == 14.61
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 4.95
    }

    def 'test co2e calculation for custom CI value' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1024**3 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([ci: 338.66], tdpDataMatrix, [:])
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 14.61
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 4.95
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
