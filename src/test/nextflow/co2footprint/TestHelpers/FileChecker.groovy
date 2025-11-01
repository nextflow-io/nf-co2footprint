package nextflow.co2footprint.TestHelpers

import groovy.json.JsonSlurper
import org.opentest4j.AssertionFailedError

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Checksum checker to compare and generate checksums (for files) and more
 */
class FileChecker {

    // Checksums to compare to
    private Map<String, Map<String, ?>> checkInfos

    // Collect errors or throw directly?
    boolean collectErrors = false

    // Error store
    Throwable error = null

    /**
     * Checksum checker from a given Path with a JSON file of checksums
     *
     * @param checksumPath JSON file of checksums
     */
    FileChecker(Path checkInfosPath=null, boolean collectErrors=false) {
        checkInfosPath ?= this.class.getResource('/file_checks.json').getPath() as Path
        this.checkInfos = checkInfosPath ? loadChecksums(checkInfosPath) : null
        this.collectErrors = collectErrors
    }

    /**
     * Adds a new error if it should be collected, otherwise throws it
     *
     * @param newError
     */
    void addError(Throwable newError) {
        if (!collectErrors) { throw newError }
        else if (error) { error.addSuppressed(newError) }
        else { error = newError }
    }

    /**
     * Calculates the MD5 checksum for a file
     *
     * @param file File to be calculated the checksum to
     * @param excludedLines Lines to be excluded in the checksum calculation
     * @return The checksum of the file as a String
     */
    static String calculateMD5(File file, List<Integer> excludedLines=[]) {
        MessageDigest md = MessageDigest.getInstance("MD5")
        int position = 0
        file.eachLine("UTF-8") { line ->
            if (!excludedLines.contains(position)) {
                byte[] bytes = (line + System.lineSeparator()).getBytes("UTF-8")
                md.update(bytes)
            }
            position += 1
        }
        md.digest().encodeHex().toString()
    }

    /**
     * Load a JSON file with checksums.
     *
     * @param jsonPath Path to the JSON file with checksums
     * @return The checksums as a Map with String keys and values
     */
    Map<String, Map<String, ?>> loadChecksums(Path jsonPath) {
        JsonSlurper jsonSlurper = new JsonSlurper()
        this.checkInfos = jsonSlurper.parse(jsonPath) as Map<String, Map<String, ?>>
        return this.checkInfos
    }

    /**
     * Asserts whether the path is a well formed file
     *
     * @param path Path to file
     */
    void checkIsFile(Path path) {
        try {
            assert Files.isRegularFile(path)
        } catch (Exception exception) {
            addError(exception)
        }
    }

    /**
     * Compare the number of lines
     *
     * @param path Path to a file
     * @param numLines Number of lines it should have
     * @return New number of lines
     */
    static Long compareNumLines(Path path, Integer numLines){
        Long newNumLines = path.countLines()
        try {
            assert newNumLines == numLines
        }
        finally {
            return newNumLines
        }
    }

    /**
     * Compare select lines against recorded lines
     *
     * @param path Path to file
     * @param lineRecords Map of paired line positions and line content
     * @return A list of the already checked lines
     */
    List<Integer> compareLines(Path path, Map<Integer, String> lineRecords) {
        List<String> lines  = path.readLines()
        String line
        lineRecords.each { Integer linePos, String lineRecord ->
            // Change from 1 to 0-based
            line = lines[linePos - 1]
            try {
                assert line == lineRecord
            }
            catch (Throwable throwable) {
                addError(throwable)
            }
        }

        return lineRecords.keySet() as List<Integer>
    }

    /**
     * Compare two files line by line
     *
     * @param path Path to the new file
     * @param recordPath Path to the recorded File
     * @param excludedLines Lines that are not compared (useful for excluding timestamps and other non comparable stuff)
     * @return Snapshot path
     */
    Path compareFiles(Path path, Path recordPath, List<Integer> excludedLines=[]) {
        // Copy file to make comparison easier
        Path parent = recordPath.getParent().normalize()
        Path snapPath = parent.resolve("new_${recordPath.getFileName()}")

        int linePosition = 0
        path.withReader { Reader readerNew ->
            recordPath.withReader { Reader readerRecord ->
                String lineNew, lineRecord
                while ((lineNew = readerNew.readLine()) != null & (lineRecord = readerRecord.readLine()) != null) {
                    if (!excludedLines.contains(linePosition)) {
                        if (lineNew.size() < 10000 & lineRecord.size() < 10000){
                            try {
                                assert lineNew == lineRecord, "Mismatch in line ${linePosition}"
                            }
                            catch (Throwable error) {
                                addError(error)
                            }
                        } else if (lineNew != lineRecord) {
                            addError(
                                    new AssertionFailedError(
                                            "Mismatching new line: ${lineNew}\n" +
                                                    "Mismatch in line ${linePosition}. Output too long, omitting recorded line."
                                    )
                            )
                        }
                    }
                    linePosition += 1
                }

                // Check for extra lines:
                if (readerNew.readLine() != null) {
                    addError( new AssertionFailedError("New file has extra lines") )
                }
                // Check for extra lines:
                if (readerRecord.readLine() != null) {
                    addError( new AssertionFailedError("Recorded file has extra lines at the end.") )
                }
            }
        }

        return snapPath
    }

