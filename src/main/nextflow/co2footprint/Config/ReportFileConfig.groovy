package nextflow.co2footprint.Config

import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('co2footprint.report')
@Description('The `co2footprint.report` scope allows you to configure the report file of the `nf-co2footprint` plugin.')
class ReportFileConfig extends BaseFileConfig implements ConfigScope{
    ReportFileConfig(Map reportFileConfig, String timestamp=null) {
        super(reportFileConfig, timestamp, 'report', 'html')
    }

}
