package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

import java.nio.file.Path

@Description('The `co2footprint.report` scope allows you to configure the report file of the `nf-co2footprint` plugin.')
class ReportFileConfig extends BaseFileConfig implements ConfigScope {

    @ConfigOption
    @Description('Path to the file.')
    final Path file

    @ConfigOption
    @Description('Whether to enable the file creation.')
    final Boolean enabled

    @ConfigOption
    @Description('Whether to overwrite a file if it already exists.')
    final Boolean overwrite

    @ConfigOption
    @Description('The number of maximum tasks that is displayed in the report.')
    final Integer maxTasks

    ReportFileConfig(Map reportFileConfig, String timestamp=null) {
        super('report', 'html')

        file = defineFile(reportFileConfig, timestamp)
        enabled = defineEnabled(reportFileConfig)
        overwrite = defineOverwrite(reportFileConfig)

        maxTasks = reportFileConfig.containsKey('maxTasks') ?
                CO2FootprintConfig.getCollect('maxTasks', reportFileConfig, usedKeys) as Integer :
                10_000

        CO2FootprintConfig.checkKeyUsage(reportFileConfig, usedKeys)
    }
}
