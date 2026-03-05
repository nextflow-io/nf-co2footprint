package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('co2footprint.dataFile')
@Description('The `co2footprint.dataFile` scope allows you to configure the data/machine-actionable file of the `nf-co2footprint` plugin.')
class DataFileConfig extends BaseFileConfig implements ConfigScope {

    @ConfigOption
    @Description('Whether only emission metrics should be reported in the data file.')
    final boolean emissionMetricsOnly

    DataFileConfig(Map dataFileConfig, String timestamp=null) {
        super(dataFileConfig, timestamp, 'data', 'yaml', false)

        emissionMetricsOnly = dataFileConfig.containsKey('emissionMetricsOnly') ?
                CO2FootprintConfig.getCollect('emissionMetricsOnly', dataFileConfig, usedKeys) as boolean :
                false

        CO2FootprintConfig.checkKeyUsage(dataFileConfig, usedKeys)
    }
}
