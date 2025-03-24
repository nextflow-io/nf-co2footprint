package nextflow.co2footprint

import nextflow.co2footprint.utils.DataMatrix

import groovy.util.logging.Slf4j

import java.util.regex.Pattern


@Slf4j
/**
 * Structure for the thermal design power (TPD) values
 *
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */
class TDPDataMatrix extends DataMatrix {

    private final Object tdpID = "tdp (W)"
    private final Object coresID = "cores"
    private final Object threadsID = "threads"
    Object fallbackModel = 'default'
    Integer tdp = null
    Integer cores = null
    Integer threads = null

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
     *
     * @param model CPU model
     * @return DataMatrix with one entry, representing the model
     */
    TDPDataMatrix matchModel(String model, String originalModel=model) {
        // Construct regular expression to address potential differences in exact name matching
        String modelRegex = toASCII(model, '\s?')                          // Convert to ASCII
                .toLowerCase()                                                        // Convert to lower case
                .replaceAll('\\(r\\)|\\(tm\\)|\\(c\\)', '\s?')      // Replace ASCII surrogates
                .replaceAll(' ?processors? ?', '')                  // make 'processor(s)' optional
                .replaceAll('\\s(?!\\?)','\s*')                     // make whitespaces optional

        // Find matches against index
        List matches = this.rowIndex.filterKeys { String str ->
                str = str.toLowerCase()      // Convert to lower case
                    .replaceAll(' ?processors? ?', '')              // make 'processor(s)' optional
                str.matches(modelRegex)
        }


        DataMatrix modelData
        // Match only if exactly one match in index / model names
        if (matches.size() == 1) {
            modelData = select([matches[0]] as LinkedHashSet)
        }
        else if ( model.contains('@') ) {
            // Case info appended with @
            return matchModel(
                    String.join('@', model.split('@').dropRight(1)).trim(),
                    originalModel
            )
        }
        else {
            modelData = select([this.fallbackModel] as LinkedHashSet)
            log.warn(
                    "Could not find CPU model \"${originalModel}\" in given TDP data table. " +
                            "Using ${this.fallbackModel} CPU power draw value (${getTDP(modelData)} W)."
            )
        }

        return new TDPDataMatrix(
                modelData.data, modelData.getOrderedColumnKeys(), modelData.getOrderedRowKeys(),
                this.fallbackModel, this.tdp, this.cores, this.threads
        )
    }

    /**
     * Return TDP value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm DataMatrix with TDP values
     * @param rowID ID of the respective row, defaults to null
     * @param rowIdx Index of the respective row, defaults to 0, neglected when rowID is given
     * @return
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
     * Return TDP value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm DataMatrix with TPD values
     * @param rowID ID of the respective row, defaults to null
     * @param rowIdx Index of the respective row, defaults to 0, neglected when rowID is given
     * @return
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
     * Return TDP value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm DataMatrix with TPD values
     * @param rowID ID of the respective row, defaults to null
     * @param rowIdx Index of the respective row, defaults to 0, neglected when rowID is given
     * @return
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
     * Return TDP value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm DataMatrix with TPD values
     * @param rowID ID of the respective row, defaults to null
     * @param rowIdx Index of the respective row, defaults to 0, neglected when rowID is given
     * @return
     */
    Double getCoreTDP(DataMatrix dm=null, Integer rowIdx=0, Object rowID=null) {
        dm = dm ?: this
        return  getTDP(dm, rowID, rowIdx) / getCores(dm, rowID, rowIdx)
    }

    /**
     * Return TDP value of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm DataMatrix with TPD values
     * @param rowID ID of the respective row, defaults to null
     * @param rowIdx Index of the respective row, defaults to 0, neglected when rowID is given
     * @return
     */
    Double getThreadTDP(DataMatrix dm=null, Integer rowIdx=0, Object rowID=null) {
        dm = dm ?: this
        return  getTDP(dm, rowID, rowIdx) / getThreads(dm, rowID, rowIdx)
    }

    /**
     * Return first name (row index) of the TPDDataMatrix.
     *
     * @param dm DataMatrix with TPD values
     * @return first model name
     */
    String getFirstName(DataMatrix dm=null) {
        dm = dm ?: this
        return dm.rowIndex.getKey(0) as String
    }

}
