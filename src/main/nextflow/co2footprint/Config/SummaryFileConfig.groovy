package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('co2footprint.summary')
@Description('The `co2footprint.summary` scope allows you to configure the summary file of the `nf-co2footprint` plugin.')
class SummaryFileConfig extends BaseFileConfig implements ConfigScope{
    SummaryFileConfig() { super() }
    SummaryFileConfig(Map summaryFileConfig, String timestamp=null) {
        super(summaryFileConfig, timestamp, 'summary', 'txt')

        CO2FootprintConfig.checkKeyUsage(summaryFileConfig, usedKeys)
    }
}
