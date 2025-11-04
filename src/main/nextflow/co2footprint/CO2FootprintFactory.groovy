package nextflow.co2footprint

import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Logging.LoggingAdapter

import java.nio.file.Path
import java.nio.file.Paths

import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.CompileStatic

import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.LoggerContext

import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

import java.util.jar.Manifest


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
    // Nextflow Session
    private Session session = null

    // Plugin version
    String pluginVersion = null

    // Configuration
    CO2FootprintConfig config = null

    // Computer
    CO2FootprintComputer co2FootprintComputer = null

    //External Data integration of TDP (Thermal design power) and CI (Carbon intensity) values.
    final TDPDataMatrix tdpDataMatrix = readTdpDataMatrix()
    final CIDataMatrix ciDataMatrix = readCiDataMatrix()

    /**
     * Set the current plugin version from the local /META-INF/MANIFEST.MF
     *
     * @param manifest URL to the manifest
     * @param tryFallback Whether a fallback in the form of a search of all MANIFESTS from the class loader should be attempted
     */
    static protected String readPluginVersion(
            URL manifest=CO2FootprintFactory.class.getResource('/META-INF/MANIFEST.MF'),
            boolean tryFallback=true
    ) {
        log.trace("MANIFEST.MF path: ${manifest.toString()}")

        try {
            // Get version from manifest
            List<String> lines = manifest.readLines()
            String line = lines.find {it.startsWith('Plugin-Version: ') }
            return line.split(': ')[1]
        }
        catch (NullPointerException nullPointerException) {
            // Fallback to checking all classLoader Files
            if (tryFallback) {
                URL url = CO2FootprintFactory.class.protectionDomain.codeSource.location
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
     * Adapt the current logging to filter marked messages for uniqueness and change the coloring of the console output.
     */
    static void adaptLogging() {
        LoggingAdapter loggingAdapter = new LoggingAdapter()
        loggingAdapter.addUniqueMarkerFilter()
        loggingAdapter.changePatternConsoleAppender()
    }

    /**
     * External Data integration of TDP (Thermal design power) values.
     *
     * @return The TDP data as a matrix
     */
    static TDPDataMatrix readTdpDataMatrix() {
        return TDPDataMatrix.fromCsv(
                Paths.get(CO2FootprintFactory.class.getResource('/cpu_tdp_data/CPU_TDP.csv').toURI())
        )
    }

    /**
     * External Data integration of CI (Carbon intensity) values.
     *
     * @return The CI data as a matrix
     */
    static CIDataMatrix readCiDataMatrix() {
        return CIDataMatrix.fromCsv(
                Paths.get(CO2FootprintFactory.class.getResource('/ci_data/ci_yearly_2024_by_location.csv').toURI())
        )
    }

    CO2FootprintConfig defineConfig(
        Map<String, Object> configModifications=[:],
        Session session=this.session,
        TDPDataMatrix tdpDataMatrix=this.tdpDataMatrix,
        CIDataMatrix ciDataMatrix=this.ciDataMatrix
    ) {
        Map<String, Object> co2footprintConfig = (session.config.navigate('co2footprint') as Map ?: [:])
        if (configModifications) { co2footprintConfig += configModifications}
        return new CO2FootprintConfig(
                co2footprintConfig,
                tdpDataMatrix,
                ciDataMatrix,
                session.config.navigate('process') as Map
        )
    }

    CO2FootprintComputer defineComputer(
            CO2FootprintConfig config=this.config,
            TDPDataMatrix tdpDataMatrix=this.tdpDataMatrix
    ){
        return new CO2FootprintComputer(tdpDataMatrix, config)
    }

    /**
     * Define the process observer.
     *
     * @param config Configuration that is to be used
     * @param session Current session
     * @param pluginVersion Used plugin version
     * @param co2FootprintComputer Computer for CO2 calculations
     * @return An observer for the session with the applied settings from the config and computer
     */
    CO2FootprintObserver defineObserver(
            CO2FootprintConfig config=this.config,
            Session session=this.session,
            String pluginVersion=this.pluginVersion,
            CO2FootprintComputer co2FootprintComputer=this.co2FootprintComputer
    ){
        return new CO2FootprintObserver(session, pluginVersion, config, co2FootprintComputer)
    }

    /**
     * @return The plugin version
     */
    String getPluginVersion() { pluginVersion }
    
    /**
     * Creates and returns the CO2Footprint trace observer.
     * Loads configuration, sets up the observer, and injects all required data.
     *
     * @param session The Nextflow session
     * @return Collection of TraceObserver (with one CO2FootprintObserver)
     */
    @Override
    Collection<TraceObserver> create(Session session) {
        this.session = session
        adaptLogging()

        // Read the plugin version
        pluginVersion = readPluginVersion()
        log.info("nf-co2footprint plugin  ~  version ${this.pluginVersion}")
        log.info('🔕 Repeated task-specific messages (🔁) are only logged once in the console. Further occurrences are logged at DEBUG level, appearing only in the `.nextflow.log` file.')

        // Define components (config & computer)
        config = defineConfig()
        co2FootprintComputer = defineComputer()

        // Define list of observers
        final ArrayList<TraceObserver> result = [ defineObserver() ]
        return result
    }
}
