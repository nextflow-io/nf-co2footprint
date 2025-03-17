package nextflow.co2footprint.utils

import java.nio.file.Path
import java.nio.file.Files

/**
 * Bidirectional Map with maintained K-V pairs in both directions
 * @param <K>
 * @param <V>
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

    // Select method
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
    private BiMap<Object, Integer> columnIndex = [:] as BiMap
    private BiMap<Object, Integer> rowIndex = [:] as BiMap

    DataMatrix(List<List> data = [], LinkedHashSet<Object> columnIndex = [], LinkedHashSet<Object> rowIndex = []) {
        this.data = data

        // Default the Indices with a Integer Range
        if (!rowIndex && data.size() > 0) {
            rowIndex = new IntRange(0, data.size())
        }
        if (!columnIndex && data.size() > 0 && data[0].size() > 0) {
            columnIndex =  new IntRange(0, data[0].size())
        }

        // Add index into Map
        rowIndex.eachWithIndex { rowIdx, i -> this.rowIndex.put(rowIdx, i) }
        columnIndex.eachWithIndex { columnIdx, i -> this.columnIndex.put(columnIdx, i) }

        // Check integrity
        assertIntegrity()
    }

    // Integrity tests
    void assertRowLengthEqual(){
       data.eachWithIndex { col, i ->
           assert this.data[i].size() == this.data[0].size()
       }
    }

    private void assertRowIndexLengthMatches(){
        assert this.data.size() == this.rowIndex.size()
    }

    void assertColumnIndexLengthMatches(){
        assert this.data.size() == 0 && this.rowIndex.size() == 0 || this.data[0].size() == this.columnIndex.size()
    }

    void assertIntegrity(){
        assertRowLengthEqual()

        assertRowIndexLengthMatches()
        assertColumnIndexLengthMatches()
    }

    @Override
    boolean equals(Object other) {
        if ( other == null ) { return false }
        if ( !(other instanceof DataMatrix) ) { return false }
        DataMatrix dm = (DataMatrix) other
        if (this.data == dm.data && this.rowIndex == dm.rowIndex && this.columnIndex == dm.columnIndex) { return true }
        return false
    }

    /**
     * Collect indices via keys from a BiMap
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    private static List<Integer> collectIndices(LinkedHashSet<Object> keys, BiMap<Object, Integer> bimap) {
        return keys.collect( { key -> bimap.getValue(key) } )
    }

    /**
     * Select rows of the DataMatrix
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    private DataMatrix selectRows(LinkedHashSet<Object> rows){
        List<Integer> iList = collectIndices(rows, this.rowIndex)
        List<List> data = this.data[iList]

        return new DataMatrix(data, this.columnIndex.keySet() as LinkedHashSet, rows)
    }

    /**
     * Select columns of the DataMatrix.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    private DataMatrix selectColumns(LinkedHashSet<Object> columns){
        // Collect indices
        List<Integer> iList = collectIndices(columns, this.columnIndex)
        List<List<Object>> data = this.data.collect { row -> row[iList] }

        return new DataMatrix(data, columns, this.rowIndex.keySet() as LinkedHashSet)
    }

    /**
     * Select a part of the DataMatrix. If no value is given for an entry, everything is selected.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    DataMatrix select(
            LinkedHashSet<Object> rows=this.rowIndex.keySet(),
            LinkedHashSet<Object> columns=this.columnIndex.keySet()
    ){
        return this.selectRows(rows).selectColumns(columns)
    }

    /**
     * Get data entries as Lists.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    List<List> getData() {
        return this.data
    }

    /**
     * Get data entries as Lists.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    BiMap<Object, Integer> getRowIndex() {
        return this.rowIndex
    }

    /**
     * Get data entries as Lists.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    BiMap<Object, Integer> getColumnIndex() {
        return this.columnIndex
    }

    /**
     * Get data entries by specifying row and column that you want to access, as well as whether they are Integer
     * indices.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    Object get(Object row, Object column, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        row = rowRawIndex ? row as Integer : this.rowIndex.getValue(row)
        column = columnRawIndex ? column as Integer : this.columnIndex.getValue(column)

        return this.data[row][column]
    }

    /**
     * Set an entry to the specified value.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    void set(Object value, Object row, Object column, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        row = rowRawIndex ? row as Integer : this.rowIndex.getValue(row)
        column = columnRawIndex ? column as Integer : this.columnIndex.getValue(column)

        data[row][column] = value
    }

    /**
     * Save data with simple CSV format.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
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
     * Infer simple numeric data types from String.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    private static def inferTypeOfString(String str) {
        try { return Integer.parseInt(str) } catch(NumberFormatException e){ }
        try { return Double.parseDouble(str) } catch(NumberFormatException e){ }
        return str
    }

    /**
     * Load data from simple CSV format.
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    static DataMatrix loadCsv(Path path, String separator=',', Integer columnIndexPos=0, Integer rowIndexPos=null) {
        List<String> lines = Files.readAllLines(path)

        LinkedHashSet<Object> columnIndex = columnIndexPos != null ? lines.remove(columnIndexPos).split(separator) : null
        if (rowIndexPos != null) {
            columnIndex.remove(columnIndex[rowIndexPos])
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
     * Convert the class into a readable / printable string*
     *
     * @author Josua Carl <josua.carl@uni-tuebingen.de>
     */
    String toString() {
        List<Object> sortedColumnsIndex = this.columnIndex.valueSet().sort().collect {i -> this.columnIndex.getKey(i)}
        String stringRepresentation = "\t\t${sortedColumnsIndex.toString()}"
        data.eachWithIndex {row, i  ->
            stringRepresentation += "\n${this.rowIndex.getKey(i)}\t${row.toString()}"
        }
        return stringRepresentation
    }
}