    /**
     * Compare the recorded checksum to the new checksum of a file.
     *
     * @param path Path to the file for the checksum calculation
     * @param recordedChecksum  The expected checksum to verify against. If null, the method will
     *                          attempt to retrieve it from the class checksum map.
     * @param excludedLines Lines to be excluded in the checksum calculation (1-based)
     * @param recordedPath Path with the complete file to compare to when the checksums don't match
     * @return New checksum and snapshot path
     */
    Map<String, ?> compareChecksums(
            Path path,
            String recordedChecksum,
            List<Integer> excludedLines=[],
            Path recordPath=null
    ) {
        Path snapPath = null
        // Change from 1 based to 0-based numbers
        excludedLines = excludedLines.collect {Integer line -> line - 1}
        String newChecksum = calculateMD5(path.toFile(), excludedLines)

        try {
            assert recordedChecksum == newChecksum
        }
        catch (AssertionError assertionError) {
            if(recordPath) {
                snapPath = compareFiles(path, recordPath, excludedLines)
                addError(
                        new AssertionFailedError(
                                "Recorded checksum '${recordedChecksum}' and new checksum '${newChecksum}' did not match, " +
                                        "but the checked lines (all except ${excludedLines}) in ${recordPath} and '${path}' reveal no difference."
                        )
                )
            } else {
                addError(assertionError)
            }
        }
        return [checksum: newChecksum, snapPath: snapPath]
    }

    void runChecks(Path path, Map<Integer, String> explicitLines=[:], Path recordPath=null){
        // Set errors to collection
        this.collectErrors = true

        // Check file property
        checkIsFile(path)

        // Get Infos to check for
        recordPath ?= this.class.getResource("/${path.getFileName()}").getPath() as Path
        Map<String, ?> checkInfos = this.checkInfos[recordPath.getFileName() as String]

        // Prepare new file check infos
        Map<String, ?> newCheckInfos = [:]
        Path snapPath = null

        // Check explicitly given or excluded lines
        Set<Integer> excludedLines = compareLines(path, explicitLines)
        Set<Integer> additionalExclusions = checkInfos.remove('excluded_lines') as Set<Integer> ?: []
        excludedLines.addAll(additionalExclusions)

        // Perform all checks
        checkInfos.each { String checkType, def value ->
            switch (checkType) {
                case 'checksum' -> {
                    Map record = compareChecksums(path, value as String, excludedLines as List, recordPath)
                    newCheckInfos.put(checkType, record.get("checksum"))
                    snapPath = record.get("snapPath") as Path
                }
                case 'num_lines' -> {
                    value = value as Integer
                    Long newNumLines = compareNumLines(path, value) as Long
                    newCheckInfos.put(checkType, newNumLines)
                    if(additionalExclusions) {
                        Long diffLines = newNumLines - value
                        newCheckInfos.put('excluded_lines', additionalExclusions.collect { Integer line -> line + diffLines })
                    }
                }
            }
        }

        // Throw errors if existent
        if (error) {
            // Reset error collection
            this.collectErrors = false

            // Copy snapshot
            Files.copy(path, snapPath, StandardCopyOption.REPLACE_EXISTING)

            // Print info to adopt the changes
            String message =
                    "🔎 The actual error message can be found below under 'Suppressed:'.\n\n" +
                            "ℹ️ If you want to adopt the changes, you may replace the file content in `nextflow.co2footprint/testResources/${recordPath.getFileName()}`\n" +
                            "with the new file content in: `${snapPath}`.\n" +
                            "💡 Suggested new fileCheck configuration (apply this in `nextflow.co2footprint/testResources/file_checks.json`):\n" +
                            "${newCheckInfos}" +
                            "\n⚠️ Pay attention to the excluded_lines, as they may differ from the suggested ones depending on your changes.\n"

            Exception checkFailedException = new Exception(message)
            checkFailedException.addSuppressed(error)

            throw checkFailedException
        }
    }
}
