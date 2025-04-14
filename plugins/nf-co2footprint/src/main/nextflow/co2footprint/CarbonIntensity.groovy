package nextflow.co2footprint

import nextflow.co2footprint.utils.DataMatrix
import nextflow.co2footprint.utils.HelperFunctions

import groovy.util.logging.Slf4j
import groovy.json.JsonSlurper

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

    private final String ciID = "Carbon intensity gCO₂eq/kWh (Life cycle)"
    private final String zoneID = "Zone id"
    String fallbackModel = 'global'
    Double ci = null
    String zone = null

    CIDataMatrix(
            List<List> data = [], LinkedHashSet<String> columnIndex = [], LinkedHashSet<String> rowIndex = [],
            Object fallbackModel = 'global', Double ci = null, String zone = null
    ) {
        super(data, columnIndex, rowIndex) // Initialize DataMatrix
        this.fallbackModel = fallbackModel
        this.ci = ci
        this.zone = zone
    }

    /**
     * Finds the carbon intensity value for a given zone in the CSV data.
     *
     * @param targetZone The zone to search for (e.g., "US", "EU", "Global"). Must be a non-null string.
     * @return The carbon intensity value (as a Double) from the target column in the same row as the specified zone,
     *         or null if the zone is not found.
     */
    private Double findCiInMatrix(String targetZone) {
        if (!targetZone) {
            throw new IllegalArgumentException("Search value cannot be null or empty.")
        }

        Integer searchColumnIndex = this.getColumnIndex().getValue(this.zoneID)
        Integer targetColumnIndex = this.getColumnIndex().getValue(this.ciID)

        if (searchColumnIndex == null || targetColumnIndex == null) {
            throw new IllegalArgumentException("Invalid column name(s): ${this.zoneID}, ${this.ciID}")
        }

        return this.getData().find { row ->
            row[searchColumnIndex]?.toString().equalsIgnoreCase(targetZone)
        }?.get(targetColumnIndex)?.toDouble()
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
        this.location = location.toUpperCase() // Ensure location is always uppercase
        this.ciData = ciData
    }

    /**
     * Retrieves real-time carbon intensity from the Electricity Maps API.
     *
     * @return The real-time carbon intensity as a Double, or null if unavailable.
     */
    private Double getRealtimeCI() {
        try {
            def command = "curl 'https://api.electricitymap.org/v3/carbon-intensity/latest?zone=${this.location}' -H 'auth-token: ${this.apiKey}'"
            def API_response = ['bash', '-c', command].execute()

            def json = new JsonSlurper().parseText(API_response.text)
            def realTimeCI = json['carbonIntensity'] as Double
            def updatedAt = HelperFunctions.transformTimestamp(json['updatedAt'] as String)

            if (realTimeCI != null) {
                log.info("${HelperFunctions.bold('───────── Using Real Time Carbon Intensity ─────────')}")
                log.info("📍 Location: ${HelperFunctions.bold(this.location)}")
                log.info("⚡ Real-time Carbon Intensity: ${HelperFunctions.bold(realTimeCI.toString())} gCO₂eq/kWh")
                log.info("🕒 Last updated: ${HelperFunctions.bold(updatedAt)}")
                log.info("${HelperFunctions.bold('────────────────────────────────────────────────────')}")
                return realTimeCI
            } else {
                log.warn("Could not retrieve real-time carbon intensity for ${HelperFunctions.bold(this.location)}.")
            }
        } catch (Exception e) {
            log.error("Error retrieving real-time carbon intensity for ${HelperFunctions.bold(this.location)}: ${e.message}")
        }
        return null
    }

    /**
    * Determines the carbon intensity for a given zone.
    *
    * @return A closure that retrieves the carbon intensity value when invoked.
    */
    private Closure<Double> getCI() {
        return { ->
            def ci

            if (this.location) {

                // Check if the API key is set and is a valid string
                if (this.apiKey && this.apiKey instanceof String) {
                    log.info("API key is set. Attempting to retrieve real-time carbon intensity.")

                    // Attempt to retrieve real-time carbon intensity
                    ci = getRealtimeCI()
                    if (ci != null) {
                        log.info("Using real-time carbon intensity for ${HelperFunctions.bold(this.location)}: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh")
                        return ci
                    } else {
                        log.warn("Could not retrieve real-time carbon intensity for ${HelperFunctions.bold(this.location)}.")
                    }
                } else {
                    log.warn("API key is not set or invalid. Skipping real-time carbon intensity retrieval.")
                }

                // Fallback to the location in the CSV
                ci = this.ciData.findCiInMatrix(this.location)
                log.info(ci != null
                    ? "Using carbon intensity for ${HelperFunctions.bold(this.location)} from fallback table: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh"
                    : "No carbon intensity value found for ${HelperFunctions.bold(this.location)}. Falling back to ${HelperFunctions.bold(this.defaultModel)}.")
            } else {
                log.warn("No location provided. Falling back to ${HelperFunctions.bold(this.defaultModel)}.")
            }

            // Fallback to the default model if no value is found for the location
            if (ci == null) {
                ci = this.ciData.findCiInMatrix(this.defaultModel)
                log.info(ci != null
                    ? "Using ${HelperFunctions.bold(this.defaultModel)} carbon intensity: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh"
                    : "No ${HelperFunctions.bold(this.defaultModel)} carbon intensity found in fallback table.")
            }

            return ci     
        }
    }

}

