package nextflow.co2footprint

import nextflow.co2footprint.utils.DataMatrix
import nextflow.co2footprint.utils.HelperFunctions
import java.nio.file.Path

import groovy.util.logging.Slf4j
import nextflow.co2footprint.utils.Markers
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
    private final String fallbackModel = 'global'

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


    static CIDataMatrix fromCsv(
            Path path, String separator = ',', Integer columnIndexPos = 0, Integer rowIndexPos = null,
            Object rowIndexColumn = null
    ) throws IOException {
        Map<String, Object> parsedCsv = DataMatrix.readCsv(path, separator, columnIndexPos, rowIndexPos, rowIndexColumn)
        return new CIDataMatrix(
                parsedCsv.data,
                parsedCsv.columnIndex,
                parsedCsv.rowIndex
        )
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
        this.location = location
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
                log.info(Markers.unique,
                """
                ${HelperFunctions.bold('───────── Using Real Time Carbon Intensity ─────────')}
                Location: ${HelperFunctions.bold(this.location)}
                ⚡ Real-time Carbon Intensity: ${HelperFunctions.bold(realTimeCI.toString())} gCO₂eq/kWh
                Last updated: ${HelperFunctions.bold(updatedAt)}
                ${HelperFunctions.bold('────────────────────────────────────────────────────')}
                """)
                return realTimeCI
            } else {
                log.warn(Markers.unique, "Could not retrieve real-time carbon intensity for ${HelperFunctions.bold(this.location)}.")
            }
        } catch (Exception e) {
            log.error(Markers.unique, "Error retrieving real-time carbon intensity for ${HelperFunctions.bold(this.location)}: ${e.message}")
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
                this.location = this.location.toUpperCase() // Ensure location is always uppercase

                // Check if the API key is set and is a valid string
                if (this.apiKey && this.apiKey instanceof String) {
                    log.info(Markers.unique, "API key is set. Attempting to retrieve real-time carbon intensity.")

                    // Attempt to retrieve real-time carbon intensity
                    ci = getRealtimeCI()
                    if (ci != null) {
                        log.info(Markers.unique, "Using real-time carbon intensity for ${HelperFunctions.bold(this.location)}: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh.")
                        return ci
                    } else {
                        log.warn("Could not retrieve real-time carbon intensity for ${HelperFunctions.bold(this.location)}.")
                    }
                } else {
                    log.warn(Markers.unique, "API key is not set or invalid. Skipping real-time carbon intensity retrieval.")
                }

                // Fallback to the location in the CSV
                ci = this.ciData.findCiInMatrix(this.location)
                log.info(Markers.unique, 
                ci != null ? "Using carbon intensity for ${HelperFunctions.bold(this.location)} from fallback table: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh"
                    : "No carbon intensity value found for ${HelperFunctions.bold(this.location)}. Falling back to ${HelperFunctions.bold('global')}.")
            } else {
                log.warn(Markers.unique, "No location provided. Falling back to ${HelperFunctions.bold('global')}.")
            }

            // Fallback to the default model if no value is found for the location
            if (ci == null) {
                ci = this.ciData.findCiInMatrix('global')
                log.info(Markers.unique, ci != null
                    ? "Using ${HelperFunctions.bold('global')} carbon intensity: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh"
                    : "No ${HelperFunctions.bold('global')} carbon intensity found in fallback table.")
            }

            return ci     
        }
    }

}

