package nextflow.co2footprint.utils

import java.math.RoundingMode
import java.text.DecimalFormat
import groovy.util.logging.Slf4j

/**
 * Utility functions to convert and format values for reporting.
 *
 * Includes methods for scientific notation, readable units, byte units,
 * and human-readable time formatting.
 */ 
@Slf4j
class Converter {

    /**
     * Converts a number into scientific notation (e.g., 1.23E3).
     *
     * @param value The number to convert
     * @return String in scientific notation or rounded if in [0.001, 999]
     */
    static String toScientificNotation(Double value) {
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
     * Converts a numeric value to a human-readable string with SI prefixes.
     * For example, 1200 with unit 'Wh' becomes '1.2 KWh'.
     * Scales the value up or down by factors of 1000 and adjusts the prefix accordingly.
     *
     * @param value Value that should be converted
     * @param scope Symbol for scope of the unit (e.g. kilo = k)
     * @param unit Name / symbol for the unit
     * @return Converted String with appropriate scale
     */
    static String toReadableUnits(double value, String scope='', String unit='') {
        final List<String> scopes = ['p', 'n', 'u', 'm', '', 'K', 'M', 'G', 'T', 'P', 'E']  // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
        int scopeIndex = scopes.indexOf(scope)

        // Scale up if value is large
        while (value >= 1000 && scopeIndex < scopes.size() - 1) {
            value /= 1000
            scopeIndex++
        }
        // Scale down if value is small
        while (value <= 1 && scopeIndex > 0) {
            value *= 1000
            scopeIndex--
        }
        value = Math.round( value * 100 ) / 100
        return "${value} ${scopes[scopeIndex]}${unit}"
    }

    /**
    * Converts a byte value to a human-readable string with binary prefixes,
    * starting from a specified unit.
    * For example, 1 with currentUnit='GB' becomes '1 GB'.
    *
    * @param value Amount of bytes (or KB, MB, etc. depending on currentUnit)
    * @param currentUnit The unit of the input value (default: 'B')
    * @return String of the value together with the appropriate unit
    */
    static String toReadableByteUnits(double value, String currentUnit = 'B') {
        final List<String> units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
        int unitIndex = units.indexOf(currentUnit)

        while (value >= 1024 && unitIndex < units.size() - 1) {
            value /= 1024
            unitIndex++
        }

        return "${value} ${units[unitIndex]}"
    }


    /**
    * Converts a time value from one unit to another.
    * E.g., 120000 ms to minutes returns 2.
    *
    * @param value      The time as a number in original given unit
    * @param unit       Given unit of time (e.g. 'ms')
    * @param targetUnit Unit of time to be converted to (e.g. 'min')
    * @return           Number of converted time as BigDecimal
    */
    static BigDecimal convertTime(def value, String unit='ms', String targetUnit='s') {
        value = value as BigDecimal
        // Supported time units
        final List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years'] 
        // Conversion factors between units (e.g., 1000 ms = 1 s, 60 s = 1 min, etc.)
        final List<Double> steps = [1000.0, 1000.0, 1000.0, 60.0, 60.0, 24.0, 7.0, 4.35, 12.0] 

        int from = units.indexOf(unit)
        int to = units.indexOf(targetUnit)

        // Convert up (e.g., ms to min)
        if (to > from) {
            steps.subList(from, to).each { value /= it }
        // Convert down (e.g., min to ms)
        } else if (to < from) {
            steps.subList(to, from).each { value *= it }
        }
        return value
    }

    /**
    * Converts a time value to a human-readable string, e.g. "2 days 3 h 4 min".
    * Breaks down the value into the largest possible units.
    *
    * @param value         The time as a number in original given unit
    * @param unit          Given unit of time (default: 'ms')
    * @param smallestUnit  The smallest unit to convert to (default: 's')
    * @param largestUnit   The largest unit to convert to (default: 'years')
    * @param threshold     The minimum value for the conversion to be included in the output (optional)
    * @return              A human-readable string representation of the time value
    */
    static String toReadableTimeUnits(
        def value,
        String unit = 'ms',
        String smallestUnit = 's',
        String largestUnit = 'years',
        Double threshold = null
    ) { 
        // Ordered list of supported time units
        final List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']
        // Conversion factors between units (e.g., 1000 ms = 1 s, 60 s = 1 min, etc.)
        final List<BigDecimal> steps = [1000G, 1000G, 1000G, 60G, 60G, 24G, 7G, 4.35G, 12G] 

        // Get indices for smallest and largest units
        int smallestIdx = getIdx(smallestUnit, units)
        int largestIdx = getIdx(largestUnit, units)

        // Convert input value to the largest unit for breakdown
        BigDecimal remaining = convertTime(value, unit, units[largestIdx]) // Convert to the largest unit
        List<String> parts = [] // Collect formatted time units

        // Iterate from largest to smallest unit, extracting each unit's value
        for (int i = largestIdx; i >= smallestIdx; i--) {
            BigDecimal unitValue
            if (i > smallestIdx) {
                // For non-smallest units, use integer part only
                unitValue = remaining.setScale(0, RoundingMode.DOWN)
                // Update remaining value for next smaller unit
                remaining = (remaining - unitValue) * steps[i - 1]
            } else {
                // For the smallest unit, keep up to 2 decimals
                unitValue = remaining.setScale(2, RoundingMode.HALF_UP)
                remaining = 0
            }

            // Only include units above threshold (if set) or non-zero units
            if ((threshold == null && unitValue > 0) || (threshold != null && unitValue > threshold)) {
                // Use singular label for units like "day", "week", etc. if value is 1
                String label = unitValue == 1 && ['days', 'weeks', 'months', 'years'].contains(units[i])
                    ? units[i][0..-2] : units[i]
                // Format value, removing trailing zeros
                String formatted = unitValue.stripTrailingZeros().toPlainString()
                parts << "${formatted}${label}"
            }
        }
        
        // Special handling: if smallest unit is exactly the conversion factor, roll up to next unit
        if (parts && smallestIdx > 0) {
            // Check if the last unit (smallest) is exactly the conversion factor
            BigDecimal lastValue = parts[-1].replaceAll(/[^\d.]/, '') as BigDecimal
            String lastUnit = parts[-1].replaceAll(/[\d.]/, '')
            BigDecimal conversionFactor = steps[smallestIdx - 1]
            if (lastValue == conversionFactor) {
                // Remove the last part (e.g., "1000ms")
                parts.remove(parts.size() - 1)
                // Increment the previous unit by 1
                String prev = parts[-1]
                String prevNum = prev.replaceAll(/[^\d.]/, '')
                String prevUnit = prev.replaceAll(/[\d.]/, '')
                BigDecimal newPrev = (prevNum as BigDecimal) + 1
                parts[-1] = "${newPrev.stripTrailingZeros().toPlainString()}${prevUnit}"
            }
        }

        // Join all parts with spaces, or return "0" + smallestUnit if no parts
        return parts ? parts.join(' ') : "0${smallestUnit}"
    }

    /**
     * Gets the index of an element in a list.
     * Throws an error if the element is not found.
     *
     * @param element The element to find
     * @param list The list to search in
     * @return The index of the element in the list
     */
    static int getIdx(Object element, List<Object> list) {
        int idx = list.indexOf(element)

        if (idx < 0) {
            String message = "Unknown element `${element}` not found in `${list}`"
            log.error(message)
            throw new IllegalArgumentException(message)
        }

        return idx
    }
}