package nextflow.co2footprint.FileCreators

class TraceFileConfig extends BaseFileConfig {

    // Helper variables
    private final String name = "${super.name}_trace"

    TraceFileConfig(Map<String, Object> traceConfigMap) {
        super()
        setDefault('file', [outDirectory, name, suffix, ending], true)
        parameters.fill(traceConfigMap)
    }
}
