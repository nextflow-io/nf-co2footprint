package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.plugin.extension.Function
import nextflow.plugin.extension.PluginExtensionPoint
import nextflow.trace.TraceRecord

import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class CO2FootprintExtension extends PluginExtensionPoint {
    CO2FootprintObserver observer

    @Override
    void init(Session session){
        CO2FootprintFactory factory = new CO2FootprintFactory()
        observer = factory.create(session)[0] as CO2FootprintObserver
    }

    @Function
    static TraceRecord parseTraceFile(Path tracePath) {
        TraceRecord traceRecord = new TraceRecord()
        return traceRecord.parseTraceFile(tracePath)
    }

    @Function
    CO2Record calculateCO2(
            Path tracePath, Boolean renderFiles=true,
            Double carbonIntensity=null, Map<LocalDateTime, Number> timeCIs=null
    ){
        TraceRecord traceRecord = parseTraceFile(tracePath)

        if (carbonIntensity) {
            observer.config.set('ci', carbonIntensity)
        }
        if (timeCIs) {
            observer.timeCiRecordCollector = new CiRecordCollector(observer.config, timeCIs as ConcurrentHashMap)
        }

        observer.traceFile.create()
        observer.startRecord(traceRecord)
        CO2Record co2Record = observer.aggregateRecords(traceRecord)

        if (renderFiles) {
            observer.renderFiles()
        }

        return co2Record
    }
}
