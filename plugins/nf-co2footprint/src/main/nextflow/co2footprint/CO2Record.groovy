package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CO2Record {

    private Double energy
    private Double co2e
    private String name
    // final? or something? to make sure for key value can be set only once?

    CO2Record(Double energy, Double co2e, String name) {
        this.energy = energy
        this.co2e = co2e
        this.name = name
    }

    // TODO implement accordingly to TraceRecord
    Double getEnergyConsumption() { energy }
    Double getCO2e() { co2e }
    String getName() { name }
}
