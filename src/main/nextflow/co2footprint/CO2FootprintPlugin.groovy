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
import nextflow.co2footprint.Recorders.SessionTraceRecorder
import nextflow.co2footprint.Parsers.ArgsParser
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Parsers.TraceFileParser
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.config.ConfigParserFactory
import nextflow.config.ConfigParser
import nextflow.plugin.BasePlugin
import nextflow.trace.TraceRecord
import org.pf4j.PluginWrapper

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Implements the CO2Footprint plugins entry point
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
class CO2FootprintPlugin extends BasePlugin implements PluginAbstractExec {
    // Record stats about the session
    static final SessionTraceRecorder sessionTraceRecorder = new SessionTraceRecorder()

    // Plugin version
    static final String version = readPluginVersion()

    // Observer of Nextflow workflows/processes/tasks
    static CO2FootprintObserver observer

    CO2FootprintPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        log.info("nf-co2footprint plugin  ~  version ${version}")

        sessionTraceRecorder.start()
        super.start()
    }

    @Override
    void stop() {
        sessionTraceRecorder.stop()
        TraceRecord sessionRecord = sessionTraceRecorder.report()

        CO2Record sessionCO2Record = observer.createCO2Record(sessionRecord)
        observer.timeCiRecordCollector.stop()

        CO2RecordTree sessionStats = new CO2RecordTree(
                observer.session?.runName,
                [level: 'session'],
                sessionCO2Record,
                null,
                [observer.workflowStats]
        )

        // Render files
        observer.renderFiles(sessionStats)

        super.stop()
    }

    /**
     * Set the current plugin version from the local /META-INF/MANIFEST.MF
     *
     * @param manifest URL to the manifest
     * @param tryFallback Whether a fallback in the form of a search of all MANIFESTS from the class loader should be attempted
     */
    protected static String readPluginVersion(
            URL manifest=CO2FootprintPlugin.class.getResource('/META-INF/MANIFEST.MF'),
            boolean tryFallback=true
    ) {
        try {
            // Get version from manifest
            List<String> lines = manifest.readLines()
            String line = lines.find {String line -> line.startsWith('Plugin-Version: ') }
            return line.split(': ')[1]
        }
        catch (NullPointerException nullPointerException) {
            // Fallback to checking all classLoader Files
            if (tryFallback) {
                URL url = CO2FootprintPlugin.class.protectionDomain.codeSource.location
                url = Paths.get(url.path.replace('/classes/groovy/main', '/tmp/jar/MANIFEST.MF')).toUri().toURL()

                readPluginVersion(url, false)
            }
            else {
                log.error(nullPointerException.getMessage())
                throw nullPointerException
            }
        }
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
            CO2FootprintObserver observer = new CO2FootprintObserver(null, config, computer)

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
        else {
            System.err.println "Invalid command: ${cmd}"
            return 1
        }
    }
}
