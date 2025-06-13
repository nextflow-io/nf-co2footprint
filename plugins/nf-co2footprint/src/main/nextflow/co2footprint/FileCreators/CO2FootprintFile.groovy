package nextflow.co2footprint.FileCreators

import java.nio.file.Path

/**
 * Base class for CO₂ footprint file writers.
 *
 * Handles file path and overwrite logic for output files (trace, summary, report).
 */
class CO2FootprintFile {

    // Whether to overwrite existing files with the same path
    protected boolean overwrite

    // The path where the file is created
    protected Path path

    // The actual file object (writer)
    protected PrintWriter file

    /**
     * Constructor for generic file class.
     *
     * @param path Path to the file, or where it is targeted to be written
     * @param overwrite Whether to overwrite existing files with the same path
     */
    CO2FootprintFile(Path path, boolean overwrite) {
        this.path = path
        this.overwrite = overwrite
    }
}
