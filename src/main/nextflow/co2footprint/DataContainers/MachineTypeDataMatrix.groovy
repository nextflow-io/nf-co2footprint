package nextflow.co2footprint.DataContainers

import groovy.util.logging.Slf4j

import java.nio.file.Path

@Slf4j
class MachineTypeDataMatrix extends DataMatrix {
    String pueId = 'pue'
    String machineTypeId = 'machineType'

    /**
     * Constructor for MachineTypeDataMatrix.
     *
     * @param data         Matrix data
     * @param columnIndex  Column index set
     * @param rowIndex     Row index set
     */
    MachineTypeDataMatrix(
            List<List> data = [], LinkedHashSet<String> columnIndex = [], LinkedHashSet<String> rowIndex = []
    ) {
        super(data, columnIndex, rowIndex)
        checkRequiredColumns(['machineType', 'pue'])
    }

    /**
     * Create a MachineTypeDataMatrix from a CSV file.
     *
     * @param path Path to the CSV file
     * @param separator Separator used in the CSV file (default is ',')
     * @param columnIndexPos Position of the column index (default is 0)
     * @param rowIndexPos Position of the row index (default is null)
     * @param rowIndexColumn Name of the column used for the row index (default is 'executor')
     * @return A MachineTypeDataMatrix object
     */
    static MachineTypeDataMatrix fromCsv(
            Path path,
            String separator = ',', Integer columnIndexPos = 0,
            Integer rowIndexPos = null, Object rowIndexColumn = 'executor'
    ) {
        DataMatrix dataMatrix = DataMatrix.fromCsv(path, separator, columnIndexPos, rowIndexPos, rowIndexColumn)
        return new MachineTypeDataMatrix(dataMatrix.data, dataMatrix.getOrderedColumnKeys(), dataMatrix.getOrderedRowKeys())
    }

    /**
     * Find an executor in the machine type data matrix.
     *
     * @param executor Name of the executor
     * @return The respective line or `null` if the executor is not found.
     */
    MachineTypeDataMatrix matchExecutor(String executor) {
        if (rowIndex.containsKey(executor)) {
            DataMatrix executorData = select([executor] as LinkedHashSet)
            return new MachineTypeDataMatrix(executorData.data, executorData.getOrderedColumnKeys(), executorData.getOrderedRowKeys())
        } else {
            log.warn("Executor '${executor}' not mapped.")
        }
        return null
    }

    /**
     * Return PUE value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm     DataMatrix with values (default: this)
     * @param rowID  ID of the respective row (default: null)
     * @param rowIdx Index of the respective row (default: 0, ignored if rowID is given)
     * @return       PUE value (W)
     */
    BigDecimal getPUE(DataMatrix dm=this, Object rowID=null, Integer rowIdx=0) {
        if (rowID) {
            return dm.get(rowID, pueId) as BigDecimal
        } else {
            return dm.get(rowIdx, pueId, true) as BigDecimal
        }
    }

    /**
     * Return machine type of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm     DataMatrix with values (default: this)
     * @param rowID  ID of the respective row (default: null)
     * @param rowIdx Index of the respective row (default: 0, ignored if rowID is given)
     * @return       Machine type value (W)
     */
    String getMachineType(DataMatrix dm=this, Object rowID=null, Integer rowIdx=0) {
        if (rowID) {
            return dm.get(rowID, machineTypeId) as String
        } else {
            return dm.get(rowIdx, machineTypeId, true) as String
        }
    }
}
