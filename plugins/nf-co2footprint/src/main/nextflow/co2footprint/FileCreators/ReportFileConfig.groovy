package nextflow.co2footprint.FileCreators

/**
 * A class for report file configurations
 */
class ReportFileConfig extends BaseFileConfig {

    // Helper variables
    private final String name = "${super.name}_report"
    private final String ending = '.html'

    @Override
    void initializeParameters() {
        super.initializeParameters()
        addParameter(
                ['maxTasks', 10_000],
                [returnType: Integer, description: 'Number of maximum tasks that are reported']
        )
    }

    ReportFileConfig(Map<String, Object> reportConfigMap=[:], String suffix=null) {
        super()
        suffix ?= this.suffix
        setDefault('file', [outDirectory, name, suffix, ending], true)
        parameters.fill(reportConfigMap)
    }

    Integer getMaxTasks() { get('maxTasks') as Integer }
}
