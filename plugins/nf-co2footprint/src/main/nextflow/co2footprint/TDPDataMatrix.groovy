package nextflow.co2footprint

import nextflow.co2footprint.utils.DataMatrix
import nextflow.co2footprint.utils.Markers

import groovy.util.logging.Slf4j

import java.nio.file.Path
import java.util.regex.Matcher

/**
 * Structure for the thermal design power (TDP) values.
 * 
 * Provides methods for loading, matching, and retrieving TDP, core, and thread values
 * for CPU models, including fallback and custom data support.
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
@Slf4j
class TDPDataMatrix extends DataMatrix {

    // Column IDs for TDP, cores, and threads
    private final Object tdpID = 'tdp (W)'
    private final Object coresID = 'cores'
    private final Object threadsID = 'threads'

    // Fallback/default model name
    Object fallbackModel = 'default'

    // Optional override values
    Integer tdp = null
    Integer cores = null
    Integer threads = null

    
    /**
     * Constructor for TDPDataMatrix.
     *
     * @param data         Matrix data
     * @param columnIndex  Column index set
     * @param rowIndex     Row index set
     * @param fallbackModel Fallback model name
     * @param tdp          TDP value (optional)
     * @param cores        Number of cores (optional)
     * @param threads      Number of threads (optional)
     */
    TDPDataMatrix(
            List<List> data = [], LinkedHashSet<String> columnIndex = [], LinkedHashSet<String> rowIndex = [],
            Object fallbackModel='default', Integer tdp=null, Integer cores=null, Integer threads=null
    ) {
        // Initialize DataMatrix without non-ASCII characters in indices
        super(
                data,
                columnIndex.collect {toASCII(it)} as LinkedHashSet,
                rowIndex.collect {toASCII(it)} as LinkedHashSet
        )

        // Initialize own values
        this.fallbackModel = fallbackModel
        this.tdp = tdp
        this.cores = cores
        this.threads = threads
    }

    /**
    * Create a TDPDataMatrix from a CSV file.
    *
    * @param path Path to the CSV file
    * @param separator Separator used in the CSV file (default is ',')
    * @param columnIndexPos Position of the column index (default is 0)
    * @param rowIndexPos Position of the row index (default is null)
    * @param rowIndexColumn Name of the column used for the row index (default is 'name')
    * @return A TDPDataMatrix object
    */
    static TDPDataMatrix fromCsv(
            Path path, String separator = ',', Integer columnIndexPos = 0, Integer rowIndexPos = null,
            Object rowIndexColumn = 'name'
    ) {
        DataMatrix dm = DataMatrix.fromCsv(path, separator, columnIndexPos, rowIndexPos, rowIndexColumn)
        TDPDataMatrix tdpMatrix = new TDPDataMatrix(
                dm.getData(), dm.getOrderedColumnKeys(), dm.getOrderedRowKeys(),
                'default', null, null, null
        )
        return tdpMatrix
    }


    /**
     * Remove non-ASCII symbols (®, ™,...)  from String.
     *
     * @param str Input string
     * @return Input string with non ASCII characters removed.
     */
    static String toASCII(String str, String replacement='') {
        return  str.replaceAll('[^\\p{ASCII}]', replacement)
    }

    /**
     * Match a CPU model to the given TDP matrix.
     * Tries to find a matching row for the model, with fallback to default if not found.
     *
     * @param model             CPU model string
     * @param fallbackToDefault Whether to fallback to default if not found (default: true)
     * @param originalModel     Original model string (for logging)
     * @return                  TDPDataMatrix with one entry, representing the model
     */
    TDPDataMatrix matchModel(String model, Boolean fallbackToDefault=true, String originalModel=model) {
        model = model ?: ''

        // Construct regular expression to address potential differences in exact name matching
        String modelRegex = toASCII(model, Matcher.quoteReplacement('\\s?'))                          // Convert to ASCII
                .toLowerCase()                                                        // Convert to lower case
                .replaceAll('\\(r\\)|\\(tm\\)|\\(c\\)', Matcher.quoteReplacement('\\s?'))      // Replace ASCII surrogates
                .replaceAll(' ?(processor|cpu)s? ?', '')                  // make 'processor/cpu(s)' optional
                .replaceAll('\\s(?!\\?)', Matcher.quoteReplacement('\\s*'))                     // make whitespaces optional

        // Find matches against index
        final List matches = this.rowIndex.filterKeys { String str ->
                str = str.toLowerCase()                                               // Convert to lower case
                    .replaceAll(' ?(processor|cpu)s? ?', '')        // make 'processor(s)/cpu' optional
                str.matches(modelRegex)
        }

        DataMatrix modelData

        // Match only if exactly one match in index / model names
        if (matches.size() == 1) {
            modelData = select([matches[0]] as LinkedHashSet)
        }
        else if ( model.contains('@') ) {
            // Case info appended with @, try again with less specific model string
            return matchModel(
                    String.join('@', model.split('@').dropRight(1)).trim(),
                    fallbackToDefault,
                    originalModel
            )
        }
        else if (fallbackToDefault) {
            modelData = select([this.fallbackModel] as LinkedHashSet)
            log.warn(
                    Markers.unique,
                    "Could not find CPU model \"${originalModel}\" in given TDP data table. " +
                    "Using ${this.fallbackModel} CPU power draw value (${getTDP(modelData)} W)."
            )
        }
        else {
            log.warn("No exact match found for '${model}'.")
            return null
        }

        return new TDPDataMatrix(
                modelData.data, modelData.getOrderedColumnKeys(), modelData.getOrderedRowKeys(),
                this.fallbackModel, this.tdp, this.cores, this.threads
        )
    }

    /**
     * Return TDP value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm     DataMatrix with TDP values (default: this)
     * @param rowID  ID of the respective row (default: null)
     * @param rowIdx Index of the respective row (default: 0, ignored if rowID is given)
     * @return       TDP value (W)
     */
    Double getTDP(DataMatrix dm=null, Object rowID=null, Integer rowIdx=0) {
        dm = dm ?: this
        if (this.tdp) {
            return this.tdp
        } else if (rowID) {
            return dm.get(rowID, this.tdpID) as Integer
        } else {
            return dm.get(rowIdx, this.tdpID, true) as Integer
        }
    }

    /**
     * Return number of cores of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm     DataMatrix with core values (default: this)
     * @param rowID  ID of the respective row (default: null)
     * @param rowIdx Index of the respective row (default: 0, ignored if rowID is given)
     * @return       Number of cores
     */
    Integer getCores(DataMatrix dm=null, Object rowID=null, Integer rowIdx=0) {
        dm = dm ?: this
        if (this.cores) {
            return this.cores
        } else if (rowID) {
            return dm.get(rowID, this.coresID) as Integer
        }
        else {
            return dm.get(rowIdx, this.coresID, true) as Integer
        }
    }

    /**
     * Return number of threads of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm     DataMatrix with thread values (default: this)
     * @param rowID  ID of the respective row (default: null)
     * @param rowIdx Index of the respective row (default: 0, ignored if rowID is given)
     * @return       Number of threads
     */
    Integer getThreads(DataMatrix dm=null, Object rowID=null, Integer rowIdx=0) {
        dm = dm ?: this
        if (this.threads) {
            return this.threads
        } else if (rowID) {
            return dm.get(rowID, this.threadsID) as Integer
        }
        else {
            return dm.get(rowIdx, this.threadsID, true) as Integer
        }
    }

    /**
     * Return per-core TDP value of DataMatrix row.
     *
     * @param dm     DataMatrix with TDP values (default: this)
     * @param rowIdx Index of the respective row (default: 0)
     * @param rowID  ID of the respective row (default: null)
     * @return       TDP per core (W)
     */
    Double getCoreTDP(DataMatrix dm=null, Integer rowIdx=0, Object rowID=null) {
        dm = dm ?: this
        return getTDP(dm, rowID, rowIdx) / getCores(dm, rowID, rowIdx)
    }

    /**
     * Return per-thread TDP value of DataMatrix row.
     *
     * @param dm     DataMatrix with TDP values (default: this)
     * @param rowIdx Index of the respective row (default: 0)
     * @param rowID  ID of the respective row (default: null)
     * @return       TDP per thread (W)
     */
    Double getThreadTDP(DataMatrix dm=null, Integer rowIdx=0, Object rowID=null) {
        dm = dm ?: this
        return getTDP(dm, rowID, rowIdx) / getThreads(dm, rowID, rowIdx)
    }

