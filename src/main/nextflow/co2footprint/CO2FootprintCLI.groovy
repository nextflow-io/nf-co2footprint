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
     * @return Exit code of the CLI execution, 0 if successful
     */
    static int postRun(Map<String, Object> parsedArgs) {
        // Define trace path
        Path tracePath = parsedArgs.containsKey('tracePath') ? Path.of(parsedArgs.get('tracePath') as String) : null
        Path provenancePath = parsedArgs.containsKey('provenancePath') ? Path.of(parsedArgs.get('provenancePath') as String) : null
        assert tracePath || provenancePath, 'Please provide either a trace file path via the `tracePath` argument or a provenance file path via the `provenancePath` argument.'
        assert !(tracePath && provenancePath), 'Please provide only one of the following arguments: `tracePath` or `provenancePath`.'

        CO2FootprintObserver observer = defineObserver(
                parsedArgs.containsKey('config') ?
                Path.of(parsedArgs.get('config') as String) :
                null
        )

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
        if (parsedArgs.containsKey('config')) {
            metadata.commandLine += " --config ${parsedArgs.get('config')}"
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
     * @param configPath Path to the config file, optional
     * @return A CO2FootprintObserver instance with the given config or default values
     */
    static CO2FootprintObserver defineObserver(Path configPath=null) {
        // Define config
        Map<String, Object> co2Config = [:]
        Map<String, Object> processConfig = [:]

        if (configPath) {
            ConfigParser configParser = ConfigParserFactory.create()
            co2Config = configParser.parse(configPath).navigate('co2footprint') as Map ?: [:]
            if (co2Config.containsKey('emApiKey') && !co2Config.get('emApiKey')) {
                log.warn(
                        'Empty value discovered for `emApiKey` in config.' +
                        'Keep in mind that secrets can not be accessed via `nextflow plugin ...`.' +
                        'Removing `emApiKey from config.'
                )
                co2Config.remove('emApiKey')
            }
            configParser.parse(configPath).navigate('process') as Map ?: [:]
        }

        CO2FootprintConfig config = new CO2FootprintConfig(
                co2Config, TDPDataMatrix.tdpDataMatrix,
                CIDataMatrix.ciDataMatrix, processConfig
        )

        // Define computer
        CO2FootprintCalculator computer = new CO2FootprintCalculator(TDPDataMatrix.tdpDataMatrix, config)

        // Return observer
        return new CO2FootprintObserver(config, computer)
    }
}
