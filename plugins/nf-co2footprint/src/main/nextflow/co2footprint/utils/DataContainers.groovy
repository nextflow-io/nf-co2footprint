
/**
 * This script defines utility classes and interfaces for working with bidirectional maps and matrix-like data structures.
 *
 * Classes and Interfaces:
 * 1. **BiMap<K, V>**:
 *    - A bidirectional map that maintains a one-to-one relationship between keys and values.
 *    - Allows efficient lookups in both directions (key-to-value and value-to-key).
 *    - Includes methods for adding, removing, and querying key-value pairs, as well as sorting and filtering.
 *
 * 2. **Matrix (Interface)**:
 *    - Defines the structure for a matrix-like data structure.
 *    - Includes methods for getting, setting, and selecting data entries.
 *
 * 3. **DataMatrix**:
 *    - A concrete implementation of the `Matrix` interface.
 *    - Represents a table-like data structure with rows and columns.
 *    - Supports operations such as selecting subsets of rows/columns, saving/loading data to/from CSV files, and validating data integrity.
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
 package nextflow.co2footprint.utils

import java.nio.file.Path
import java.nio.file.Files



/**
 * A Bidirectional Map (BiMap) that maintains a one-to-one relationship between keys and values.
 * @param <K> The type of keys maintained by this map.
 * @param <V> The type of values maintained by this map.
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
class BiMap<K, V> {

    private final Map<K, V> keyToValueMap = new LinkedHashMap<>()
    private final Map<V, K> valueToKeyMap = new LinkedHashMap<>()

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
    * Checks if this BiMap is equal to another object.
    *
    * @param other The object to compare with this BiMap.
    * @return true if the other object is a BiMap and both the key-to-value map
    *         and value-to-key map are equal; false otherwise.
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
     * Method to put a key-value pair into the bidirectional map
     * @param key
     * @param value
     */
    void put(K key, V value)
    {
        keyToValueMap.put(key, value)
        valueToKeyMap.put(value, key)
    }

    // method to get a value based on the key
    V getValue(K key) {
        return keyToValueMap.get(key)
    }

    // method to get a key based on the value
    K getKey(V value) {
        return valueToKeyMap.get(value)
    }

    // method to check if a key exists in the map
    boolean containsKey(K key) {
        return keyToValueMap.containsKey(key)
    }

    // method to check if a value exists in the map
    boolean containsValue(V value) {
        return valueToKeyMap.containsKey(value)
    }

    // method to check if a key exists in the map
    List filterKeys(Closure filterFunction) {
        return keyToValueMap.keySet().stream().filter(filterFunction).toList()
    }

    // method to filter the Values for a function
    List filterValues(Closure filterFunction) {
        return valueToKeyMap.keySet().stream().filter(filterFunction).toList()
    }

    // method to remove a key-value pair based on the key
    V removeByKey(K key) {
        V value = keyToValueMap.remove(key)
        valueToKeyMap.remove(value)
        return value
    }

    // method to remove a key-value pair based on the key
    K removeByValue(V value) {
        K key = valueToKeyMap.remove(value)
        keyToValueMap.remove(key)
        return key
    }

    // method to remove all key-value pairs from the bidirectional map
    BiMap<K,V> clear() {
        keyToValueMap.clear()
        valueToKeyMap.clear()
        return this
    }

    // method to get a set of all keys in the bidirectional map
    Set<K> keySet() {
        return keyToValueMap.keySet()
    }

    // method to get a set of all values in the bidirectional map
    Set<V> valueSet() {
        return valueToKeyMap.keySet()
    }

    Integer size() {
        return keyToValueMap.size()
    }

    BiMap<K,V> sortByValues() {
        List<V> values = valueToKeyMap.keySet().sort()
        List<K> keys = values.collect { value -> valueToKeyMap[value]}

        return new BiMap(null, keys, values)
    }

    BiMap<K,V> sortByKeys() {
        List<K> keys = keyToValueMap.keySet().sort()
        List<V> values = keys.collect { key -> keyToValueMap[key]}

        return new BiMap(null, keys, values)
    }

    String toString() {
        return keyToValueMap.toString()
    }
}



