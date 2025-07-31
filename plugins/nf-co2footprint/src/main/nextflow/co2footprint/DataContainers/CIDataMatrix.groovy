package nextflow.co2footprint.DataContainers

import groovy.util.logging.Slf4j
import nextflow.co2footprint.utils.HelperFunctions
import nextflow.co2footprint.Logging.Markers

import java.nio.file.Path

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
        String logMessage
        String extraLogInfo = "See `.nextflow.log` for additional occurrences of this message."

        try {
            ci = this.get(targetZone, this.ciColumn)
            logMessage = "Using carbon intensity for ${HelperFunctions.bold(targetZone)} from fallback table: ${HelperFunctions.bold(ci.toString())} gCO₂eq/kWh."
            log.info(Markers.unique, 
                    logMessage + extraLogInfo,
                    'using-ci-from-table-info',
                    logMessage)
        } catch (IllegalArgumentException e) {
            if (targetZone == 'GLOBAL') {
                Exception err = new IllegalStateException("Could not retrieve ${HelperFunctions.bold('GLOBAL')} carbon intensity value from fallback table.")
                log.error(err.getMessage(), err)
                throw err  // <-- will stop execution
            }
            else {
                log.warn(Markers.unique, 
                        "🔁 Could not find carbon intensity for zone ${HelperFunctions.bold(targetZone)}: ${e.message}",
                        'missing-ci-in-table-warning'
                ) 
            }
            return null
        }
        return ci as Double
    }
}