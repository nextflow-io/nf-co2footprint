package nextflow.co2footprint

import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
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
 * Factory class for creating the CO2Footprint trace observer.
 * 
 * Loads external data for CPU TDP and carbon intensity, sets up deduplicated logging,
 * and creates the observer with all required configuration and data.
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
        final LoggerContext lc = LoggerFactory.getILoggerFactory() as LoggerContext   // Get Logging Context
        final TurboFilter dmf = new DeduplicateMarkerFilter([Markers.unique])         // Define DeduplicateMarkerFilter
        dmf.start()
        lc.addTurboFilter(dmf)                                                  // Add filter to context
    }

    protected void getPluginVersion() {
        final InputStreamReader reader = new InputStreamReader(this.class.getResourceAsStream('/META-INF/MANIFEST.MF'))
        String line
        while ( (line = reader.readLine()) && !version ) {
            def h = line.split(': ')
            if ( h[0] == 'Plugin-Version' ) this.version = h[1]
        }
        reader.close()
    }

    /**
     * External Data integration of TDP (Thermal design power) and CI (Carbon intensity) values
     */
    private final TDPDataMatrix tdpDataMatrix = TDPDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/cpu_tdp_data/CPU_TDP.csv').toURI())
    )

    private final CIDataMatrix ciDataMatrix = CIDataMatrix.fromCsv(
            Paths.get(this.class.getResource('/ci_data/ci_yearly_2024_by_location.csv').toURI())
    )
    
    /**
     * Creates and returns the CO2Footprint trace observer.
     * Loads configuration, sets up the observer, and injects all required data.
     *
     * @param session The Nextflow session
     * @return Collection of TraceObserver (with one CO2FootprintObserver)
     */
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

        final ArrayList<TraceObserver> result = new ArrayList(1)

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
