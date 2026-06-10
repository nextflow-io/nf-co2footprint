package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

import java.nio.file.Path

@Description('The `co2footprint.trace` scope allows you to configure the trace file of the `nf-co2footprint` plugin.')
class TraceFileConfig extends BaseFileConfig implements ConfigScope {

    @ConfigOption
    @Description('Path to the file.')
    final Path file

    @ConfigOption
    @Description('Whether to enable the file creation.')
    final Boolean enabled

    @ConfigOption
    @Description('Whether to overwrite a file if it already exists.')
    final Boolean overwrite

    TraceFileConfig(Map traceFileConfig, String timestamp=null) {
        super('trace', 'txt')

        file = defineFile(traceFileConfig, timestamp)
        enabled = defineEnabled(traceFileConfig)
        overwrite = defineOverwrite(traceFileConfig)

        CO2FootprintConfig.checkKeyUsage(traceFileConfig, usedKeys)
    }
}
