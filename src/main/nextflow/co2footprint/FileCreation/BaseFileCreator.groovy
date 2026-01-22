package nextflow.co2footprint.FileCreation

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
     * @param path Path to the file, or where it is targeted to be written
     * @param overwrite Whether to overwrite existing files with the same path
     */
    BaseFileCreator(Path path, boolean overwrite) {
        this.path = path
        this.overwrite = overwrite
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
