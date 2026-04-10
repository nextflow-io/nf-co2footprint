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

import nextflow.co2footprint.Recorders.SessionTraceRecorder
import nextflow.co2footprint.Parsers.ArgsParser
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.plugin.BasePlugin
import nextflow.plugin.Plugins
import nextflow.trace.TraceRecord
import org.pf4j.PluginWrapper

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
    final SessionTraceRecorder sessionTraceRecorder = new SessionTraceRecorder()

    // Plugin version
    static final String version = readPluginVersion()

    // Observer of Nextflow workflows/processes/tasks
    CO2FootprintObserver observer

    CO2FootprintPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    /**
     * Start the plugin. Earliest point of interaction with the core code.
     */
    @Override
    void start() {
        log.info("nf-co2footprint plugin  ~  version ${version}")

        sessionTraceRecorder.start()
        super.start()
    }

    /**
     * Stop the plugin. Latest point of interaction with the core code.
     */
    @Override
    void stop() {
        sessionTraceRecorder.stop()
        TraceRecord sessionRecord = sessionTraceRecorder.report()

        if (observer != null) {
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
        }

        super.stop()
    }

    static CO2FootprintPlugin getPlugin() {
        if(Plugins.manager) {
            PluginWrapper pluginWrapper = Plugins.manager.getPlugin('nf-co2footprint')
            return pluginWrapper.plugin as CO2FootprintPlugin
        }
        return  null
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
        return switch (cmd) {
            case 'postRun' -> CO2FootprintCLI.postRun(parsedArgs)
            default -> {
                System.err.println("Invalid command: ${cmd}")
                1
            }
        }
    }
}
