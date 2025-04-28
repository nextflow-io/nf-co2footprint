package nextflow.co2footprint

import nextflow.co2footprint.utils.DataMatrix
import nextflow.co2footprint.utils.HelperFunctions
import java.nio.file.Path
import groovy.util.logging.Slf4j
import nextflow.co2footprint.utils.Markers
import groovy.json.JsonSlurper
import org.slf4j.MarkerFactory

/**
 * Structure for the carbon intensity (CI) values.
 *
 * This class extends the DataMatrix class to manage carbon intensity data.
 * It provides additional properties and methods specific to CI data.
 *
 * @author: Nadja Volkmann <nadja.volkmann@uni-tuebingen.de>
 */
@Slf4j
class CIDataMatrix extends DataMatrix {

    private final String ciColumn = 'Carbon intensity gCO₂eq/kWh (Life cycle)'

    /**
     * Constructor to initialize CIDataMatrix with data, columnIndex, and rowIndex.
     * Calls the parent constructor.
     */
    CIDataMatrix(
            List<List> data = [],
            LinkedHashSet<Object> columnIndex = [],
            LinkedHashSet<Object> rowIndex = []
    ) {
        super(data, columnIndex, rowIndex)
    }


    /**
     * Create a CIDataMatrix from a CSV file.
     *
     * @param path Path to the CSV file
     * @param separator Separator used in the CSV file (default is ',')
     * @param columnIndexPos Position of the column index (default is 0)
     * @param rowIndexPos Position of the row index (default is null)
     * @param rowIndexColumn Name of the column used for the row index (default is 'Zone id')
     * @return A CIDataMatrix object
     */
    static CIDataMatrix fromCsv(
            Path path, String separator = ',', Integer columnIndexPos = 0, Integer rowIndexPos = null,
            Object rowIndexColumn = 'Zone id'
    ) {
        DataMatrix dm = DataMatrix.fromCsv(path, separator, columnIndexPos, rowIndexPos, rowIndexColumn)
        CIDataMatrix ciMatrix = new CIDataMatrix(
                dm.getData(), dm.getOrderedColumnKeys(), dm.getOrderedRowKeys()
        )
        return ciMatrix
    }


    
    /**
     * Retrieves the carbon intensity value for a given zone from the matrix.
     *
     * @param targetZone The zone for which to retrieve the carbon intensity.
     * @return The carbon intensity value as a Double, or null if not found.
     */
    protected Double findCiInMatrix(String targetZone) {
        def ci
        try {
            ci = this.get(targetZone, this.ciColumn)
        } catch (IllegalArgumentException e) {
            log.warn("Could not find carbon intensity for zone '${targetZone}': ${e.message}")
            return null
        }
        return ci.toDouble()
    }
}

/**
 * Class to compute carbon intensity (CI) values.
 *
 * This class uses CIDataMatrix to manage and retrieve carbon intensity data.
 *
 * @author: Nadja Volkmann <nadja.volkmann@uni-tuebingen.de>
 */
@Slf4j
class CIValueComputer {
    String apiKey
    String location
    CIDataMatrix ciData

    CIValueComputer(String apiKey, String location, CIDataMatrix ciData) {
        this.apiKey = apiKey
        this.location = location
        this.ciData = ciData
    }

    /**
     * Retrieves the real-time carbon intensity from the electricityMaps API.
     *
     * @param processName The name of the process (optional).
     * @return The real-time carbon intensity value as a Double, or null if not found.
     */
    protected Double getRealtimeCI(String processName = null) {

        def url = new URL("https://api.electricitymap.org/v3/carbon-intensity/latest?zone=${this.location}")

        def connection = url.openConnection()
        connection.setRequestProperty("auth-token", this.apiKey)
        def json = null
        Double realTimeCI = null
        String updatedAt = null
        def marker = processName ? MarkerFactory.getMarker(processName): null

    
        if (connection.responseCode == 200) {
            json = new JsonSlurper().parse(connection.inputStream)
            realTimeCI = json['carbonIntensity'] as Double
            updatedAt = HelperFunctions.transformTimestamp(json['updatedAt'] as String)
        } else {
            def errorResponse = connection.errorStream.text
            def errorMessage = new JsonSlurper().parseText(errorResponse).message

            if (marker != null) {
                log.warn(marker, "Error: ${connection.responseCode} - ${errorMessage}")
            } else {
                log.warn("Error: ${connection.responseCode} - ${errorMessage}")
            }
        }

        // TODO: Are these logging statements needed?
        /*
        if (realTimeCI != null) {
            log.info(marker,
            """
            Process: ${processName}
            ${HelperFunctions.bold('───────── Using Real Time Carbon Intensity ─────────')}
            Location: ${HelperFunctions.bold(this.location)}
            ⚡ Real-time Carbon Intensity: ${HelperFunctions.bold(realTimeCI.toString())} gCO₂eq/kWh
            Last updated: ${HelperFunctions.bold(updatedAt)}
            ${HelperFunctions.bold('────────────────────────────────────────────────────')}
            """)
        } else {
            log.warn(marker, "Could not retrieve real-time carbon intensity for process '${processName}' and location ${HelperFunctions.bold(this.location)}.")
        }*/

        return realTimeCI

    }

    /**
     * Computes the carbon intensity value.
     *
     * This method checks if the API key is set and retrieves the real-time carbon intensity.
     * If the API key is not set, it falls back to the location in the CSV.
     *
     * @return The carbon intensity value as a Double, or null if not found.
     */
    private def computeCI() {
        def ci

        if (this.location) {
            this.location = this.location.toUpperCase() // Ensure location is always uppercase

            // Check if the API key is set and retrieve real-time carbon intensity
            if (this.apiKey && this.apiKey instanceof String) {

                log.info(Markers.unique, "API key is set. Attempting to retrieve real-time carbon intensity.")
                

                ci = { String processName = null -> getRealtimeCI(processName) }
                if (ci() != null) {
                    return ci
                }
            } else {
                log.warn(Markers.unique, "API key is not set. Skipping real-time carbon intensity retrieval.")
            }

            // Fallback to the location in the CSV
            ci = this.ciData.findCiInMatrix(this.location)
            log.info(Markers.unique, 
            ci != null ? "Using carbon intensity for ${HelperFunctions.bold(this.location)} from fallback table: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh"
                : "Could not retrieve carbon intensity value for ${HelperFunctions.bold(this.location)} from fallback table. Attempting to retrieve ${HelperFunctions.bold('GLOBAL')} carbon intensity value.")
        } else {
            log.warn(Markers.unique, "No location provided. Attempting to retrieve ${HelperFunctions.bold('GLOBAL')} carbon intensity value.")
        }

        // Fallback to the global default value if no value is found for the location
        if (ci == null) {
            ci = this.ciData.findCiInMatrix('GLOBAL')

            if (ci == null) {
                Exception err = new IllegalStateException("Could not retrieve ${HelperFunctions.bold('GLOBAL')} carbon intensity value from fallback table.")
                log.error(err.getMessage(), err)
            } else {
                log.info(Markers.unique, "Using ${HelperFunctions.bold('GLOBAL')} carbon intensity from fallback table: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh.")
            }
        }
        return ci
    }

}

