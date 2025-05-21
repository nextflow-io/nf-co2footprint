package nextflow.co2footprint.utils

import java.text.DecimalFormat

/**
 * Functions to convert values
 */
class Converter {

    /**
     * Changes a number into scientific notation ($x.x \times 10^y$)
     *
     * @param value
     * @return
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
     * Convert any unit to readable by taking $10^3$ steps
     *
     * @param value Value that should be converted
     * @param scope Symbol for scope of the unit (e.g. kilo = k)
     * @param unit Name / symbol for the unit
     * @return Converted String with appropriate scale
     */
    static String toReadableUnits(double value, String scope='', String unit='') {
        final List<String> scopes = ['p', 'n', 'u', 'm', '', 'K', 'M', 'G', 'T', 'P', 'E']  // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
        int scopeIndex = scopes.indexOf(scope)

        while (value >= 1000 && scopeIndex < scopes.size() - 1) {
            value /= 1000
            scopeIndex++
        }
        while (value <= 1 && scopeIndex > 0) {
            value *= 1000
            scopeIndex--
        }
        value = Math.round( value * 100 ) / 100
        return "${value} ${scopes[scopeIndex]}${unit}"
    }

    /**
     * Adds prefixes such as Mega (MB) to Bytes and calculates correct number.
     *
     * @param value Amount of bytes
     * @return String of the value together with the appropriate unit
     */
    static String toReadableByteUnits(double value) {
        final List<String> units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
        int unitIndex=0

        while (value >= 1024 && unitIndex < units.size() - 1) {
            value /= 1024
            unitIndex++
        }

        return "${value} ${units[unitIndex]}"
    }


    /**
     * Converts the given time to another unit of time
     *
     * @param value The time as a number in original given unit
     * @param unit Given unit of time
     * @param targetUnit Unit of time to be converted to
     * @return Number of converted time
     */
    static BigDecimal convertTime(def value, String unit='ms', String targetUnit='s') {
        value = value as BigDecimal
        final List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']   // Units of time
        final List<Double> steps = [1000.0, 1000.0, 1000.0, 60.0, 60.0, 24.0, 7.0, 4.35, 12.0]                // (Average) magnitude change between units

        int givenUnitPos = units.indexOf(unit)
        int targetUnitPos = units.indexOf(targetUnit)

        // Obtain conversion rates in the given range
        if (targetUnitPos > givenUnitPos) {
            steps.subList(givenUnitPos, targetUnitPos).each { step -> value /= step }
        }
        else if (targetUnitPos < givenUnitPos) {
            steps.subList(targetUnitPos, givenUnitPos).each { step -> value *= step }
        }

        return value
    }

    /**
     * Converts a time value to a human-readable string
     *
     * @param value The time as a number in original given unit
     * @param unit Given unit of time
     * @param smallestUnit The smallest unit to convert to
     * @param largestUnit The largest unit to convert to
     * @param threshold The minimum value for the conversion to be included in the output
     * @param numSteps The maximum number of conversion steps to perform
     * @param readableString The string to append the result to
     * @return A human-readable string representation of the time value
     */
    static String toReadableTimeUnits(
        def value,
        String unit = 'ms',
        String smallestUnit = 's',
        String largestUnit = 'years',
        Double threshold = null,
        Integer numSteps = null,
        String readableString = ''
    ) {
        // Ordered list of supported time units
        final List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']

        // Calculate the number of conversion steps left
        final int smallestIdx = units.indexOf(smallestUnit)
        final int largestIdx = units.indexOf(largestUnit)
        numSteps = (numSteps == null) ? (largestIdx - smallestIdx) : (numSteps - 1)

        // Convert value to the current target unit
        String targetUnit = largestUnit
        final BigDecimal targetValue = convertTime(value as BigDecimal, unit, targetUnit)
        def targetValueFormatted = Math.floor(targetValue)

        // Singularize unit if value is exactly 1 and unit is plural
        if (targetValueFormatted == 1 && ['days', 'weeks', 'months', 'years'].contains(targetUnit)) {
            targetUnit = targetUnit.dropRight(1) // e.g. "days" -> "day"
        }

        // If this is the last step, use the remaining value as is
        if (numSteps == 0) {
            targetValueFormatted = targetValue
        }

        // Only add to output if above threshold or no threshold set
        if (threshold == null || targetValueFormatted > threshold) {
            value = targetValue - targetValueFormatted
            unit = largestUnit
            // Format to 2 decimals, remove trailing zeros
            final String formattedValue = (targetValueFormatted as BigDecimal).setScale(2, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString()
            readableString += readableString ? " ${formattedValue}${targetUnit}" : "${formattedValue}${targetUnit}"
        }

        // If we've reached the smallest unit or max steps, return the result
        if (numSteps == 0) {
            final String result = readableString.trim()
            return result ? result : "0${smallestUnit}"
        }

        // Otherwise, continue with the next smaller unit
        final String nextLargestUnit = units[largestIdx - 1]
        return toReadableTimeUnits(
                value, unit,
                smallestUnit, nextLargestUnit,
                threshold, numSteps, readableString
        )
    }
}