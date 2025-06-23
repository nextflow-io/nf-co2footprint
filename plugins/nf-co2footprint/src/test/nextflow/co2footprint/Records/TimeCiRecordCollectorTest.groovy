package nextflow.co2footprint.Records

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class TimeCiRecordCollectorTest  extends Specification {
    @Shared
    CIDataMatrix ciDataMatrix = Mock(CIDataMatrix)

    @Shared
    TDPDataMatrix tdpDataMatrix = Mock(TDPDataMatrix)

    def 'Test addition of new time-CI pair' () {
        setup:
        CO2FootprintConfig config = new CO2FootprintConfig( [:], tdpDataMatrix, ciDataMatrix, [:] )
        TimeCiRecordCollector timeCiRecordCollector = new TimeCiRecordCollector(config)

        when:
        LocalDateTime now = LocalDateTime.now()
        timeCiRecordCollector.add([time: now, ci: 10])
        Map timeCis = timeCiRecordCollector.getTimeCIs()

        then:
        timeCis == [(now): 10] as ConcurrentHashMap
    }

    def 'Test scheduling function' () {
        setup:
        CO2FootprintConfig config = new CO2FootprintConfig(
                [apiKey: 'mock_key', ci: { return [time: LocalDateTime.now(), ci: 10] }],
                tdpDataMatrix, ciDataMatrix, [:]
        )
        TimeCiRecordCollector timeCiRecordCollector = new TimeCiRecordCollector(config)

        when:
        timeCiRecordCollector.start(config, 0, 500) // execute every 0.5 second
        Thread.sleep(2000)                  // wait 2 seconds
        timeCiRecordCollector.stop()
        Thread.sleep(1000)                  // wait 2 seconds

        Map timeCis = timeCiRecordCollector.getTimeCIs()

        then:
        timeCis instanceof ConcurrentHashMap<LocalDateTime, Integer>
        timeCis.size() == 5

        // Check whether the timestamps are roughly in the right distance to another (+/- 50ms)
        LocalDateTime  previous = timeCis.keySet().min()
        timeCis.remove(previous)
        timeCis.keySet().sort().each { LocalDateTime current ->
            assert previous.until(current, ChronoUnit.MILLIS) > 450
            assert previous.until(current, ChronoUnit.MILLIS) < 550
            previous = current
        }
    }

    def 'Test CI value computation' () {
        setup:
        LocalDateTime now = LocalDateTime.now()
        Integer i = 0
        CO2FootprintConfig config = new CO2FootprintConfig(
                [apiKey: 'mock_key', ci: { i++; return [time: LocalDateTime.now(), ci: 10 - i] }],
                tdpDataMatrix, ciDataMatrix, [:]
        )
        TimeCiRecordCollector timeCiRecordCollector = new TimeCiRecordCollector(config)
        TraceRecord traceRecord = Mock(TraceRecord)

        when:
        timeCiRecordCollector.start(config, 0, 500) // execute every 0.5 second
        Thread.sleep(2000)                  // wait 2 seconds
        timeCiRecordCollector.stop()
        Thread.sleep(1000)                  // wait 2 seconds

        traceRecord.get('start') >> now
        traceRecord.get('complete') >> LocalDateTime.now()
        Double weightedCI = timeCiRecordCollector.getWeightedCI(traceRecord)

        then:
        // Because start and finish are not 100 % equally set apart from middle time point, the result will be shifted a bit around 7.0
        weightedCI > 6.0
        weightedCI < 8.0
    }
}