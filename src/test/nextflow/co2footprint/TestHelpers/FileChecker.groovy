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

import groovy.yaml.YamlSlurper
import org.opentest4j.AssertionFailedError
import org.yaml.snakeyaml.Yaml

import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Checksum checker to compare and generate checksums (for files) and more
 */
class FileChecker {
    // YAML Slurper
    Yaml yaml = new Yaml()

    // Directory with files to check
    private Path checksDirectory

    // Directory with recorded files in build test resources
    private Path buildChecksDirectory

    // Checksums to compare to
    private Path checksInfoPath

    // Folder for files with failed checks
    private Path failPath

    // Parsed check information
    private Map<String, Map<String, ?>> checksInfo

    // Collect errors or throw directly?
    boolean collectErrors = false

    // Error store
    List<Throwable> errors = []

    /**
     * Checksum checker from a given path, relative to `testResources`.
     * Optionally with a JSON file with check infos, such as checksums.
     *
     * @param checksDirectory Path relative to `testResources`
     * @param collectErrors Whether or not to collect errors or raise them directly
     */
    FileChecker(String checksDirectory='.', boolean collectErrors=false) {
        Path projectRoot = Path.of(System.getProperty('user.dir'))
        String relativeChecksPath = checksDirectory.startsWith('/') ? checksDirectory.substring(1) : checksDirectory
        this.checksDirectory = projectRoot.resolve('src/testResources').resolve(relativeChecksPath)

        this.checksInfoPath = this.checksDirectory.resolve('file_checks.yaml')
        if(!checksInfoPath.isFile()) {
            this.checksInfoPath = this.checksDirectory.resolve('file_checks.json')
        }
        this.checksInfo = checksInfoPath.isFile() ? loadChecksInfo(checksInfoPath) : null

        this.collectErrors = collectErrors

        buildChecksDirectory = projectRoot.resolve('build/resources/test').resolve(relativeChecksPath)
        failPath = buildChecksDirectory.resolve('failed')
        failPath.mkdirs()
    }

    /**
     * Adds a new error if it should be collected, otherwise throws it‚
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
     * Load a YAML file with checksums.
     *
     * @param path Path to the YAML file with checksums
     * @return The checksums as a Map with String keys and values
     */
    static Map<String, Map<String, ?>> loadChecksInfo(Path yamlPath) {
        YamlSlurper yamlSlurper = new YamlSlurper()
        return yamlSlurper.parse(yamlPath) as Map<String, Map<String, ?>>
    }