/**
     * Return first name (row index) of the TDPDataMatrix.
     *
     * @param dm DataMatrix with TDP values (default: this)
     * @return   First model name
     */
    String getFirstName(DataMatrix dm=null) {
        dm = dm ?: this
        return dm.rowIndex.getKey(0) as String
    }

    /**
     * Replaces this instance with a new TDPDataMatrix.
     *
     * @param newTDPDataMatrix New TDPDataMatrix to copy data from
     */
    void update(TDPDataMatrix newTDPDataMatrix) {
        this.fallbackModel = newTDPDataMatrix.fallbackModel
        this.tdp = newTDPDataMatrix.tdp
        this.cores = newTDPDataMatrix.cores
        this.threads = newTDPDataMatrix.threads

        this.data = newTDPDataMatrix.data
        this.rowIndex = newTDPDataMatrix.rowIndex
        this.columnIndex = newTDPDataMatrix.columnIndex
    }

    /**
     * Compare two TDPDataMatrix objects and log if values are overwritten.
     *
     * @param oldData Old TDPDataMatrix
     * @param newData New TDPDataMatrix
     */
    static compareToOldData(TDPDataMatrix oldData, TDPDataMatrix newData) {
        // Compare entries to warn about changing
        if (oldData) {
            TDPDataMatrix oldEntry
            TDPDataMatrix newEntry
            for (String model : oldData.getRowIndex().keySet()) {
                oldEntry = oldData.matchModel(model, false)
                newEntry = newData.matchModel(model, false)
                if (oldEntry && oldEntry.getData() != newEntry.getData()) {
                    log.info(
                            "Already existing TDP value (${oldEntry.getTDP()} W) of '${model}' " +
                            "is overwritten with custom value: ${newEntry.getTDP()} W"
                    )
                }
            }
        }
    }


}

