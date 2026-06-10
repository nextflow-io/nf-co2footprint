package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

import java.nio.file.Path

@Description('The `co2footprint.summary` scope allows you to configure the summary file of the `nf-co2footprint` plugin.')
class SummaryFileConfig extends BaseFileConfig implements ConfigScope {

    @ConfigOption
    @Description('Path to the file.')
    final Path file

    @ConfigOption
    @Description('Whether to enable the file creation.')
    final Boolean enabled

    @ConfigOption
    @Description('Whether to overwrite a file if it already exists.')
    final Boolean overwrite

    SummaryFileConfig(Map summaryFileConfig, String timestamp=null) {
        super('summary', 'txt')

        file = defineFile(summaryFileConfig, timestamp)
        enabled = defineEnabled(summaryFileConfig)
        overwrite = defineOverwrite(summaryFileConfig)

        CO2FootprintConfig.checkKeyUsage(summaryFileConfig, usedKeys)
    }
}
