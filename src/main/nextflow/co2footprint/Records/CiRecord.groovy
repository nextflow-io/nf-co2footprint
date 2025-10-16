package nextflow.co2footprint.Records

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.DataContainers.CIMatch
import nextflow.co2footprint.Logging.Markers

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Slf4j
class CiRecord {
    // Time of the CI recording
    LocalDateTime time = null

    // Carbon intensity value
    Number value = null

    // Connection to Electricity Maps API
    Closure<HttpURLConnection> connectAPI = null

    // JSON Parser
    JsonSlurper jsonSlurper = null


    CiRecord(
            Number value=null,
            CIDataMatrix ciDataMatrix=null,
            String location=null,
            String emApiKey=null,
            LocalDateTime time=LocalDateTime.now()
    ) {
        // Ensure location is upper case
        location = location?.toUpperCase()

        // Define time
        this.time = time

        // Determine CI in the order: given value -> EM API -> location from table -> GLOBAL from table
        if (value == null) {
            CIMatch ci = ciDataMatrix.findCiInMatrix(location, 'GLOBAL')
            value = ci?.value

            // Checks whether API key was given to open a connection to Electricity Maps
            if (emApiKey) {
                log.info(
                    Markers.silentUnique,
                    "Electricity Maps API key is set. Attempting to retrieve real-time carbon intensity."
                )

                // Build the API URL
                URL url = new URI("https://api.electricitymap.org/v3/carbon-intensity/latest?zone=${location}").toURL()

                // Open the connection
                this.connectAPI = {
                    HttpURLConnection ciApiConnection = url.openConnection() as HttpURLConnection
                    ciApiConnection.setRequestProperty("auth-token", emApiKey)
                    return ciApiConnection
                }

                // Define the slurper
                this.jsonSlurper = new JsonSlurper()
            }
            else {
                log.info(
                    Markers.silentUnique,
                    "Electricity Maps API key is not set. " +
                    "To retrieve real-time carbon intensity values, please provide a key with the parameter `emApiKey`.\n" +
                    "\t💡 You can obtain a key for ElectricityMaps at https://portal.electricitymaps.com/auth/login."
                )
                log.info(
                    Markers.silentUnique,
                    "Using fallback carbon intensity from ${ci?.zone} from CI table: ${value} gCO₂eq/kWh.",
                    'using-ci-from-table-info'
                )
            }
        }
        else {
            log.info(
                Markers.silentUnique,
                "Using given value: ${value} gCO₂eq/kWh.",
                'using-given-ci'
            )
        }

        this.value = value
    }

    /**
     * Attempt to retrieve the real-time carbon intensity (CI) for the configured location using the electricityMaps API.
     *
     * If the API call is successful, logs and returns the real-time CI value.
     * Otherwise the previous CI value is used (fallback from table for location was defined during initialization).
     */
    void update() {
        if (!connectAPI) { return }
        log.debug('Attempting update of CI value.')

        HttpURLConnection ciApiConnection = connectAPI()
        if (ciApiConnection.responseCode == HttpURLConnection.HTTP_OK) {
            // Parse the successful API response
            Map json = jsonSlurper.parse(ciApiConnection.inputStream) as Map

            this.time = Instant.parse(json['datetime'] as String).atZone(ZoneId.systemDefault()).toLocalDateTime()
            this.value = json['carbonIntensity'] as Double
            log.info(
                    Markers.unique,
                    "API call successful. " +
                    "CI: ${this.value} gCO₂e/kWh (${this.time.format(DateTimeFormatter.ofPattern('dd.MM.yyyy HH:mm:ss'))}). " +
                    "Response code: ${ciApiConnection.responseCode} (${ciApiConnection.responseMessage})."
            )
        }
        // Throw a warning, while keeping the previous ci value
        else {
            // Handle API error response
            log.warn(
                Markers.unique,
                "API call failed. Response code: ${ciApiConnection.responseCode} -> ${ciApiConnection.errorStream.text}"
            )
        }
    }

    /**
     * Method which is picked up by ConfigEntry to return the raw value of an instance.
     *
     * @return The current carbon intensity
     */
    Number evaluate() { return value }
}