    /**
     * Raises an error when lines mismatch.
     * @param lineMismatches - A list with all mismatched lines
     */
    void raiseLineMismatchError(Map<Integer, List<String>> lineMismatches) {
        if (lineMismatches.size() > 0) {
            StringBuilder stringBuilder = new StringBuilder()
            lineMismatches.each { Integer pos, List<String> mismatchLines ->
                if (mismatchLines.any( {String line -> line.size() > 10000} )){
                    stringBuilder.append("${pos}:\tLine too long to display, please refer to file comparison.\n")
                }
                else{
                    stringBuilder.append("${pos}:\t|${mismatchLines.join('<->')}|\n")
                }
            }
            Throwable assertionError = new AssertionFailedError(
                "The following ${lineMismatches.size()} line mismatches were found (|<Actual><-><Recorded>|):\n" +
                stringBuilder.toString()
            )
            addError(assertionError)
        }
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
     * Compare two files line by line
     *
     * @param path Path to the new file
     * @param recordPath Path to the recorded File
     */
    boolean compareFiles(CheckFile newCheckFile, CheckFile recordCheckFile) {
        boolean errorFound = false

        Map<Integer, List<String>> lineMismatches = [:]
        List<String> newLines = newCheckFile.lines
        List<String> recordedLines = recordCheckFile.lines

        // Check for missing lines
        Integer lineDifference = recordedLines.size() - newLines.size()
        if (lineDifference > 0) {
            errorFound = true
            addError( new AssertionFailedError("Newly generated file is missing ${lineDifference} lines.") )
        }
        // Check for extra lines
        else if (lineDifference < 0) {
            errorFound = true
            addError( new AssertionFailedError("Newly generated file has ${lineDifference * -1} extra lines.") )
        }
        else {
            String lineNew, lineRecord
            for (i in 0..<newLines.size()) {
    
                lineNew = newLines[i]
                lineRecord = recordedLines[i]
                try {
                    assert lineNew == lineRecord
                }
                catch (Throwable ignore) {
                    errorFound = true
                    lineMismatches[i + 1] = [lineNew, lineRecord]
                }
            }
        }
        
        raiseLineMismatchError(lineMismatches)

        return errorFound
    }

    void runChecks(Path path, Map<String, List<String>> replacements=[:], List<String> exclusions=[], Path recordedPath=null){
        // Set errors to collection
        this.collectErrors = true

        // Check file property
        checkIsFile(path)

        // Get Infos to check for
        recordedPath ?= buildChecksDirectory.resolve(path.getFileName())
        
        Map<String, Object> checksInfo = this.checksInfo.get(recordedPath.getFileName() as String, [:]).deepClone()
        
        // Check replacements and excluded lines
        List<Integer> excludedLines = checksInfo.remove('excluded_lines') as List<Integer> ?: []
        Map<String, String> checkInfoReplacements = checksInfo.remove('replacements') as Map<String, String> ?: [:]
        replacements.putAll(checkInfoReplacements)
        
        // Define check file instances
        CheckFile checkFile = CheckFile.of(path, [:], exclusions, excludedLines, 1)
        CheckFile recordedCheckFile = CheckFile.of(recordedPath, replacements, exclusions, excludedLines, 1)

        // Prepare new file check infos
        Map<String, Object> newCheckInfos = [:]
        
        // Perform checksum testing
        String checksum = checksInfo.remove('checksum')
        String newChecksum
        if (checksum) {
            newChecksum = checkFile.compareChecksums(checksum)
            newCheckInfos['checksum'] = newChecksum ?: checksum
        }
        else {
            newChecksum = checkFile.compareChecksums(recordedCheckFile)
        }

        // Perform line count check
        Integer numLines = checksInfo.remove('num_lines')
        Long newNumLines
        if (numLines) {
            newNumLines = compareNumLines(path, numLines)
            newCheckInfos['num_lines'] = newNumLines
        }

        // Perform full line by line comparison, if checksum did not match
        if (newChecksum) {
            String message = "The generated checksum `${newChecksum}` does not match with the recorded `${checksum}`"
            boolean errorFound = compareFiles(checkFile, recordedCheckFile)
            if (!errorFound) {
                message += '\nℹ️ The line-by-line match revealed no difference, the checksum should be updated.'
            }
            addError( new AssertionFailedError(message) )
        }
        
        // Append additional info to new check JSON
        if (excludedLines) {
            Integer lineDifference = recordedCheckFile.lines.size() - checkFile.lines.size()
            newCheckInfos['excluded_lines'] = excludedLines.collect { Integer excludedLine -> excludedLine - lineDifference }
        }

        // Reset error collection
        this.collectErrors = false

        // Throw errors if existent
        if (errors) {
            Path failedSnapshotPath = failPath.resolve(path.fileName)
            // Copy snapshot
            Files.copy(path, failedSnapshotPath, StandardCopyOption.REPLACE_EXISTING)
            
            Object finalConfig = [(recordedPath.getBaseName()): newCheckInfos]
            String yamlString = yaml.dump(finalConfig)
            // Print info to adopt the changes
            String message =
                "\n❌ File checks for '${path}' failed,\n" +
                "🔎 The actual error messages can be found below as a list.\n" +
                "ℹ️ You may want to have a look at the difference between the new and recorded file:\n" +
                "NEW: ${failedSnapshotPath} <-> RECORDED: ${recordedPath}.\n" +
                "💡 Suggested new fileCheck configuration (apply this to `${checksInfoPath}`):\n" +
                "\n${yamlString}\n" +
                "⚠️ Pay attention to the replacements, as they may differ from the suggested ones depending on your changes.\n"

            Exception checkFailedException = new Exception(message)
            
            // Add errors to general error
            errors.eachWithIndex { Throwable error, Integer i->
                Throwable numberError = new Error("----------------- File Checker Error ${i}:\n")
                numberError.addSuppressed(error)
                checkFailedException.addSuppressed(numberError)
            }
            errors = []
            throw checkFailedException
        }
    }

    /**
     * Check multiple files in a single scoop.
     * 
     * @param fileCheckMap Map with all options assigned to a name.
     */
    void runMultiFileChecks(Map<String, Map<String, Object>> fileCheckMap) {
        Map<String, Throwable> errorsMulti = [:]
        fileCheckMap.each { String runName, Map<String, Object> options ->
            try {
                runChecks(
                        options['path'] as Path,
                        options.get('replacements', [:]) as Map<String, List<String>>,
                        options.get('exclusions', []) as List<String>,
                        options.get('recordedPath') as Path
                )
            }
            catch (Throwable error) {
                errorsMulti[runName] = error
            }
        }
        
        if(errorsMulti) {
            String message = "\n❌ File checks failed for the following: ${errorsMulti.keySet()}\n"
            Exception checkFailedExceptionMulti = new Exception(message)
            
            errorsMulti.each { String name, Throwable error -> checkFailedExceptionMulti.addSuppressed(error)}
            
            throw checkFailedExceptionMulti
        }
        
    }
}
