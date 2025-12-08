package nextflow.co2footprint.DataContainers

import groovy.util.logging.Slf4j

import java.nio.file.Path

@Slf4j
class AWSRegionsDataMatrix extends DataMatrix{
    String zoneId = 'Zone id'
    String region = null

    /**
     * Constructor for TDPDataMatrix.
     *
     * @param data         Matrix data
     * @param columnIndex  Column index set
     * @param rowIndex     Row index set
     */
    AWSRegionsDataMatrix(
            List<List> data = [], LinkedHashSet<String> columnIndex = [], LinkedHashSet<String> rowIndex = []
    ) {
        super(data, columnIndex, rowIndex)
        checkRequiredColumns(['Zone id'])
    }

    /**
     * Create a MachineTypeDataMatrix from a CSV file.
     *
     * @param path Path to the CSV file
     * @param separator Separator used in the CSV file (default is ',')
     * @param columnIndexPos Position of the column index (default is 0)
     * @param rowIndexPos Position of the row index (default is null)
     * @param rowIndexColumn Name of the column used for the row index (default is 'Region code')
     * @return A TDPDataMatrix object
     */
    static AWSRegionsDataMatrix fromCsv(
            Path path,
            String separator = ',', Integer columnIndexPos = 0,
            Integer rowIndexPos = null, Object rowIndexColumn = 'Region code'
    ) {
        DataMatrix dataMatrix = DataMatrix.fromCsv(path, separator, columnIndexPos, rowIndexPos, rowIndexColumn)
        return new AWSRegionsDataMatrix(dataMatrix.data, dataMatrix.getOrderedColumnKeys(), dataMatrix.getOrderedRowKeys())
    }

    /**
     * Detects the AWS region using environment variables, EC2 metadata, or the AWS CLI,
     * and sets the corresponding geographic Zone ID in the configuration.
     *
     * The mapping between AWS regions and Zone IDs is read from
     * `aws_region_zoneID_mapping.csv`, which must have:
     * - Row index: 'Region code'
     * - Column: 'Zone id'
     */
    String fetchRegion() {
        // 1️⃣ Try environment variables first (works for Batch, Fargate, CloudShell)
        region = System.getenv('AWS_REGION') ?: System.getenv('AWS_DEFAULT_REGION')

        // 2️⃣ Try EC2 metadata service (works on EC2 instances)
        if (!region) {
            log.debug('Region could not be fetched via AWS_REGION or AWS_DEFAULT_REGION evironment variables.')
            try {
                URL url = new URI("http://169.254.169.254/latest/meta-data/placement/availability-zone").toURL()
                HttpURLConnection conn = (HttpURLConnection) url.openConnection()
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                String availabilityZone = conn.inputStream.text.trim()
                if (availabilityZone && availabilityZone.length() > 1) {
                    region = availabilityZone.substring(0, availabilityZone.length() - 1)
                }
            } catch (Exception ignored) { }
        }

        // 3️⃣ Optional fallback: AWS CLI
        if (!region) {
            log.debug('Region could not be fetched via localhost URL.')
            try {
                Process proc = ["aws", "configure", "get", "region"].execute()
                region = proc.text
            } catch (Exception ignored) { }
        }

        if (region) {
            region?.trim()
        } else {
            log.debug('Region could not be fetched via `aws configure get region` command.')
            log.debug('No region found.')
        }
        return region
    }

    /**
     * Finds the corresponding geographic Zone ID.
     *
     * The mapping between AWS regions and Zone IDs is read from
     * `aws_region_zoneID_mapping.csv`, which must have:
     * - Row index: 'Region code'
     * - Column: 'Zone id'
     */
    AWSRegionsDataMatrix matchRegion(String region=this.region) {
        if (rowIndex.containsKey(region)) {
            DataMatrix regionData = select([region] as LinkedHashSet)
            return new AWSRegionsDataMatrix(regionData.data, regionData.getOrderedColumnKeys(), regionData.getOrderedRowKeys())
        } else {
            log.warn("AWS region '${region}' could not be matched to Zone ID.")
        }
        return null
    }

    /**
     * Return Zone iD of DataMatrix row. If none is given, the first position is assumed.
     *
     * @param dm     DataMatrix with TDP values (default: this)
     * @param rowID  ID of the respective row (default: null)
     * @param rowIdx Index of the respective row (default: 0, ignored if rowID is given)
     * @return       TDP value (W)
     */
        BigDecimal getZoneId(DataMatrix dm=this, Object rowID=null, Integer rowIdx=0) {
            if (rowID) {
                return dm.get(rowID, zoneId) as BigDecimal
            } else {
                return dm.get(rowIdx, zoneId, true) as BigDecimal
            }
        }
}
