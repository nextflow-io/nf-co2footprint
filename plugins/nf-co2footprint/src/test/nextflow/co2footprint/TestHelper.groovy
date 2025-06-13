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

package nextflow.co2footprint

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.opentest4j.AssertionFailedError

import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonSlurper
import java.security.MessageDigest

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TestHelper {

    static private fs = Jimfs.newFileSystem(Configuration.unix())

    static Path createInMemTempFile(String name='temp.file', String content=null) {
        Path tmp = fs.getPath("/tmp")
        tmp.mkdir()
        def result = Files.createTempDirectory(tmp, 'test').resolve(name)
        if( content )
            result.text = content
        return result
    }

}


/**
 * Checksum checker to compare and generate checksums (for files)
 */
class ChecksumChecker {

    // Checksums to compare to
    private Map<String, String> checksums

    /**
     * Checksum checker from a given Path with a JSON file of checksums
     *
     * @param checksumPath JSON file of checksums
     */
    ChecksumChecker(Path checksumPath=null) {
        this.checksums = checksumPath ? loadChecksums(checksumPath) : null
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
    Map<String, String> loadChecksums(Path jsonPath=this.class.getResource('/checksums.json').getPath() as Path) {
        JsonSlurper jsonSlurper = new JsonSlurper()
        this.checksums = jsonSlurper.parse(jsonPath) as Map<String, String>
        return this.checksums
    }

    /**
     * Compare the recorded checksum to the new checksum of a file.
     *
     * @param path Path to the file for the checksum calculation
     * @param recordedChecksum  The expected checksum to verify against. If null, the method will
     *                          attempt to retrieve it from the class checksum map.
     * @param excludedLines Lines to be excluded in the checksum calculation
     * @param recordedPath Path with the complete file to compare to when the checksums don't match
     */
    void compareChecksums(Path path, String recordedChecksum=null, List<Integer> excludedLines=[], Path recordPath=null) {
        String newChecksum = calculateMD5(path.toFile(), excludedLines)
        recordedChecksum ?= this.checksums[path.getFileName() as String]
        try {
            assert recordedChecksum == newChecksum
        }
        catch (AssertionError assertionError) {
            if(recordPath) {
                compareFiles(path, recordPath, excludedLines)
                throw new AssertionFailedError(
                        "Recorded checksum '${recordedChecksum}' and new checksum '${newChecksum}' did not match, " +
                        "but the checked lines (all except ${excludedLines}) in ${recordPath} and '${path}' reveal no difference."
                )
            } else {
                throw assertionError
            }
        }
    }

    /**
     * Compare two files line by line
     *
     * @param path Path to the new file
     * @param recordPath Path to the recorded File
     * @param excludedLines Lines that are not compared (useful for excluding timestamps and other non comparable stuff)
     */
    static void compareFiles(Path path, Path recordPath, List<Integer> excludedLines=[]) {
        int linePosition = 0
        path.withReader { Reader readerNew ->
            recordPath.withReader { Reader readerRecord ->
                String lineNew, lineRecord
                while ((lineNew = readerNew.readLine()) != null & (lineRecord = readerRecord.readLine()) != null) {
                    if (!excludedLines.contains(linePosition)) {
                        if (lineNew.size() < 10000 & lineRecord.size() < 10000){
                            assert lineNew == lineRecord, "Mismatch in line ${linePosition}"
                        } else if (lineNew != lineRecord) {
                             throw new AssertionFailedError(
                                     "Mismatching new line: ${lineNew}\n" +
                                     "Mismatch in line ${linePosition}. Output too long, omitting recorded line."
                             )
                        }
                    }
                    linePosition += 1
                }

                // Check for extra lines:
                if (readerNew.readLine() != null) {
                    throw new AssertionFailedError("New file has extra lines")
                }
                // Check for extra lines:
                if (readerRecord.readLine() != null) {
                    throw new AssertionFailedError("Recorded file has extra lines at the end.")
                }
            }
        }
    }
}
