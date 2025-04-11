/**
 * This script provides a collection of helper functions for converting numerical values into human-readable formats.
 * 
 * The utility functions include:
 * 1. **convertToScientificNotation**: Converts a double value into scientific notation or a rounded string representation.
 * 2. **convertToReadableUnits**: Converts a numerical value into a readable unit format (e.g., kilo, mega, giga).
 * 3. **convertBytesToReadableUnits**: Converts a byte value into a readable unit format (e.g., KB, MB, GB).
 * 4. **convertMillisecondsToReadableUnits**: Converts a time duration in milliseconds into a readable format (e.g., hours, minutes, seconds).
 * 
 * These functions are useful for formatting numerical data for display purposes, such as in logs, reports, or user interfaces.
 * 
 * @author Josua Carl <josua.carl@uni-tuebingen.de>
 */

package nextflow.co2footprint.utils

import java.text.DecimalFormat

class HelperFunctions {
    /**
     * Converts a double value into scientific notation or a rounded string representation.
     * 
     * @param value The double value to convert.
     * @return A string representation of the value in scientific notation or rounded format.
     *
     * Example:
     * ```
     * 12345.678 --> "1.23E4"
     * 0.000123 --> "1.23E-4"
     * ```
     */
    static String convertToScientificNotation(Double value) {
        if (value == 0) {
            return value.toString()
        } else if (value <= 999 && value >= 0.001) {
            return value.round(3).toString()
        } else if (value == null) {
            return value
        } else {
            def formatter = new DecimalFormat("0.00E0")
            return formatter.format(value)
        }
    }

    /**
     * Converts a numerical value into a readable unit format (e.g., pico, nano, kilo, mega).
     * 
     * @param value The numerical value to convert.
     * @param unitIndex The starting index for the unit (default is 4, representing no prefix).
     * @return A string representation of the value with the appropriate unit.
     * 
     * Example:
     * ```
     * 1234567 --> "1.23 M"
     * 0.000123 --> "123 u"
     * ```
     */
    static String convertToReadableUnits(double value, int unitIndex=4) {
        def units = ['p', 'n', 'u', 'm', ' ', 'K', 'M', 'G', 'T', 'P', 'E']  // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
        
        while (value >= 1000 && unitIndex < units.size() - 1) {
            value /= 1000
            unitIndex++
        }
        while (value <= 1 && unitIndex > 0) {
            value *= 1000
            unitIndex--
        }
        value = Math.round( value * 100 ) / 100
        return "${value} ${units[unitIndex]}"
    }

    /**
     * Converts a byte value into a readable unit format (e.g., KB, MB, GB).
     * 
     * @param value The byte value to convert.
     * @return A string representation of the value with the appropriate byte unit.
     * 
     * Example:
     * ```
     * 1024 --> "1.0 KB"
     * 1048576 --> "1.0 MB"
     * ```
     */
    static String convertBytesToReadableUnits(double value) {
        def units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
        int unitIndex=0

        while (value >= 1024 && unitIndex < units.size() - 1) {
            value /= 1024
            unitIndex++
        }

        return "${value} ${units[unitIndex]}"
    }

    /**
     * Converts a time duration in milliseconds into a readable format (e.g., hours, minutes, seconds).
     * 
     * @param value The time duration in milliseconds.
     * @return A string representation of the time in a human-readable format.
     * 
     * Example:
     * ```
     * 500 --> "500ms"
     * 65000 --> "1m 5s"
     * ```
     */
    static String convertMillisecondsToReadableUnits(double value) {
        if ( value < 1000 ) {
            return "${value}ms"
        } else {
            int h = Math.floor(value/3600000) as Integer
            int m = Math.floor((value % 3600000)/60000) as Integer
            int s = Math.floor((value % 60000)/1000) as Integer

            if ( value < 60000 )
                return "${s}s"
            else if ( value < 3600000 )
                return "${m}m ${s}s"
            else
                return "${h}h ${m}m ${s}s"
        }
        // TODO also convert to days etc. or could we keep it like this?
    }
}