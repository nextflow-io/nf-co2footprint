package nextflow.co2footprint.DataContainers

import groovy.transform.Canonical
import groovy.transform.TupleConstructor
import groovy.util.logging.Slf4j
import nextflow.co2footprint.CO2FootprintFactory
import nextflow.co2footprint.Logging.Markers

import java.nio.file.Path
import java.nio.file.Paths

/**
 * A match onto the CI matrix with associated zone and ci value.
 */
@Canonical class CIMatch {
    String zone
    Number value
}

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
    static final CIDataMatrix ciDataMatrix = readCiDataMatrix()

    /**
     * External Data integration of CI (Carbon intensity) values.
     *
     * @return The CI data as a matrix
     */
    static CIDataMatrix readCiDataMatrix() {
        return fromCsv(
                Paths.get(CO2FootprintFactory.class.getResource('/ci_data/ci_yearly_2024_by_location.csv').toURI())
        )
    }

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
     * @param targetZone The zone for which to retrieve the carbon intensity
     * @param fallbackZone The zone for which to retrieve the carbon intensity if the targetZone is not in CI table
     * @return The carbon intensity value as a Number matched with the zone it was found for in a {@link CIMatch}
     * @throws IllegalStateException
     */
    CIMatch findCiInMatrix(String targetZone, String fallbackZone=null) throws IllegalStateException {
        if (rowIndex.containsKey(targetZone)){
            Number ci = get(targetZone, this.ciColumn) as Double
            return new CIMatch(targetZone, ci)
        }
        else if (fallbackZone != null) {
            log.warn(
                Markers.silentUnique,
                "Target zone ${targetZone} not found. Attempting to retrieve carbon intensity for fallback zone ${fallbackZone}."
            )
            return findCiInMatrix(fallbackZone)
        }
        else {
            String message = "${targetZone} not in CI table."
            log.error(message)
            throw new IllegalStateException(message)  // <-- will stop execution
        }
    }
}