package nextflow.co2footprint.Records

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class CiRecordCollectorTest extends Specification {
    @Shared
    CIDataMatrix ciDataMatrix = Mock(CIDataMatrix)

    @Shared
    TDPDataMatrix tdpDataMatrix = Mock(TDPDataMatrix)

    def 'Test addition of new time-CI pair' () {
        setup:
        CO2FootprintConfig config = new CO2FootprintConfig( [:], tdpDataMatrix, ciDataMatrix, [:] )
        CiRecordCollector timeCiRecordCollector = new CiRecordCollector(config)

        when:
        LocalDateTime now = LocalDateTime.now()
        timeCiRecordCollector.add(new CiRecord(10, null, null, null, now))
        Map timeCis = timeCiRecordCollector.getTimeCIs()

        then:
        timeCis == [(now): 10] as ConcurrentHashMap
    }

    def 'Test CI value computation' () {
        setup:
        CiRecordCollector timeCiRecordCollector = new CiRecordCollector(Mock(CO2FootprintConfig))
        TraceRecord traceRecord = Mock(TraceRecord)

        when:
        LocalDateTime time_10_00_00 =  LocalDateTime.of(2025, 8, 20, 10, 0, 0)
        LocalDateTime time_10_01_00 =  LocalDateTime.of(2025, 8, 20, 10, 1, 0)
        LocalDateTime time_10_02_00 =  LocalDateTime.of(2025, 8, 20, 10, 2, 0)

        timeCiRecordCollector.add(new CiRecord(10.0, null, null, null, time_10_00_00))
        timeCiRecordCollector.add(new CiRecord(30.0, null, null, null, time_10_01_00))

        traceRecord.get('start') >> time_10_00_00.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        traceRecord.get('complete') >> time_10_02_00.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        Double weightedCI = timeCiRecordCollector.getWeightedCI(traceRecord)

        then:
        weightedCI == 20.0
    }
}