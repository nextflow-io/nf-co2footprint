package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.config.ConfigParser
import nextflow.config.ConfigParserFactory
import nextflow.script.ScriptFile
import nextflow.script.WorkflowMetadata

import java.nio.file.Path

/**
 * A class for the command line interface (CLI) of the plugin.
 */
@Slf4j
class CO2FootprintCLI {
    /**
     * Post run estimations of footprint from the CLI.
     *
     * @param parsedArgs Parsed arguments as a map, expected keys: `tracePath` or `provenancePath` and optionally `config`
     * @param configMap A Nextflow config in {@link Map} form, optional
     * @return Exit code of the CLI execution, 0 if successful
     */
    static int postRun(Map<String, Object> parsedArgs, Map configMap=[:]) {
        // Ensure duplicate warnings (e.g. unknown CPU model) are deduplicated,
        // regardless of whether this was reached via CO2FootprintPlugin.exec() or called directly.
        CO2FootprintFactory.adaptLogging()

        // Define trace path
        Path tracePath = parsedArgs.containsKey('tracePath') ? Path.of(parsedArgs.get('tracePath') as String) : null
        Path provenancePath = parsedArgs.containsKey('provenancePath') ? Path.of(parsedArgs.get('provenancePath') as String) : null
        assert tracePath || provenancePath, 'Please provide either a trace file path via the `tracePath` argument or a provenance file path via the `provenancePath` argument.'
        assert !(tracePath && provenancePath), 'Please provide only one of the following arguments: `tracePath` or `provenancePath`.'
        
        // Define infrastructure classes of plugin
        CO2FootprintConfig co2FootprintConfig = defineConfig(configMap)
        CO2FootprintObserver observer = defineObserver(co2FootprintConfig)

        // Create trace file
        observer.traceFile.create()

        // Define workflow metadata
        Session session = new Session()
        session.runName = 'CLI estimation'
        WorkflowMetadata metadata = new WorkflowMetadata(session, new ScriptFile( tracePath ? tracePath : provenancePath ))
        metadata.scriptName = 'nf-co2footprint CLI'
        metadata.projectName = 'nf-co2footprint CLI post-run'
        metadata.success = true
        metadata.commandLine = tracePath ?
                "nextflow plugin nf-co2footprint:postRun --tracePath ${tracePath.toString()}" :
                "nextflow plugin nf-co2footprint:postRun --provenancePath ${provenancePath.toString()}"
        if (parsedArgs.containsKey('delimiter')) {
            metadata.commandLine += " --delimiter ${parsedArgs.get('delimiter')}"
        }

        // Call on extension function to parse trace or provenance file and estimate footprint
        if (tracePath) {
            CO2FootprintExtension.tracePostRun(tracePath, observer, metadata, parsedArgs.get('delimiter', '\t') as String)
        }
        else if (provenancePath) {
            CO2FootprintExtension.provenancePostRun(provenancePath, observer, metadata)
        }

        // Render files
        observer.renderFiles(observer.workflowStats, metadata)

        return 0
    }

    /**
     * Define an observer based on a config file if provided, otherwise with default values.
     *
     * @param co2FootprintConfig CO2footprint plugin configuration
     * @return A CO2FootprintObserver instance with the given config or default values
     */
    static CO2FootprintObserver defineObserver(CO2FootprintConfig co2FootprintConfig) {
        // Define computer
        CO2FootprintCalculator computer = new CO2FootprintCalculator(TDPDataMatrix.tdpDataMatrix, co2FootprintConfig)

        // Return observer
        return new CO2FootprintObserver(co2FootprintConfig, computer)
    }

    /**
     * Define a CO2 footprint configuration if config file is provided.
     *
     * @param config Run config as a Map, optional
     * @param configPath Path to the config file, optional
     * @return A CO2FootprintObserver instance with the given config or default values
     */
    static CO2FootprintConfig defineConfig(Map config=null) {        
        // Extract whole config from given map or path
        ConfigObject configObject = null
        if (config) {
            configObject = config as ConfigObject
        }

        Map<String, Object> co2Config = configObject?.navigate('co2footprint') as Map ?: [:]
        Map<String, Object> processConfig = configObject?.navigate('process') as Map ?: [:]

        if (co2Config.containsKey('emApiKey') && !co2Config.get('emApiKey')) {
            log.warn(
                    'Empty value discovered for `emApiKey` in config.' +
                            'Keep in mind that secrets can not be accessed via `nextflow plugin ...`.' +
                            'Removing `emApiKey from config.'
            )
            co2Config.remove('emApiKey')
        }

        CO2FootprintConfig co2FootprintConfig = new CO2FootprintConfig(
                co2Config, TDPDataMatrix.tdpDataMatrix,
                CIDataMatrix.ciDataMatrix, processConfig
        )
        
        return co2FootprintConfig
    }
}
