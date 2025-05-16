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
            log.info(Markers.unique, "Using carbon intensity for ${HelperFunctions.bold(targetZone)} from fallback table: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh")
        } catch (IllegalArgumentException e) {
            if (targetZone == 'GLOBAL') {
                Exception err = new IllegalStateException("Could not retrieve ${HelperFunctions.bold('GLOBAL')} carbon intensity value from fallback table.")
                log.error(err.getMessage(), err)
                throw err  // <-- will stop execution
            }
            else {
                log.warn(Markers.unique, "Could not find carbon intensity for zone ${HelperFunctions.bold(targetZone)}: ${e.message}")
            }
            return null
        }
        return ci as Double
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
     * Retrieves the real-time carbon intensity (CI) for the configured location using the electricityMaps API.
     *
     * If the API call is successful, logs and returns the real-time CI value.
     * If the API call fails, logs a warning and falls back to the CI value from the local matrix for the location.
     * If no value is found for the location, falls back to the 'GLOBAL' CI value.
     * If 'GLOBAL' is also not found, an exception will be thrown by findCiInMatrix.
     *
     * @param processName (Optional) The process name for logging/marker purposes.
     * @return The carbon intensity value as a Double, or null if not found.
     */
    protected Double getRealtimeCI() {
        // Build the API URL
        URL url = new URL("https://api.electricitymap.org/v3/carbon-intensity/latest?zone=${this.location}")
        
        // Open the connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestProperty("auth-token", this.apiKey)

        Map json = null
        Double ci = null
        String updatedAt = null


        if (connection.responseCode == 200) {
            // Parse the successful API response
            json = new JsonSlurper().parse(connection.inputStream)
            ci = json['carbonIntensity'] as Double
            
            log.info(Markers.unique,"API call successful. Response code: ${connection.responseCode} (${connection.responseMessage})")
        } else {
            // Handle API error response
            String errorResponse = connection.errorStream.text
            String errorMessage = new JsonSlurper().parseText(errorResponse).message

            log.warn(Markers.unique, "API call failed. Response code: ${connection.responseCode} (${errorMessage})")
            
            // Fallback to the location in the CSV
            ci = this.ciData.findCiInMatrix(this.location)
            // Fallback to the global default value if no value is found for the location
            if (ci == null) {
                ci = this.ciData.findCiInMatrix('GLOBAL')
            }
        }
        return ci
    }

    /**
     * Computes the carbon intensity (CI) value to use for calculations.
     *
     * This method checks if a location is provided and ensures it is uppercase.
     * If an API key is set, it returns a closure that retrieves the real-time CI value using the API.
     * If the API key is not set, or if real-time CI retrieval is not possible, it falls back to the
     * location-specific CI value from the local matrix.
     * If no location-specific value is found, it falls back to the 'GLOBAL' CI value.
     *
     * @return A closure for real-time CI retrieval if the API key is set, or a Double value from the matrix.
     *         Returns null if no value is found.
     */
    private def computeCI() {
        def ci

        if (this.location) {
            this.location = this.location.toUpperCase() // Ensure location is always uppercase

            // Check if the API key is set and retrieve real-time carbon intensity
            if (this.apiKey && this.apiKey instanceof String) {

                log.info(Markers.unique, "API key is set. Attempting to retrieve real-time carbon intensity.")
                return { getRealtimeCI() }

            } else {
                log.warn(Markers.unique, "API key is not set. Skipping real-time carbon intensity retrieval.")
            }
            // Fallback to the location in the CSV
            ci = this.ciData.findCiInMatrix(this.location)
        } else {
            log.warn(Markers.unique, "No location provided. Attempting to retrieve ${HelperFunctions.bold('GLOBAL')} carbon intensity value.")
        }
        // Fallback to the global default value if no value is found for the location
        if (ci == null) {
            ci = this.ciData.findCiInMatrix('GLOBAL')
        }
        return ci
    }
}

