package nextflow.co2footprint

import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Logging.LoggingAdapter

import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.CompileStatic

import groovy.util.logging.Slf4j

import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

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

    // Configuration
    CO2FootprintConfig config = null

    /**
     * Adapt the current logging to filter marked messages for uniqueness and change the coloring of the console output.
     */
    static void adaptLogging() {
        LoggingAdapter loggingAdapter = new LoggingAdapter()
        loggingAdapter.addUniqueMarkerFilter()
        loggingAdapter.changePatternConsoleAppender()
    }

    /**
     * Define a configuration for the nf-co2footprint plugin.
     *
     * @param configModifications Modifications that should be made to the config as a {@link Map}
     * @param session The current Nextflow session
     * @param tdpDataMatrix Matrix with CPU Thermal design power (TDP) information
     * @param ciDataMatrix Matrix with carbon intensity (CI) information
     * @return A configuration that can be used by the plugin
     */
    CO2FootprintConfig defineConfig(
            Map<String, Object> configModifications=[:],
            Session session=this.session,
            TDPDataMatrix tdpDataMatrix=TDPDataMatrix.tdpDataMatrix,
            CIDataMatrix ciDataMatrix=CIDataMatrix.ciDataMatrix
    ) {
        Map<String, Object> co2footprintConfig = (session?.config?.navigate('co2footprint') as Map ?: [:])
        if (configModifications) { co2footprintConfig += configModifications}
        return new CO2FootprintConfig(
                co2footprintConfig,
                tdpDataMatrix,
                ciDataMatrix,
                session?.config?.navigate('process') as Map
        )
    }

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

        log.info('🔕 Repeated task-specific messages (🔁) are only logged once in the console. Further occurrences are logged at DEBUG level, appearing only in the `.nextflow.log` file.')

        // Define components (config & computer)
        config = defineConfig()
        CO2FootprintCalculator co2FootprintCalculator = new CO2FootprintCalculator(TDPDataMatrix.tdpDataMatrix, config)

        // Define list of observers
        TraceObserver observer = new CO2FootprintObserver(config, co2FootprintCalculator)
        CO2FootprintPlugin co2FootprintPlugin = CO2FootprintPlugin.getPlugin()
        co2FootprintPlugin?.observer = observer
        final ArrayList<TraceObserver> result = [ observer ]
        return result
    }
}