/**
 * Interface for a Table/Matrix
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
interface Matrix {
    List<List<Object>> data = []
    BiMap<Object, Integer> columnIndex = [:] as BiMap
    BiMap<Object, Integer> rowIndex = [:] as BiMap

    // Get method
    Object get(Object row, Object column, boolean rowRawIndex, boolean columnRawIndex)

    // Select method
    Matrix select(LinkedHashSet<Object> rows, LinkedHashSet<Object> columns)

    // Set methods
    void set(Object value, Object row, Object column, boolean rowRawIndex, boolean columnRawIndex)
}

/**
 * DataMatrix / Table Base Class
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
class DataMatrix implements Matrix {
    List<List<Object>> data = []
    protected BiMap<Object, Integer> columnIndex = [:] as BiMap
    protected BiMap<Object, Integer> rowIndex = [:] as BiMap

    // Constructor
    DataMatrix(
            List<List> data = [],
            LinkedHashSet<Object> columnIndex = [],
            LinkedHashSet<Object> rowIndex = []
    ) throws IllegalStateException  {
        this.data = data

        // Default the Indices with a Integer Range
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

    // Integrity tests

    /**
    * Ensures that all rows in the data matrix have the same length.
    *
    * @throws IllegalStateException if any row has a different length than the first row.
    */
    void assertRowLengthEqual() throws IllegalStateException  {
       data.eachWithIndex { row, i ->
           if (row.size() != this.data[0].size()) {
               throw new IllegalStateException(
                       "Length of row ${i} (${row.size()}) does not match size preceding rows (${this.data[0].size()})."
               )
           }
       }
    }

    /**
    * Ensures that the number of rows in the data matches the size of the row index.
    *
    * @throws IllegalStateException if the number of rows in the data does not match the size of the row index.
    */
    private void assertRowIndexLengthMatches() throws IllegalStateException {
        if (this.data.size() != this.rowIndex.size()) {
            throw new IllegalStateException(
                    "Data size ${this.data.size()} does not match rowIndex length ${this.rowIndex.size()}"
            )
        }
    }

    /**
    * Ensures that the number of columns in the data matches the size of the column index.
    *
    * @throws IllegalStateException if the number of columns in the data does not match the size of the column index.
    */
    void assertColumnIndexLengthMatches() throws IllegalStateException  {
        if (this.data.size() == 0 && this.columnIndex.size() != 0) {
            throw new IllegalStateException(
                    'Passed column index without data.'
            )
        } else if (this.data.size() > 0 && this.data[0].size() != this.columnIndex.size()) {
            throw new IllegalStateException(
                    "Data length ${this.data[0].size()} does not match rowIndex length ${this.columnIndex.size()}"
            )
        }
    }
    
    /**
    * Validates the integrity of the data matrix by checking row lengths, row index size, and column index size.
    *
    * @throws IllegalStateException if any integrity check fails.
    */
    void assertIntegrity() throws IllegalStateException  {
        assertRowLengthEqual()

        assertRowIndexLengthMatches()
        assertColumnIndexLengthMatches()
    }

    /**
    * Checks if this DataMatrix is equal to another object.
    *
    * @param other The object to compare with this DataMatrix.
    * @return true if the other object is a DataMatrix and has the same data, row index, and column index; false otherwise.
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
    * Checks if the DataMatrix contains data.
    *
    * @return true if the matrix has at least one row and one column; false otherwise.
    */
    boolean asBoolean(){
        return this.data.size() != 0 && this.data[0].size() != 0
    }

    /**
    * Collects indices from a BiMap based on the provided keys.
    *
    * @param keys The keys to collect indices for.
    * @param bimap The BiMap to retrieve indices from.
    * @return A list of indices corresponding to the provided keys.
    */
    private static List<Integer> collectIndices(LinkedHashSet<Object> keys, BiMap<Object, Integer> bimap) {
        List<Integer> indices = keys.collect( { key -> bimap.getValue(key) } )
        indices.removeAll( {it == null })
        return indices
    }

    /**
    * Selects specific rows from the DataMatrix.
    *
    * @param rows The set of row keys to select.
    * @return A new DataMatrix containing only the selected rows.
    */
    private DataMatrix selectRows(LinkedHashSet<Object> rows){
        List<Integer> iList = collectIndices(rows, this.rowIndex)
        List<List<Object>> data = this.data[iList]

        return new DataMatrix(data, this.columnIndex.keySet() as LinkedHashSet, rows)
    }

    /**
    * Selects specific columns from the DataMatrix.
    *
    * @param columns The set of column keys to select.
    * @return A new DataMatrix containing only the selected columns.
    */
    private DataMatrix selectColumns(LinkedHashSet<Object> columns){
        // Collect indices
        List<Integer> iList = collectIndices(columns, this.columnIndex)
        List<List<Object>> data = this.data.collect { row -> row[iList] }

        return new DataMatrix(data, columns, this.rowIndex.keySet() as LinkedHashSet)
    }

    /**
    * Selects a subset of the DataMatrix based on specified rows and columns.
    *
    * @param rows The set of row keys to select (optional, defaults to all rows).
    * @param columns The set of column keys to select (optional, defaults to all columns).
    * @return A new DataMatrix containing the selected rows and columns.
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
    * Retrieves the data of the DataMatrix as a list of lists.
    *
    * @return The data of the matrix.
    */
    List<List> getData() {
        return this.data
    }

    /**
    * Retrieves the row index of the DataMatrix.
    *
    * @return The BiMap representing the row index.
    */
    BiMap<Object, Integer> getRowIndex() {
        return this.rowIndex
    }

    /**
    * Retrieves the column index of the DataMatrix.
    *
    * @return The BiMap representing the column index.
    */
    BiMap<Object, Integer> getColumnIndex() {
        return this.columnIndex
    }

    /**
    * Retrieves the row keys in the order they appear in the DataMatrix.
    *
    * @return A LinkedHashSet of row keys in order.
    */
    LinkedHashSet getOrderedRowKeys() {
        return this.rowIndex.valueSet().sort().collect {i -> this.rowIndex.getKey(i)}
    }

    /**
    * Retrieves the column keys in the order they appear in the DataMatrix.
    *
    * @return A LinkedHashSet of column keys in order.
    */
    LinkedHashSet getOrderedColumnKeys() {
        return this.columnIndex.valueSet().sort().collect {i -> this.columnIndex.getKey(i)}
    }

    /**
    * Retrieves a specific data entry from the DataMatrix.
    *
    * @param row The row key or index.
    * @param column The column key or index.
    * @param rowRawIndex Whether the row is specified as a raw index (default: false).
    * @param columnRawIndex Whether the column is specified as a raw index (default: false).
    * @return The data entry at the specified row and column.
    */
    Object get(Object row, Object column, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        row = rowRawIndex ? row as Integer : this.rowIndex.getValue(row)
        column = columnRawIndex ? column as Integer : this.columnIndex.getValue(column)

        return this.data[row][column]
    }

    /**
    * Sets a specific data entry in the DataMatrix.
    *
    * @param value The value to set.
    * @param row The row key or index.
    * @param column The column key or index.
    * @param rowRawIndex Whether the row is specified as a raw index (default: false).
    * @param columnRawIndex Whether the column is specified as a raw index (default: false).
    */
    void set(Object value, Object row, Object column, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        row = rowRawIndex ? row as Integer : this.rowIndex.getValue(row)
        column = columnRawIndex ? column as Integer : this.columnIndex.getValue(column)

        data[row][column] = value
    }

    /**
    * Saves the DataMatrix to a CSV file.
    *
    * @param path The file path to save the CSV to.
    * @param separator The separator to use in the CSV (default: ',').
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
        byte[] byteString = csvString.getBytes()

        Files.write(path, byteString)
    }

    /**
    * Infers the type of a string (Integer, Double, or String).
    *
    * @param str The string to infer the type of.
    * @return The inferred type (Integer, Double, or String).
    */
    private static def inferTypeOfString(String str) {
        try { return Integer.parseInt(str) } catch(NumberFormatException ignore){ }
        try { return Double.parseDouble(str) } catch(NumberFormatException ignore){ }
        return str
    }

    /**
    * Loads a DataMatrix from a CSV file.
    *
    * @param path The file path to load the CSV from.
    * @param separator The separator used in the CSV (default: ',').
    * @param columnIndexPos The position of the column index row (default: 0).
    * @param rowIndexPos The position of the row index column (optional).
    * @param rowIndexColumn The name of the row index column (optional).
    * @return A new DataMatrix loaded from the CSV file.
    */
    static DataMatrix loadCsv(
            Path path, String separator=',',
            Integer columnIndexPos=0, Integer rowIndexPos=null,
            Object rowIndexColumn=null
    ) {
        List<String> lines = Files.readAllLines(path)

        LinkedHashSet<Object> columnIndex = columnIndexPos != null ? lines.remove(columnIndexPos).split(separator) : null

        if (rowIndexPos != null) {
            rowIndexColumn = columnIndex[rowIndexPos]
        }
        if (rowIndexColumn != null) {
            rowIndexPos = columnIndex.findIndexOf { it == rowIndexColumn } as Integer
            columnIndex.remove(rowIndexColumn)
        }
        LinkedHashSet<Object> rowIndex = []
        List<List<Object>> data = []
        boolean  escaped = false
        int start = 0
        int end = 0
        lines.each {line ->

            // get elements of row from string
            List<Object> row  = []
            line.eachWithIndex{ character, i ->
                end = i
                if (character == separator && !escaped) {
                    row.add( inferTypeOfString(line.substring(start, end)) )
                    start = i+1
                }
                else if (character == '"') {
                    escaped = !escaped
                }
            }
            row.add( inferTypeOfString(line.substring(start, end+1)) )
            start = 0

            // extract row Index
            if (rowIndexPos != null) {
                Object rowIdx = row[rowIndexPos]
                row.remove(rowIdx)
                rowIndex.add(rowIdx)
            }

            // add row to data
            data.add( row )
        }

        return new DataMatrix(data, columnIndex, rowIndex)
    }

    /**
    * Converts the DataMatrix into a readable string representation.
    *
    * @return A string representation of the DataMatrix.
    */
    String toString() {
        List<Object> sortedColumnsIndex = getOrderedColumnKeys()
        String stringRepresentation = "\t\t${sortedColumnsIndex.toString()}"
        data.eachWithIndex {row, i  ->
            stringRepresentation += "\n${this.rowIndex.getKey(i)}\t${row.toString()}"
        }
        return stringRepresentation
    }
}
