package nextflow.co2footprint.FileCreation

import nextflow.co2footprint.Config.BaseFileConfig

import java.nio.file.Files
import java.nio.file.Path

/**
 * Base class for CO₂ footprint file writers.
 *
 * Handles file path and overwrite logic for output files (trace, summary, report).
 */
class BaseFileCreator {

    // Whether to overwrite existing files with the same path
    protected boolean overwrite

    // The path where the file is created
    protected Path path

    // The writer
    protected BufferedWriter writer

    // The actual file object (writer)
    protected PrintWriter file

    // Whether this file was created
    boolean created

    /**
     * Constructor for generic file class.
     *
     * @param config A {@link BaseFileConfig}, defining the file
     */
    BaseFileCreator(BaseFileConfig config) {
        this.path = config.file.complete()
        this.overwrite = config.overwrite
    }

    /**
     * Shared creation functionality.
     */
    void create() {
        // Create the parent directory of a file
        final Path parent = path.normalize().getParent()
        if (parent) { Files.createDirectories(parent) }

        this.created = true
    }

    /**
     * Close the file writers.
     */
    void close() {
        if (!created) { return }

        file?.close()
        writer?.close()
    }
}
