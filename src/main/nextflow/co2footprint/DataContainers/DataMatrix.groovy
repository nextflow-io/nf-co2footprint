package nextflow.co2footprint.DataContainers

import groovy.util.logging.Slf4j

import java.nio.file.Files
import java.nio.file.Path

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

        // Extract column index from the specified line (keep as LinkedHashSet to preserve your behavior)
        LinkedHashSet<Object> columnIndex = columnIndexPos != null ? lines.remove(columnIndexPos).split(separator) : null

        // Handle row index column if specified by position
        if (rowIndexPos != null) {
            rowIndexColumn = columnIndex[rowIndexPos]
        }

        // If rowIndexColumn is specified (either directly or derived), resolve its index and remove from header
        if (rowIndexColumn != null) {
            rowIndexPos = columnIndex.findIndexOf { it == rowIndexColumn } as Integer
            if (rowIndexPos < 0) {
                throw new IllegalArgumentException("Row index column '${rowIndexColumn}' not found in header ${columnIndex}")
            }
            // Remove by value (Set has no removeAt) — keeps your original semantics
            columnIndex.remove(rowIndexColumn)
        }

        // Initialize row index and data (keep as LinkedHashSet for your behavior)
        LinkedHashSet<Object> rowIndex = []
        List<List<Object>> data = []

        int start = 0
        int end = 0

        boolean escaped = false // Track if we are inside a quoted field

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

            // Extract row index if specified
            if (rowIndexPos != null) {
                if (rowIndexPos < 0 || rowIndexPos >= row.size()) {
                    throw new IllegalArgumentException("rowIndexPos ${rowIndexPos} out of bounds for row ${row}")
                }
                Object rowIdx = row[rowIndexPos]
                row.removeAt(rowIndexPos)   // ✅ this is a List, so removeAt is correct
                if (rowIndex.contains(rowIdx)) {
                    log.warn("Duplicate row index detected: ${rowIdx}. Only the first occurrence will be used in the row index.")
                }
                rowIndex.add(rowIdx)
            }

            // Add row to data
            data.add(row)
            // Check if the line was properly closed
            // If we are still escaped, it means the last quote was not closed
            assert !escaped, "Unclosed quote in line: ${line}"
            start = 0
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
     * Searched for the integer index in the 'data' table of a row or column ID.
     *
     * @param rowOrColumnID ID of the row or column
     * @param orientation Orientation of the search, Must be either 'row' or 'column'
     * @param checkOccurrence Whether to throw an error, when the ID is not found
     * @return The integer index, or null if none was found and no error was thrown
     * @throws IllegalArgumentException when no match was found and the occurrence is checked
     */
    Integer getIdx(Object rowOrColumnID, String orientation, boolean checkOccurrence=true) throws IllegalArgumentException {
        BiMap<Object, Integer> index = switch (orientation) {
            case 'row' -> this.rowIndex
            case 'column' -> this.columnIndex
            default -> null
        }

        Integer idx = index?.getValue(rowOrColumnID)

        if (checkOccurrence && idx == null) {
            final String message = "${orientation.capitalize()} ID `${rowOrColumnID}` not found in the ${orientation}-index `${index}`."
            throw new IllegalArgumentException(message)
        }

        return  idx
    }

    /**
     * Get a value from the matrix by row and column.
     * Row and column can be names or integer indices, controlled by rowRawIndex/columnRawIndex.
     */
    Object get(Object rowID, Object columnID, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        Integer rowIdx = rowRawIndex ? rowID as Integer : getIdx(rowID, 'row')
        Integer columnIdx = columnRawIndex ? columnID as Integer : getIdx(columnID, 'column')

        // Return the data at the resolved row and column
        return this.data[rowIdx][columnIdx]
    }

    /**
     * Set a value in the matrix at the specified row and column.
     * Row and column can be names or integer indices, controlled by rowRawIndex/columnRawIndex.
     */
    void set(Object value, Object rowID, Object columnID, boolean rowRawIndex=false, boolean columnRawIndex=false) {
        Integer rowIdx = rowRawIndex ? rowID as Integer : getIdx(rowID, 'row')
        Integer columnIdx = columnRawIndex ? columnID as Integer : getIdx(columnID, 'column')

        data[rowIdx][columnIdx] = value
    }

    /**
     * Put a row into the DataMatrix. If the given rowId is already present, the old row is replaced.
     *
     * @param row List of Objects to be added at the rowId
     * @param rowId ID of the row
     */
    void putRow(List<Object> row, Object rowId) {
        assert row.size() == this.data[0].size()
        Integer rowIdx = getIdx(rowId, 'row', false)

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
