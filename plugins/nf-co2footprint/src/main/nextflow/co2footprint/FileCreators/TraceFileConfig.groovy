package nextflow.co2footprint.FileCreators

/**
 * A class for trace file configurations
 */
class TraceFileConfig extends BaseFileConfig {

    // Helper variables
    private final String name = "${super.name}_trace"

    TraceFileConfig(Map<String, Object> traceConfigMap=[:], String suffix=null) {
        super()
        suffix ?= this.suffix
        setDefault('file', [outDirectory, name, suffix, ending], true)
        parameters.fill(traceConfigMap)
    }
}
