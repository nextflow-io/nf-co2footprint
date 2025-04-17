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
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix)
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, ciDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 24.10
        // CO2 converted to g
        round(co2Record.getCO2e()/1000) == 11.45
    }

    def 'test co2e calculation for specific cpu_model' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "AMD EPYC 7251"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix)
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, ciDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 29.11
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 13.83
    }

    def 'test co2e calculation with non-default pue' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([pue: 1.4], tdpDataMatrix)
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, ciDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 20.2
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 9.59
    }

    def 'test co2e calculation with CI value retrieved for Germany' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([location: 'DE'], tdpDataMatrix)
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, ciDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 24.10
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 8.16
    }

    def 'test co2e calculation for custom CI value' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([ci: 338.66], tdpDataMatrix)
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, ciDataMatrix, config)
        CO2Record co2Record = co2FootprintComputer.computeTaskCO2footprint(new TaskId(0), traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(co2Record.getEnergyConsumption()/1000) == 24.10
        // CO2 in g
        round(co2Record.getCO2e()/1000) == 8.16
    }

    // ------ Equivalences Calculation ------

    def 'test co2e equivalences calculation' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        CO2FootprintConfig config = new CO2FootprintConfig([:], tdpDataMatrix)
        CO2FootprintComputer co2FootprintComputer = new CO2FootprintComputer(tdpDataMatrix, ciDataMatrix, config)
        CO2EquivalencesRecord co2EquivalencesRecord = co2FootprintComputer.computeCO2footprintEquivalences(
                10**8 + 500000.0
        )

        expect:
        co2EquivalencesRecord.getCarKilometers().round(7) == 574.2857143 as Double
        co2EquivalencesRecord.getTreeMonths().round(7) == 109.5965104 as Double
        co2EquivalencesRecord.getPlanePercent().round(7) == 201.000000 as Double
        co2EquivalencesRecord.getPlaneFlightsReadable() == '2.0'
    }
}
