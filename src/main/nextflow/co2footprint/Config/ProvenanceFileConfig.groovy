package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('co2footprint.provenance')
@Description('The `co2footprint.provenance` scope allows you to configure the data/machine-actionable file of the `nf-co2footprint` plugin.')
class ProvenanceFileConfig extends BaseFileConfig implements ConfigScope {

    @ConfigOption
    @Description('Whether only emission metrics should be reported in the data file.')
    final boolean emissionMetricsOnly

    ProvenanceFileConfig(Map provenanceFileConfig, String timestamp=null) {
        super(provenanceFileConfig, timestamp, 'data', 'yaml', false)

        emissionMetricsOnly = provenanceFileConfig.containsKey('emissionMetricsOnly') ?
                CO2FootprintConfig.getCollect('emissionMetricsOnly', provenanceFileConfig, usedKeys) as boolean :
                true

        CO2FootprintConfig.checkKeyUsage(provenanceFileConfig, usedKeys)
    }
}
