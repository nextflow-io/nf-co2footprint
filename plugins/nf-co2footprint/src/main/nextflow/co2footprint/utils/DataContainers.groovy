package nextflow.co2footprint.utils

import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.nio.file.Files

/**
 * Bidirectional Map with maintained K-V pairs in both directions.
 *
 * Allows fast lookup of value by key and key by value.
 * Maintains insertion order and supports unique keys and values.
 *
 * @param <K> Key type
 * @param <V> Value type
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
class BiMap<K, V> {

    // Map from key to value
    private final Map<K, V> keyToValueMap = new LinkedHashMap<>()
    // Map from value to key
    private final Map<V, K> valueToKeyMap = new LinkedHashMap<>()

    /**
     * Construct a BiMap from a map or from parallel lists of keys and values.
     * If both keys and values are provided, they must be unique and of equal length.
     */
    BiMap(Map<K,V> map = [:], List<K> keys = [], List<V> values = []) {
        if (keys && values) {
            assert  keys.size() == values.size()
            assert  keys.unique() && values.unique()
            for (int i = 0; i < keys.size(); i++) { this.put(keys[i], values[i]) }
        }
        else {
            map.each { key, value -> this.put(key, value) }
        }
    }

    /**
     * Checks equality of two BiMap instances by comparing both key-value and value-key maps.
     */
    @Override
    boolean equals(Object other) {
        if ( other == null ) { return false }
        if ( !(other instanceof BiMap) ) { return false }
        BiMap bimap = (BiMap) other
        if (this.keyToValueMap == bimap.keyToValueMap && this.valueToKeyMap == bimap.valueToKeyMap) { return true }
        return false
    }

    /**
     * Put a key-value pair into the bidirectional map.
     * Both key and value must be unique.
     */
    void put(K key, V value)
    {
        keyToValueMap.put(key, value)
        valueToKeyMap.put(value, key)
    }

    /**
     * Get a value based on the key.
     */
    V getValue(K key) {
        return keyToValueMap.get(key)
    }

    /**
     * Get a key based on the value.
     */
    K getKey(V value) {
        return valueToKeyMap.get(value)
    }

    /**
     * Check if a key exists in the map.
     */
    boolean containsKey(K key) {
        return keyToValueMap.containsKey(key)
    }

    /**
     * Check if a value exists in the map.
     */
    boolean containsValue(V value) {
        return valueToKeyMap.containsKey(value)
    }

    /**
     * Filter keys using a closure and return a list of matching keys.
     */
    List filterKeys(Closure filterFunction) {
        return keyToValueMap.keySet().stream().filter(filterFunction).toList()
    }

    /**
     * Filter values using a closure and return a list of matching values.
     */
    List filterValues(Closure filterFunction) {
        return valueToKeyMap.keySet().stream().filter(filterFunction).toList()
    }

    /**
     * Remove a key-value pair based on the key.
     * Returns the removed value.
     */
    V removeByKey(K key) {
        final V value = keyToValueMap.remove(key)
        valueToKeyMap.remove(value)
        return value
    }

    /**
     * Remove a key-value pair based on the value.
     * Returns the removed key.
     */
    K removeByValue(V value) {
        final K key = valueToKeyMap.remove(value)
        keyToValueMap.remove(key)
        return key
    }

    /**
     * Remove all key-value pairs from the bidirectional map.
     * Returns this BiMap for chaining.
     */
    BiMap<K,V> clear() {
        keyToValueMap.clear()
        valueToKeyMap.clear()
        return this
    }

    /**
     * Get a set of all keys in the bidirectional map.
     */
    Set<K> keySet() {
        return keyToValueMap.keySet()
    }

    /**
     * Get a set of all values in the bidirectional map.
     */
    Set<V> valueSet() {
        return valueToKeyMap.keySet()
    }

    /**
    * Get the number of key-value pairs in the map.
    */
    Integer size() {
        return keyToValueMap.size()
    }

    /**
     * Return a new BiMap sorted by values.
     */
    BiMap<K,V> sortByValues() {
        final List<V> values = valueToKeyMap.keySet().sort()
        final List<K> keys = values.collect { value -> valueToKeyMap[value]}

        return new BiMap(null, keys, values)
    }

    /**
     * Return a new BiMap sorted by keys.
     */
    BiMap<K,V> sortByKeys() {
        final List<K> keys = keyToValueMap.keySet().sort()
        final List<V> values = keys.collect { key -> keyToValueMap[key]}

        return new BiMap(null, keys, values)
    }

    /**
     * String representation of the BiMap (shows key-to-value mapping).
     */
    String toString() {
        return keyToValueMap.toString()
    }
}



