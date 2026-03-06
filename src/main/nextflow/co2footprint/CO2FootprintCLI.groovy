package nextflow.co2footprint

import groovy.util.logging.Slf4j
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Parsers.TraceFileParser
import nextflow.co2footprint.Records.CO2Record
import nextflow.config.ConfigParser
import nextflow.config.ConfigParserFactory
import nextflow.trace.TraceRecord

import java.nio.file.Path

/**
 * A class for the command line interface (CLI) of the plugin.
 */
@Slf4j
class CO2FootprintCLI {
    /**
     * Post run estimations of footprint from the CLI.
     *
     * @param parsedArgs Parsed arguments as a map
     * @return
     */
    static int postRun(Map<String, Object> parsedArgs) {
        // Define trace path
        assert parsedArgs.containsKey('tracePath') && (parsedArgs.get('tracePath') instanceof String)
        Path tracePath = Path.of(parsedArgs.get('tracePath') as String)

        // Define config
        Map<String, Object> co2Config = [:]
        Map<String, Object> processConfig = [:]
        if(parsedArgs.get('config') instanceof String) {
            Path configPath = Path.of(parsedArgs.get('config') as String)
            ConfigParser configParser = ConfigParserFactory.create()
            co2Config = configParser.parse(configPath).navigate('co2footprint') as Map?: [:]
            if (co2Config.containsKey('emApiKey') && !co2Config.get('emApiKey')) {
                log.warn(
                        'Empty value discovered for `emApiKey` in config.' +
                        'Keep in mind that secrets can not be accessed via `nextflow plugin ...`.' +
                        'Removing `emApiKey from config.'
                )
                co2Config.remove('emApiKey')
            }
            configParser.parse(configPath).navigate('process') as Map?: [:]
        }

        // Define separate observer
        CO2FootprintConfig config = new CO2FootprintConfig(
                co2Config, TDPDataMatrix.tdpDataMatrix,
                CIDataMatrix.ciDataMatrix, processConfig
        )
        CO2FootprintCalculator computer = new CO2FootprintCalculator(TDPDataMatrix.tdpDataMatrix, config)
        CO2FootprintObserver observer = new CO2FootprintObserver(config, computer)

        // Parse the trace file
        List<TraceRecord> traceRecords = TraceFileParser.parseExecutionTraceFile(tracePath)

        // Create trace file
        observer.traceFile.create()

        // Collect CO2Records from traces & optionally write the corresponding files
        List<CO2Record> co2Records = []
        traceRecords.each { TraceRecord traceRecord ->
            observer.recordStarted(traceRecord)
            co2Records.add(observer.aggregateRecords(traceRecord))
        }
        observer.renderFiles()

        return 0
    }
}
