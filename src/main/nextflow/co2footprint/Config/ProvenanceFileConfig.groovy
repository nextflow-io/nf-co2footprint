package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

import java.nio.file.Path

@Description('The `co2footprint.provenance` scope allows you to configure the data/machine-actionable file of the `nf-co2footprint` plugin.')
class ProvenanceFileConfig extends BaseFileConfig implements ConfigScope {

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
    @Description('Whether only emission metrics should be reported in the provenance file.')
    final boolean emissionMetricsOnly

    ProvenanceFileConfig(Map provenanceFileConfig, String timestamp=null) {
        super('provenance', 'json', false)

        file = defineFile(provenanceFileConfig, timestamp)
        enabled = defineEnabled(provenanceFileConfig)
        overwrite = defineOverwrite(provenanceFileConfig)

        emissionMetricsOnly = provenanceFileConfig.containsKey('emissionMetricsOnly') ?
                CO2FootprintConfig.getCollect('emissionMetricsOnly', provenanceFileConfig, usedKeys) as boolean :
                true

        CO2FootprintConfig.checkKeyUsage(provenanceFileConfig, usedKeys)
    }
}
