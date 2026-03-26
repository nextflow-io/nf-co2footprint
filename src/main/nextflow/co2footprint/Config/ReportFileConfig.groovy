package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

@Description('The `co2footprint.report` scope allows you to configure the report file of the `nf-co2footprint` plugin.')
class ReportFileConfig extends BaseFileConfig implements ConfigScope{

    @ConfigOption
    @Description('The number of maximum tasks that is displayed in the report.')
    final Integer maxTasks

    ReportFileConfig(Map reportFileConfig, String timestamp=null) {
        super(reportFileConfig, timestamp, 'report', 'html')
        maxTasks = reportFileConfig.containsKey('maxTasks') ?
                CO2FootprintConfig.getCollect('maxTasks', reportFileConfig, usedKeys) as Integer :
                10_000

        CO2FootprintConfig.checkKeyUsage(reportFileConfig, usedKeys)
    }
}
