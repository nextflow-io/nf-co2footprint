package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CO2Record {

    public Float co2e
    // final? or something? to make sure for key value can be set only once?

    CO2Record(Float co2e) {
        this.co2e = co2e
    }

    // TODO implement accordingly to TraceRecord
}
