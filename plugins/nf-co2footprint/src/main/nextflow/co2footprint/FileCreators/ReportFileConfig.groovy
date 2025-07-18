package nextflow.co2footprint.FileCreators

class ReportFileConfig extends BaseFileConfig {

    // Helper variables
    private final String name = "${super.name}_report"
    private final String ending = '.html'

    ReportFileConfig(Map<String, Object> reportConfigMap) {
        super()
        setDefault('file', [outDirectory, name, suffix, ending], true)
        parameters.fill(reportConfigMap)
    }
}