/**
 * Interface for a Table/Matrix.
 * Defines the basic structure and required methods for a matrix-like data container.
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
interface Matrix {
    // The matrix data as a list of lists (rows of columns)
    List<List<Object>> data = []
    // Column index mapping (column name -> index)
    BiMap<Object, Integer> columnIndex = [:] as BiMap
    // Row index mapping (row name -> index)
    BiMap<Object, Integer> rowIndex = [:] as BiMap

    // Get a value from the matrix at the specified row and column.
    Object get(Object row, Object column, boolean rowRawIndex, boolean columnRawIndex)

    // Select a submatrix by rows and columns.
    Matrix select(LinkedHashSet<Object> rows, LinkedHashSet<Object> columns)

    // Set a value in the matrix at the specified row and column.
    void set(Object value, Object row, Object column, boolean rowRawIndex, boolean columnRawIndex)
}

/**
 * DataMatrix / Table Base Class.
 * Implements a 2D data structure with named row and column indices, supporting selection and CSV I/O.
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
@Slf4j
class DataMatrix implements Matrix {
    // The matrix data as a list of lists (rows of columns)
    protected List<List<Object>> data = []
    // Column index mapping (column name -> index)
    protected BiMap<Object, Integer> columnIndex = [:] as BiMap
    // Row index mapping (row name -> index)
    protected BiMap<Object, Integer> rowIndex = [:] as BiMap

    /**
     * Constructor to initialize DataMatrix with data, columnIndex, and rowIndex.
     * If no indices are provided, defaults to integer ranges.
     *
     * @param data        The data as a list of lists.
     * @param columnIndex The column index as a LinkedHashSet.
     * @param rowIndex    The row index as a LinkedHashSet.
     */
    DataMatrix(
            List<List> data = [],
            LinkedHashSet<Object> columnIndex = [],
            LinkedHashSet<Object> rowIndex = []
    ) throws IllegalStateException  {
        this.data = data

        // Default the indices to integer ranges if not provided
        if (!rowIndex && data.size() > 0) {
            rowIndex = new IntRange(0, data.size() - 1)
        }
        if (!columnIndex && data.size() > 0 && data[0].size() > 0) {
            columnIndex =  new IntRange(0, data[0].size() - 1)
        }

        // Add index into Map
        rowIndex.eachWithIndex { rowIdx, i -> this.rowIndex.put(rowIdx, i) }
        columnIndex.eachWithIndex { columnIdx, i -> this.columnIndex.put(columnIdx, i) }

        // Check integrity
        assertIntegrity()
    }

    /**
     * Read CSV file and return a DataMatrix object.
     * Handles optional row and column indices.
     *
     * @param path            Path to the CSV file.
     * @param separator       Separator used in the CSV file (default is ',').
     * @param columnIndexPos  Position of the column index in the CSV file (default is 0).
     * @param rowIndexPos     Position of the row index in the CSV file (default is null).
     * @param rowIndexColumn  Column name for the row index (default is null).
     * @return                A DataMatrix object containing the data from the CSV file.
     */
    static DataMatrix fromCsv(
            Path path, String separator = ',', Integer columnIndexPos = 0, Integer rowIndexPos = null,
            Object rowIndexColumn = null
    ) throws IOException {
        List<String> lines = Files.readAllLines(path)

        // Extract column index from the specified line
        LinkedHashSet<Object> columnIndex = columnIndexPos != null ? lines.remove(columnIndexPos).split(separator) : null

        // Handle row index column if specified
        if (rowIndexPos != null) {
            rowIndexColumn = columnIndex[rowIndexPos]
        }
        if (rowIndexColumn != null) {
            rowIndexPos = columnIndex.findIndexOf { it == rowIndexColumn } as Integer
            columnIndex.remove(rowIndexColumn)
        }

        // Initialize row index and data
        LinkedHashSet<Object> rowIndex = []
        List<List<Object>> data = []
        boolean escaped = false
        int start = 0
        int end = 0

        // Parse each line of the CSV, handling quoted fields and separators
        lines.each { line ->
            List<Object> row = []
            line.eachWithIndex { character, i ->
                end = i
                if (character == separator && !escaped) {
                    row.add(inferTypeOfString(line.substring(start, end)))
                    start = i + 1
                } else if (character == '"') {
                    escaped = !escaped
                }
            }
            row.add(inferTypeOfString(line.substring(start, end + 1)))
            start = 0

            // Extract row index if specified
            if (rowIndexPos != null) {
                Object rowIdx = row[rowIndexPos]
                row.remove(rowIdx)
                rowIndex.add(rowIdx)
            }

            // Add row to data
            data.add(row)
        }

        return new DataMatrix(data, columnIndex, rowIndex)

    }

     // --- Integrity checks ---

    /**
     * Ensure all rows have the same length as the first row.
     * Throws IllegalStateException if not.
     */
    void assertRowLengthEqual() throws IllegalStateException  {
       data.eachWithIndex { row, i ->
           if (row.size() != this.data[0].size()) {
               final String message = "Length of row ${i} (${row.size()}) does not match size preceding rows (${this.data[0].size()})."
               log.error(message)
               throw new IllegalStateException(message)
           }
       }
    }

    /**
     * Ensure the number of rows matches the row index size.
     */
    private void assertRowIndexLengthMatches() throws IllegalStateException {
        if (this.data.size() != this.rowIndex.size()) {
            final String message = "Data size ${this.data.size()} does not match rowIndex length ${this.rowIndex.size()}"
            log.error(message)
            throw new IllegalStateException(message)
        }
    }

    /**
     * Ensure the number of columns matches the column index size.
     */
    void assertColumnIndexLengthMatches() throws IllegalStateException  {
        if (this.data.size() == 0 && this.columnIndex.size() != 0) {
            final String message = 'Passed column index without data.'
            log.error(message)
            throw new IllegalStateException(message)
        } else if (this.data.size() > 0 && this.data[0].size() != this.columnIndex.size()) {
            final String message = "Data length ${this.data[0].size()} does not match rowIndex length ${this.columnIndex.size()}"
            log.error(message)
            throw new IllegalStateException(message)
        }
    }

    /**
     * Run all integrity checks for the matrix.
     */
    void assertIntegrity() throws IllegalStateException  {
        assertRowLengthEqual()

        assertRowIndexLengthMatches()
        assertColumnIndexLengthMatches()
    }

    /**
     * Compare two DataMatrix objects for equality (data and indices).
     */
    @Override
    boolean equals(Object other) {
        if ( other == null ) { return false }
        if ( !(other instanceof DataMatrix) ) { return false }
        DataMatrix dm = (DataMatrix) other
        if (this.data == dm.data && this.rowIndex == dm.rowIndex && this.columnIndex == dm.columnIndex) { return true }
        return false
    }

    /**
     * Returns true if the matrix contains any data.
     */
    boolean asBoolean(){
        return this.data.size() != 0 && this.data[0].size() != 0
    }

    /**
     * Helper to collect index positions for a set of keys from a BiMap.
     */
    private static List<Integer> collectIndices(LinkedHashSet<Object> keys, BiMap<Object, Integer> bimap) {
        List<Integer> indices = keys.collect( { key -> bimap.getValue(key) } )
        indices.removeAll( {it == null })
        return indices
    }

    /**
     * Select only the specified rows from the DataMatrix.
     * Returns a new DataMatrix with the selected rows.
     */
    private DataMatrix selectRows(LinkedHashSet<Object> rows){
        final List<Integer> iList = collectIndices(rows, this.rowIndex)
        List<List<Object>> data = this.data[iList]

        return new DataMatrix(data, this.columnIndex.keySet() as LinkedHashSet, rows)
    }

    /**
     * Select only the specified columns from the DataMatrix.
     * Returns a new DataMatrix with the selected columns.
     */
    private DataMatrix selectColumns(LinkedHashSet<Object> columns){
        // Collect indices
        final List<Integer> iList = collectIndices(columns, this.columnIndex)
        List<List<Object>> data = this.data.collect { row -> row[iList] }

        return new DataMatrix(data, columns, this.rowIndex.keySet() as LinkedHashSet)
    }

    /**
     * Select a submatrix by rows and columns.
     * If no rows or columns are specified, selects all.
     */
    DataMatrix select(
            LinkedHashSet<Object> rows=null,
            LinkedHashSet<Object> columns=null
    ){
        rows = rows ?: this.rowIndex.keySet()
        columns = columns ?: this.columnIndex.keySet()
        return this.selectRows(rows).selectColumns(columns)
    }

    /**
     * Get the matrix data as a list of lists.
     */
    List<List> getData() {
        return this.data
    }

    /**
     * Get the row index BiMap.
     */
    BiMap<Object, Integer> getRowIndex() {
        return this.rowIndex
    }

    /**
     * Get the column index BiMap.
     */
    BiMap<Object, Integer> getColumnIndex() {
        return this.columnIndex
    }

    /**
     * Return the row keys in matrix order.
     */
    LinkedHashSet getOrderedRowKeys() {
        return this.rowIndex.valueSet().sort().collect {i -> this.rowIndex.getKey(i)}
    }

    /**
     * Return the column keys in matrix order.
     */
    LinkedHashSet getOrderedColumnKeys() {
        return this.columnIndex.valueSet().sort().collect {i -> this.columnIndex.getKey(i)}
    }

    /**
     * Get a value from the matrix by row and column.
     * Row and column can be names or integer indices, controlled by rowRawIndex/columnRawIndex.
     */
    Object get(Object row, Object column, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        // Resolve row index
        row = rowRawIndex ? row as Integer : this.rowIndex.getValue(row)
        if (row == null) {
            final String message = "Row '${rowRawIndex ? row : row as String}' not found in the row index."
            log.error(message)
            throw new IllegalArgumentException(message)
        }

        // Resolve column index
        column = columnRawIndex ? column as Integer : this.columnIndex.getValue(column)
        if (column == null) {
            final String message = "Column '${columnRawIndex ? column : column as String}' not found in the column index."
            log.error(message)
            throw new IllegalArgumentException(message)
        }

        // Return the data at the resolved row and column
        return this.data[row][column]
    }

    /**
     * Set a value in the matrix at the specified row and column.
     * Row and column can be names or integer indices, controlled by rowRawIndex/columnRawIndex.
     */
    void set(Object value, Object row, Object column, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        row = rowRawIndex ? row as Integer : this.rowIndex.getValue(row)
        column = columnRawIndex ? column as Integer : this.columnIndex.getValue(column)

        data[row][column] = value
    }

    /**
     * Put a row into the DataMatrix. If the given rowId is already present, the old row is replaced.
     *
     * @param row List of Objects to be added at the rowId
     * @param rowId ID of the row
     */
    void putRow(List<Object> row, Object rowId) {
        assert row.size() == this.data[0].size()
        Integer rowIdx = this.rowIndex.getValue(rowId)

        if (rowIdx != null) {
            data[rowIdx] = row
        }
        else {
            rowIdx = data.size()
            data.add(row)
        }

        this.rowIndex.put(rowId, rowIdx)
    }

    /**
     * Save the matrix data as a CSV file.
     * Handles quoting of values containing the separator.
     */
    void saveCsv(Path path, String separator=',') {
        String csvString = ''
        BiMap<Object, Integer> sortedCols = this.columnIndex.sortByValues()
        sortedCols.keySet().each { colIdx ->
            csvString += separator + colIdx.toString()
        }
        csvString += '\n'
        this.data.eachWithIndex {row, i ->
            csvString += this.rowIndex.getKey(i).toString()
            row.each { element ->
                String elementString =  element.toString()
                elementString = elementString.contains(separator) ? "\"${elementString}\"" : elementString
                csvString = csvString + "${separator}${elementString}"
            }
            csvString = csvString + '\n'
        }
        final byte[] byteString = csvString.getBytes()

        Files.write(path, byteString)
    }

    /**
     * Infer simple numeric data types from a String.
     * Tries Integer, then Double, otherwise returns the original String.
     */
    private static def inferTypeOfString(String str) {
        try { return Integer.parseInt(str) } catch(NumberFormatException ignore){ }
        try { return Double.parseDouble(str) } catch(NumberFormatException ignore){ }
        return str
    }

    /**
     * Convert the class into a readable / printable String.
     */
    String toString() {
        final LinkedHashSet<Object> sortedColumnsIndex = getOrderedColumnKeys()
        String stringRepresentation = "\t\t${sortedColumnsIndex.toString()}"
        data.eachWithIndex {row, i  ->
            stringRepresentation += "\n${this.rowIndex.getKey(i)}\t${row.toString()}"
        }
        return stringRepresentation
    }

    /**
    * Checks if the given DataMatrix contains all required columns.
    * If not, it logs an error and throws an IllegalStateException.
    *
    * @param matrix The DataMatrix to check.
    * @param requiredColumns The list of required column names.
    */
    void checkRequiredColumns(List<String> requiredColumns) {
        def matrixColumns = this.columnIndex.keySet()
        if (!matrixColumns.containsAll(requiredColumns)) {
            def missing = requiredColumns - matrixColumns
            log.error("CSV is missing required columns: ${missing}")
            throw new IllegalStateException("CSV is missing required columns: ${missing}")
        }
    }
}
