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

    // Plugin version
    private String pluginVersion = null

    /**
     * Set the current version of the plugin into the respective variable
     */
    protected void setPluginVersion() {
        Enumeration<URL> resources = this.class.classLoader.getResources('META-INF/MANIFEST.MF')
        URL manifest = resources.find { it.toString().endsWith('/plugins/nf-co2footprint/build/resources/main/META-INF/MANIFEST.MF') } as URL
        List<String> lines = manifest.readLines()
        String line = lines.find {it.startsWith('Plugin-Version: ') }
        pluginVersion = line.split(': ')[1]
    }

    /**
     * @return The plugin version
     */
    String getPluginVersion() { pluginVersion }

    /**
     * External Data integration of TDP (Thermal design power) and CI (Carbon intensity) values
     */
    private TDPDataMatrix tdpDataMatrix
    private CIDataMatrix ciDataMatrix
    
    /**
     * Creates and returns the CO2Footprint trace observer.
     * Loads configuration, sets up the observer, and injects all required data.
     *
     * @param session The Nextflow session
     * @return Collection of TraceObserver (with one CO2FootprintObserver)
     */
    @Override
    Collection<TraceObserver> create(Session session) {
        // Read the plugin version
        setPluginVersion()
        log.info("nf-co2footprint plugin  ~  version ${this.pluginVersion}")

        // Read in matrices
        this.tdpDataMatrix = TDPDataMatrix.fromCsv(
                Paths.get(this.class.getResource('/cpu_tdp_data/CPU_TDP.csv').toURI())
        )
        this.ciDataMatrix = CIDataMatrix.fromCsv(
                Paths.get(this.class.getResource('/ci_data/ci_yearly_2024_by_location.csv').toURI())
        )

        // Define config
        CO2FootprintConfig config = new CO2FootprintConfig(
                session.config.navigate('co2footprint') as Map,
                this.tdpDataMatrix,
                this.ciDataMatrix,
                session.config.navigate('process') as Map
        )

        // Define list of observers
        final ArrayList<TraceObserver> result = [
            new CO2FootprintObserver(
                session,
                this.pluginVersion,
                config,
                new CO2FootprintComputer(this.tdpDataMatrix, config)
            )
        ]

        return result
    }


}
