package nextflow.co2footprint.FileCreators

import nextflow.co2footprint.Config.BaseConfig

import java.nio.file.Files
import java.nio.file.Path

/**
 * Base class for CO₂ footprint file writers.
 *
 * Handles file path and overwrite logic for output files (trace, summary, report).
 */
class BaseFile {
    // Whether to produce a file at all
    protected  boolean enabled

    // Whether to overwrite existing files with the same path
    protected boolean overwrite

    // The path where the file is created
    protected Path path

    // The actual file object (writer)
    protected PrintWriter file

    static <T> T initialize(BaseFileConfig fileConfig, Class<T> type=this.class){
        // Break initialization if not enabled
        if(!fileConfig.getEnabled()) { return null }
        else { return type.newInstance(fileConfig)}
    }

    /**
     * Constructor for generic file class.
     *
     * @param path Path to the file, or where it is targeted to be written
     * @param overwrite Whether to overwrite existing files with the same path
     */
    BaseFile(boolean enabled, Path path, boolean overwrite) {
        this.enabled = enabled
        this.path = path
        this.overwrite = overwrite
    }

    BaseFile(BaseFileConfig fileConfig) {
        this(
            fileConfig.getEnabled(),
            fileConfig.getPath(),
            fileConfig.getOverwrite()
        )
    }

    /**
     * Ensures that the directory of the file exists
     */
    void ensureParentDirectory() {
        final Path parent = path.normalize().getParent()
        if (parent) {
            Files.createDirectories(parent)
        }
    }

    void create() { ensureParentDirectory() }
}
