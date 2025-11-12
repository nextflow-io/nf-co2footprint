/*
 * Copyright 2021, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.cli.PluginAbstractExec
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Parsers.ArgsParser
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.Parsers.TraceFileParser
import nextflow.config.ConfigParserFactory
import nextflow.config.ConfigParser
import nextflow.plugin.BasePlugin
import nextflow.trace.TraceRecord
import org.pf4j.PluginWrapper

import java.nio.file.Path

/**
 * Implements the CO2Footprint plugins entry point
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
class CO2FootprintPlugin extends BasePlugin implements PluginAbstractExec {

    CO2FootprintPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    /**
     * Returns the commands which can be used from within the plugin.
     *
     * @return A list of Strings with command that are implemented in exec()
     */
    @Override
    List<String> getCommands() {
        return ['postRun']
    }

    /**
     * Function that is executed when `nextflow plugin nf-co2footprint:<CMD> --arg arg1 ...` is called.
     *
     * @param cmd Given command as a String
     * @param args Ordered list of String arguments
     * @return Exit code (0 = OK)
     */
    @Override
    int exec(String cmd, List<String> args) {
        // Change logging so duplicates are omitted
        CO2FootprintFactory.adaptLogging()

        Map<String, Object> parsedArgs = ArgsParser.parse(args)
        if( cmd == 'postRun' ) {
            // Define trace path
            assert parsedArgs.containsKey('tracePath') && parsedArgs.get('tracePath') instanceof String
            Path tracePath = Path.of(parsedArgs.get('tracePath') as String)

            // Define config
            assert parsedArgs.get('config') instanceof String
            Path configPath = Path.of(parsedArgs.get('config') as String)
            ConfigParser configParser = ConfigParserFactory.create()
            Map<String, Object> co2Config = configParser.parse(configPath).navigate('co2footprint') as Map?: [:]
            if (co2Config.containsKey('emApiKey') && !co2Config.get('emApiKey')) {
                log.warn(
                    'Empty value discovered for `emApiKey` in config.' +
                    'Keep in mind that secrets can not be accessed via `nextflow plugin ...`.' +
                    'Removing `emApiKey from config.'
                )
                co2Config.remove('emApiKey')
            }
            Map<String, Object> processConfig = configParser.parse(configPath).navigate('process') as Map?: [:]

            // Define separate observer
            TDPDataMatrix tdpDataMatrix = CO2FootprintFactory.readTdpDataMatrix()
            CIDataMatrix ciDataMatrix = CO2FootprintFactory.readCiDataMatrix()
            CO2FootprintConfig config = new CO2FootprintConfig(co2Config, tdpDataMatrix, ciDataMatrix, processConfig)
            CO2FootprintComputer computer = new CO2FootprintComputer(tdpDataMatrix, config)
            CO2FootprintObserver observer = new CO2FootprintObserver(null, 'unknown', config, computer)

            // Parse the trace file
            List<TraceRecord> traceRecords = TraceFileParser.parseExecutionTraceFile(tracePath)

            // Prepare aggregator
            observer.aggregator = new CO2RecordAggregator()

            // Create trace file
            observer.traceFile.create()

            // Collect CO2Records from traces & optionally write the corresponding files
            List<CO2Record> co2Records = []
            traceRecords.each { TraceRecord traceRecord ->
                observer.startRecord(traceRecord)
                co2Records.add(observer.aggregateRecords(traceRecord))
            }
            observer.renderFiles()

            return 0
        }
        else {
            System.err.println "Invalid command: ${cmd}"
            return 1
        }
    }
}
