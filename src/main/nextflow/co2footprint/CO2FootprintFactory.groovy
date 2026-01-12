package nextflow.co2footprint

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Logging.LoggingAdapter
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

import java.nio.file.Paths

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

    // Plugin version
    private String pluginVersion = null

    /**
     * Set the current plugin version from the local /META-INF/MANIFEST.MF
     *
     * @param manifest URL to the manifest
     * @param tryFallback Whether a fallback in the form of a search of all MANIFESTS from the class loader should be attempted
     */
    protected void setPluginVersion(
            URL manifest=this.class.getResource('/META-INF/MANIFEST.MF'),
            boolean tryFallback=true
    ) {
        log.trace("MANIFEST.MF path: ${manifest.toString()}")

        try {
            // Get version from manifest
            List<String> lines = manifest.readLines()
            String line = lines.find {it.startsWith('Plugin-Version: ') }
            pluginVersion = line.split(': ')[1]
        }
        catch (NullPointerException nullPointerException) {
            // Fallback to checking all classLoader Files
            if (tryFallback) {
                URL url = this.class.protectionDomain.codeSource.location
                url = Paths.get(url.path.replace('/classes/groovy/main', '/tmp/jar/MANIFEST.MF')).toUri().toURL()

                setPluginVersion(url, false)
            }
            else {
                log.error(nullPointerException.getMessage())
                throw nullPointerException
            }
        }
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
        // Logging
        LoggingAdapter loggingAdapter = new LoggingAdapter()
        loggingAdapter.addUniqueMarkerFilter()
        loggingAdapter.changePatternConsoleAppender()

        // Read the plugin version
        setPluginVersion()
        log.info("nf-co2footprint plugin  ~  version ${this.pluginVersion}")
        log.info('🔕 Repeated task-specific messages (🔁) are only logged once in the console. Further occurrences are logged at DEBUG level, appearing only in the `.nextflow.log` file.')

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
