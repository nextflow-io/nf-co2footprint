package nextflow.co2footprint.FileCreators

import groovy.util.logging.Slf4j
import nextflow.co2footprint.Config.BaseConfig
import nextflow.trace.TraceHelper

import java.nio.file.Path

@Slf4j
class BaseFileConfig extends BaseConfig {
    // Helper variables
    protected final String outDirectory = 'pipeline_info'
    protected final String name = 'co2footprint'
    protected final String suffix = "_${TraceHelper.launchTimestampFmt()}"
    protected final String ending = '.txt'

    Closure<String> makeFile= { String outDirectory, name, suffix, ending ->
        Path.of(outDirectory, "${name}${suffix}${ending}").toString()
    }

    void initializeParameters() {
        addParameter(
                ['enabled', true],
                [returnType: Boolean, description: 'Whether to create the file']
        )
        addParameter(
                ['file', makeFile],
                [returnType: String, allowedTypes: [String, Closure<String>, GString, Path], description: 'Path to file']
        )
    }

    Boolean getEnabled() { get('enabled') }
    String getFile() { get('file') }
    Path getPath() { Path.of(get('file') as String) }

    BaseFileConfig() {
        super()
        initializeParameters()
        setDefaults([outDirectory, name, suffix, ending], false)
    }
}
