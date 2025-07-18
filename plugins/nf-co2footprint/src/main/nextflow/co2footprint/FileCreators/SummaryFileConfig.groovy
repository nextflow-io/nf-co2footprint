package nextflow.co2footprint.FileCreators

class SummaryFileConfig extends BaseFileConfig {

    // Helper variables
    private final String name = "${super.name}_summary"

    SummaryFileConfig(Map<String, Object> summaryConfigMap) {
        super()
        setDefault('file', [outDirectory, name, suffix, ending], true)
        parameters.fill(summaryConfigMap)
    }
}
