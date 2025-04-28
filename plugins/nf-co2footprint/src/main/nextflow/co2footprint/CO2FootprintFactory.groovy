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

import nextflow.co2footprint.utils.DeduplicateMarkerFilter
import nextflow.co2footprint.utils.Markers

import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.CompileStatic

import java.nio.file.Paths

import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.turbo.TurboFilter

/**
 * Implements the CO2Footprint observer factory
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
@Slf4j
@CompileStatic
@PackageScope(PackageScopeTarget.FIELDS)
class CO2FootprintFactory implements TraceObserverFactory {

    private String version

    /**
     * Logging:
     * Removes duplicates in some warnings, to avoid cluttering the output with repeated information.
     * Example: If the CPU model is not found it should only be warned once, that a fallback value is used.
     */
    static {
        LoggerContext lc = LoggerFactory.getILoggerFactory() as LoggerContext   // Get Logging Context
        TurboFilter dmf = new DeduplicateMarkerFilter([Markers.unique])         // Define DeduplicateMarkerFilter
        dmf.start()
        lc.addTurboFilter(dmf)                                                  // Add filter to context
    }

    protected void getPluginVersion() {
        def reader = new InputStreamReader(this.class.getResourceAsStream('/META-INF/MANIFEST.MF'))
        String line
        while ( (line = reader.readLine()) && !version ) {
            def h = line.split(": ")
            if ( h[0] == 'Plugin-Version' ) this.version = h[1]
        }
        reader.close()
    }

    /**
     * External Data integration of TDP (Thermal design power) and CI (Carbon intensity) values
     */
    private final TDPDataMatrix tdpDataMatrix = TDPDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/CPU_TDP.csv').toURI())
    )

    private final CIDataMatrix ciDataMatrix = CIDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/fallbackCIDataTable.csv').toURI())
    )
    

    @Override
    Collection<TraceObserver> create(Session session) {
        getPluginVersion()
        log.info("nf-co2footprint plugin  ~  version ${version}")

        CO2FootprintConfig config = new CO2FootprintConfig(
                session.config.navigate('co2footprint') as Map,
                this.tdpDataMatrix,
                this.ciDataMatrix,
                session.config.navigate('process') as Map
        )

        final result = new ArrayList(1)

        result.add(
                new CO2FootprintObserver(
                    session,
                    version,
                    config,
                    new CO2FootprintComputer(tdpDataMatrix, config)
            )
        )

        return result
    }

    

}
