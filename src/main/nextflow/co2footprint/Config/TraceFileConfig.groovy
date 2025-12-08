package nextflow.co2footprint.Config

import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

@ScopeName('co2footprint.trace')
@Description('The `co2footprint.trace` scope allows you to configure the trace file of the `nf-co2footprint` plugin.')
class TraceFileConfig extends BaseFileConfig implements ConfigScope{
    TraceFileConfig(Map traceFileConfig, String timestamp=null) {
        super(traceFileConfig, timestamp, 'trace', 'txt')
    }
}
