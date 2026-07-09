package nextflow.co2footprint.TestHelpers

import java.nio.file.Path
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * A class to create files that can be easily checked in tests.
 */
class CheckFile {    
    // Actual content
    final String content
    final List<String> lines
    final List<Integer> excludedLines
    String checksum = null

    /**
     * Prepare file so it is easier checkable by replacing some of its content and remove some lines.
     * 
     * @param lines
     * @param replacements
     * @param excludedLines
     * @param excludedLineShift
     */
    CheckFile(List<String> lines, Map<String, List<String>> replacements, List<Integer> excludedLines, Integer excludedLineShift) {
        this.excludedLines = excludedLines
        lines = excludeLines(lines, excludedLines, excludedLineShift)
        this.lines = replaceInTemplate(lines, replacements)
        this.content = this.lines.join(System.lineSeparator())
    }
    
    String getChecksum() {
        return checksum ?: calculateMD5(content)
    }
    
    /**
     * Replaces strings in a template found under the given path.
     *
     * @param lines - Lines of the template
     * @param replacements - A map with grouped regular expressions and their replacements
     * @return The lines with replacements as a String
     */
    static List<String> replaceInTemplate(List<String> lines, Map<String, List<String>> replacements) {
        List<String> newLines = []
        if (replacements.size() > 0) {
            lines.each { String line ->
                String currentLine = line
                replacements.each { String regex, List<String> replacementList ->
                    line.eachMatch(regex) {
                        replacementList.eachWithIndex { String replacement, int i ->
                            currentLine = currentLine.replace(it[i+1], replacement)
                        }
                    }
                }
                newLines.add(currentLine)
            }
            return newLines
        }
        
        return lines
    }

    /**
     * Excludes lines from the given list.
     * 
     * @param lines - List of lines
     * @param excludedLines - List of integers with positions that are to be excluded
     * @param excludedLineShift - A number by which all excluded lines are shifted
     * @return A combined String with the excluded lines removed
     */
    static List<String> excludeLines(List<String> lines, List<Integer> excludedLines, Integer excludedLineShift=0) {
        excludedLines = excludedLines.collect( { Integer lineNum -> lineNum - excludedLineShift })

        excludedLines.each { int i -> lines.remove(i) }
        
        return lines
    }

    static CheckFile of(List<String> lines, Map<String, List<String>> replacements, List<Integer> excludedLines = [], Integer excludedLineShift=0) {
        return new CheckFile(lines, replacements, excludedLines, excludedLineShift)
    }

    static CheckFile of(String content, Map<String, List<String>> replacements, List<Integer> excludedLines = [], Integer excludedLineShift=0) {
        return new CheckFile(content.readLines(), replacements, excludedLines, excludedLineShift)
    }

    static CheckFile of(Path path, Map<String, List<String>> replacements, List<Integer> excludedLines = [], Integer excludedLineShift=0) {
        return new CheckFile(path.readLines(), replacements, excludedLines, excludedLineShift)
    }

    static CheckFile of(File file, Map<String, List<String>> replacements, List<Integer> excludedLines = [], Integer excludedLineShift=0) {
        return new CheckFile(file.readLines(), replacements, excludedLines, excludedLineShift)
    }

    /**
     * Compare select lines against recorded lines
     *
     * @param path Path to file
     * @return A Map of mismatched position and content
     */
    Map<Integer, List<String>> compare(List<String> otherLines) {
        Map<Integer, List<String>> lineMismatches = [:]
        otherLines.eachWithIndex{ String otherLine, int i ->
            try {
                assert lines[i] == otherLine
            }
            catch (Throwable ignore) {
                lineMismatches[i+1] = [lines[i], otherLine]
            }
        }

        return lineMismatches
    }
    
    /**
     * Calculates the MD5 checksum for a file
     *
     * @param str String to be calculated the checksum to
     * @return The checksum of the file as a String
     */
    static String calculateMD5(String str) {
        MessageDigest md = MessageDigest.getInstance("MD5")

        str.eachLine { String line ->
            byte[] bytes = (line + System.lineSeparator()).getBytes("UTF-8")
            md.update(bytes)
        }
        return md.digest().encodeHex().toString()
    }
    
    /**
     * Compare the recorded checksum to the new checksum of a file.
     *
     * @param otherCheckFile {@link CheckFile} to compare against.
     * @return Returns other checksum if they do not match
     */
    String compareChecksums(String otherChecksum){
       if(checksum != otherChecksum) { 
            return otherChecksum
       }
       return null
    }

    /**
     * Compare the recorded checksum to the new checksum of a file.
     *
     * @param otherCheckFile {@link CheckFile} to compare against.
     * @return New checksum
     */
    String compareChecksums(CheckFile otherCheckFile){
        return compareChecksums(otherCheckFile.checksum)
    }

    /**
     * Compare select lines against recorded lines
     *
     * @param path Path to file
     * @return A Map of mismatched position and content
     */
    Map<Integer, List<String>> compare(Map<Integer, String> otherLines) {
        String line
        Map<Integer, List<String>> lineMismatches = [:]
        otherLines.each { Integer linePos, String lineRecord ->
            // Change from 1 to 0-based
            line = lines[linePos - 1]
            try {
                assert line == lineRecord
            }
            catch (Throwable ignore) {
                lineMismatches[linePos] = [line, lineRecord]
            }
        }

        return lineMismatches
    }

    /**
     * Compare select lines against recorded lines
     *
     * @param path Path to file
     * @return A Map of mismatched position and content
     */
    Map<Integer, List<String>> compare(CheckFile otherFile) {
        return compare(otherFile.lines)
    }
}