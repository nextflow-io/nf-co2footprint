/*
 * Copyright 2020-2022, Seqera Labs
 * Copyright 2013-2019, Centre for Genomic Regulation (CRG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    // Directory with files to check
    private Path checksDirectory

    // Checksums to compare to
    private Path checksInfoPath
    private Map<String, Map<String, ?>> checksInfo

    // Collect errors or throw directly?
    boolean collectErrors = false

    // Error store
    List<Throwable> errors = []

    // Folder for files with failed checks
    private Path failPath

    /**
     * Checksum checker from a given path, relative to `testResources`.
     * Optionally with a JSON file with check infos, such as checksums.
     *
     * @param checksDirectory Path relative to `testResources`
     * @param collectErrors Whether or not to collect errors or raise them directly
     */
    FileChecker(String checksDirectory='.', boolean collectErrors=false) {
        this.checksDirectory = Path.of(this.class.getResource(checksDirectory).toURI())

        this.checksInfoPath = this.checksDirectory.resolve('file_checks.json')
        this.checksInfo = checksInfoPath.isFile() ? loadChecksInfo(checksInfoPath) : null

        this.collectErrors = collectErrors

        failPath = this.checksDirectory.resolve('failed')
        failPath.createDirIfNotExists()
    }

    /**
     * Adds a new error if it should be collected, otherwise throws it
     *
     * @param error
     */
    void addError(Throwable error) {
        if (!collectErrors) {
            throw error
        }
        else {
            errors.add(error)
        }
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
    static Map<String, Map<String, ?>> loadChecksInfo(Path jsonPath) {
        JsonSlurper jsonSlurper = new JsonSlurper()
        return jsonSlurper.parse(jsonPath) as Map<String, Map<String, ?>>
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
    Long compareNumLines(Path path, Integer numLines){
        Long newNumLines = path.countLines()
        try {
            assert newNumLines == numLines
        }
        catch (AssertionError e) {
            addError(e)
        }
        return newNumLines
    }

    /**
     * Compare select lines against recorded lines
     *
     * @param path Path to file
     * @param lineRecords Map of paired line positions and line content
     * @return A list of the already checked lines
     */
    List<Integer> compareLines(Path path, def lineRecords) {
        List<String> lines  = path.readLines()
        String line
        List<Integer> visitedPositions = []
        if (lineRecords instanceof Map<Integer, String> ) {
            lineRecords.each { Integer linePos, String lineRecord ->
                visitedPositions.add(linePos)
                // Change from 1 to 0-based
                line = lines[linePos - 1]
                try {
                    assert line == lineRecord
                }
                catch (Throwable throwable) {
                    addError(throwable)
                }
            }
        }
        else if (lineRecords instanceof List<String>){
            lineRecords.eachWithIndex{ String lineRecord, int i ->
                visitedPositions.add(i + 1)
                try {
                    assert lines[i] == lineRecord
                }
                catch (Throwable throwable) {
                    addError(throwable)
                }
            }
        }
        return visitedPositions
    }

    /**
     * Compare two files line by line
     *
     * @param path Path to the new file
     * @param recordPath Path to the recorded File
     * @param excludedLines Lines that are not compared (useful for excluding timestamps and other non comparable stuff)
     */
    boolean compareFiles(Path path, Path recordPath, List<Integer> excludedLines=[]) {
        boolean errorFound = false

        int linePosition = 0
        path.withReader { Reader readerNew ->
            recordPath.withReader { Reader readerRecord ->
                String lineNew, lineRecord
                while ((lineNew = readerNew.readLine()) != null & (lineRecord = readerRecord.readLine()) != null) {
                    if (!excludedLines.contains(linePosition)) {
                        if (lineNew.size() < 10000 & lineRecord.size() < 10000){
                            try {
                                assert lineNew == lineRecord, "Mismatch in line ${linePosition + 1}"
                            }
                            catch (Throwable error) {
                                errorFound = true
                                addError(error)
                            }
                        } else if (lineNew != lineRecord) {
                            errorFound = true
                            addError(
                                    new AssertionFailedError(
                                    "Mismatching new line: ${lineNew}\n" +
                                    "Mismatch in line ${linePosition + 1}. Output too long, omitting recorded line."
                                    )
                            )
                        }
                    }
                    linePosition += 1
                }

                // Check for extra lines:
                if (readerNew.readLine() != null) {
                    errorFound = true
                    addError( new AssertionFailedError("New file has extra lines") )
                }
                // Check for extra lines:
                if (readerRecord.readLine() != null) {
                    errorFound = true
                    addError( new AssertionFailedError("Recorded file has extra lines at the end.") )
                }
            }
        }
        return errorFound
    }

    /**
     * Compare the recorded checksum to the new checksum of a file.
     *
     * @param path Path to the file for the checksum calculation
     * @param recordedChecksum  The expected checksum to verify against. If null, the method will
     *                          attempt to retrieve it from the class checksum map.
     * @param excludedLines Lines to be excluded in the checksum calculation (1-based)
     * @param recordedPath Path with the complete file to compare to when the checksums don't match
     * @return New checksum
     */
    String compareChecksums(Path path, String recordedChecksum, List<Integer> excludedLines=[], Path recordPath=null){
        // Change from 1 based to 0-based numbers
        excludedLines = excludedLines.collect {Integer line -> line - 1}
        String newChecksum = calculateMD5(path.toFile(), excludedLines)

        try {
            assert recordedChecksum == newChecksum
        }
        catch (AssertionError assertionError) {
            if(recordPath) {
                boolean errorFound = compareFiles(path, recordPath, excludedLines)
                String message = "Recorded checksum '${recordedChecksum}' and new checksum '${newChecksum}' did not match."
                if (!errorFound) {
                    message += ' ℹ️ The line-by-line comparison showed no difference. Checksum may be outdated.'
                }
                addError(new AssertionFailedError(message))
            } else {
                addError(assertionError)
            }
        }
        return newChecksum
    }

    void runChecks(Path path, Map<Integer, String> explicitLines=[:], Path recordPath=null){
        // Set errors to collection
        this.collectErrors = true

        // Check file property
        checkIsFile(path)

        // Get Infos to check for
        recordPath ?= checksDirectory.resolve(path.getFileName())
        Map<String, Object> checksInfo = this.checksInfo.get(recordPath.getFileName() as String, [:])

        // Prepare new file check infos
        Map<String, Object> newCheckInfos = [:]

        // Check explicitly given or excluded lines
        Set<Integer> excludedLines = compareLines(path, explicitLines)
        Set<Integer> additionalExclusions = checksInfo.remove('excluded_lines') as Set<Integer> ?: []
        excludedLines.addAll(additionalExclusions)

        // Perform all checks
        checksInfo.each { String checkType, Object value ->
            switch (checkType) {
                case 'checksum' -> {
                    String newChecksum = compareChecksums(path, value as String, excludedLines as List, recordPath)
                    newCheckInfos.put(checkType, newChecksum)
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

        // Reset error collection
        this.collectErrors = false

        // Throw errors if existent
        if (errors) {
            Path newPath = failPath.resolve(path.fileName)
            // Copy snapshot
            Files.copy(path, newPath, StandardCopyOption.REPLACE_EXISTING)

            errors.eachWithIndex { Throwable error, Integer i->
                System.err.println("----------------- File Checker Error ${i}:")
                error.printStackTrace()
                System.err.println()
            }

            // Print info to adopt the changes
            String message =
                "❌ File checks for '${path}' failed,\n\n" +
                "🔎 The actual error messages can be found above as a list.\n" +
                "ℹ️ You may want to have a look at the difference between the new and recorded file:\n" +
                "NEW: ${newPath} <-> RECORDED: ${recordPath}.\n" +
                "💡 Suggested new fileCheck configuration (apply this to `${checksInfoPath}`):\n" +
                "${newCheckInfos}\n" +
                "⚠️ Pay attention to the excluded_lines, as they may differ from the suggested ones depending on your changes.\n"

            Exception checkFailedException = new Exception(message)
            throw checkFailedException
        }
    }
}
