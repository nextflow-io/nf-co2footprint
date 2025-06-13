package nextflow.co2footprint.DataContainers

/**
 * Interface for a Table/Matrix
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
interface Matrix {
    List<List<Object>> data = []
    BiMap<Object, Integer> columnIndex = [:] as BiMap
    BiMap<Object, Integer> rowIndex = [:] as BiMap

    // Select method
    Object get(Object row, Object column, boolean rowRawIndex, boolean columnRawIndex)

    // Select method
    Matrix select(LinkedHashSet<Object> rows, LinkedHashSet<Object> columns)

    // Set methods
    void set(Object value, Object row, Object column, boolean rowRawIndex, boolean columnRawIndex)
}
