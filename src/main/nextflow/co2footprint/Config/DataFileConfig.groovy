package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('co2footprint.dataFile')
@Description('The `co2footprint.dataFile` scope allows you to configure the data/machine-actionable file of the `nf-co2footprint` plugin.')
class DataFileConfig extends BaseFileConfig implements ConfigScope {
    DataFileConfig(Map dataFileConfig, String timestamp=null) {
        super(dataFileConfig, timestamp, 'data', 'yaml')

        CO2FootprintConfig.checkKeyUsage(dataFileConfig, usedKeys)
    }
}
