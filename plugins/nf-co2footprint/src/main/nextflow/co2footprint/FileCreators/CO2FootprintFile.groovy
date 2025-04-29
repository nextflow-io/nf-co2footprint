package nextflow.co2footprint.FileCreators

import java.nio.file.Path

class CO2FootprintFile {

    /**
     * Overwrite existing trace file (required in some cases, as rolling filename has been deprecated)
     */
    protected boolean overwrite

    /**
     * The path where the files are created. It is set by the object constructor
     */
    protected Path path

    /**
     * The actual file object
     */
    protected PrintWriter file

    /**
     * Constructor for generic file class
     *
     * @param path Path to the file, or where it is targeted to be written
     * @param overwrite Whether to overwrite existing files with the same path
     */
    CO2FootprintFile(Path path, boolean overwrite) {
        this.path = path
        this.overwrite = overwrite
    }
}
