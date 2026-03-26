package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

@Description('The `co2footprint.summary` scope allows you to configure the summary file of the `nf-co2footprint` plugin.')
class SummaryFileConfig extends BaseFileConfig implements ConfigScope{
    SummaryFileConfig(Map summaryFileConfig, String timestamp=null) {
        super(summaryFileConfig, timestamp, 'summary', 'txt')

        CO2FootprintConfig.checkKeyUsage(summaryFileConfig, usedKeys)
    }
}
