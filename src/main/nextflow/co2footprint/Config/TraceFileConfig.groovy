package nextflow.co2footprint.Config

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.config.spec.ConfigScope
import nextflow.script.dsl.Description

@Description('The `co2footprint.trace` scope allows you to configure the trace file of the `nf-co2footprint` plugin.')
class TraceFileConfig extends BaseFileConfig implements ConfigScope{
    TraceFileConfig(Map traceFileConfig, String timestamp=null) {
        super(traceFileConfig, timestamp, 'trace', 'txt')

        CO2FootprintConfig.checkKeyUsage(traceFileConfig, usedKeys)
    }
}
