package nextflow.co2footprint.FileCreators

/**
 * A class for summary file configurations
 */
class SummaryFileConfig extends BaseFileConfig {

    // Helper variables
    private final String name = "${super.name}_summary"

    SummaryFileConfig(Map<String, Object> summaryConfigMap=[:], String suffix=null) {
        super()
        suffix ?= this.suffix
        setDefault('file', [outDirectory, name, suffix, ending], true)
        parameters.fill(summaryConfigMap)
    }
}
