package nextflow.co2footprint.DataContainers


import nextflow.co2footprint.utils.HelperFunctions
import groovy.util.logging.Slf4j
import nextflow.co2footprint.Logging.Markers
import groovy.json.JsonSlurper


/**
 * Class to compute carbon intensity (CI) values.
 *
 * This class uses CIDataMatrix to manage and retrieve carbon intensity data.
 *
 * @author: Nadja Volkmann <nadja.volkmann@uni-tuebingen.de>
 */
@Slf4j
class CIValueComputer {
    String emApiKey
    String location
    CIDataMatrix ciData

    CIValueComputer(String emApiKey, String location, CIDataMatrix ciData) {
        this.emApiKey = emApiKey
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
        URL url = new URI("https://api.electricitymap.org/v3/carbon-intensity/latest?zone=${this.location}").toURL()

        // Open the connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestProperty("auth-token", this.emApiKey)

        Map json
        Double ci

        if (connection.responseCode == 200) {
            // Parse the successful API response
            json = new JsonSlurper().parse(connection.inputStream) as Map
            ci = json['carbonIntensity'] as Double
            log.info(Markers.unique,
                     "API call successful. Response code: ${connection.responseCode} (${connection.responseMessage})",
                     'api-call-successful-info')
        } else {
            // Handle API error response
            String errorResponse = connection.errorStream.text
            String errorMessage = new JsonSlurper().parseText(errorResponse).message
            log.warn(Markers.unique, 
                    "API call failed. Response code: ${connection.responseCode} (${errorMessage})",
                    'api-call-failed-warning')

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
    def computeCI() {
        def ci

        if (this.location) {
            this.location = this.location.toUpperCase() // Ensure location is always uppercase

            // Check if the API key is set and retrieve real-time carbon intensity
            if (this.emApiKey && this.emApiKey instanceof String) {

                log.info(Markers.unique, "Electricity Maps API key is set. Attempting to retrieve real-time carbon intensity.")
                return { getRealtimeCI() }

            } else {
                log.info(
                    Markers.unique,
                    "Electricity Maps API key is not set. " +
                    "To retrieve real-time carbon intensity values, please provide a key with the parameter `emApiKey`.\n" +
                    "\t💡 You can obtain a key for ElectricityMaps at https://portal.electricitymaps.com/auth/login.")
            }
            // Fallback to the location in the CSV
            ci = this.ciData.findCiInMatrix(this.location)
        } else {
            log.warn(Markers.unique, "No location provided. Attempting to retrieve GLOBAL carbon intensity value.")
        }
        // Fallback to the global default value if no value is found for the location
        if (ci == null) {
            ci = this.ciData.findCiInMatrix('GLOBAL')
        }
        return ci
    }
}
