package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CO2Record {

    private Float co2e
    private String name
    // final? or something? to make sure for key value can be set only once?

    CO2Record(Float co2e, String name) {
        this.co2e = co2e
        this.name = name
    }

    // TODO implement accordingly to TraceRecord
    Float getCO2e() { co2e }
    String getName() { name }
}
