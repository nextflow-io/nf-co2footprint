package nextflow.co2footprint.utils

import groovy.util.logging.Slf4j

import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Utility functions to convert and format values for reporting.
 *
 * Includes methods for scientific notation, readable units, byte units,
 * and human-readable time formatting.
 */
@Slf4j
class Converter {

    /**
     * Checks whether a unit was found and throws an error if not.
     *
     * @param unit Unit as a String
     * @param units List of units that are available
     */
    static int matchUnit(String unit, List<String> units) {
        int unitIndex = units.indexOf(unit)

        if (unitIndex < 0) {
            String message = "Unit `${unit}` not found in units `${units}`"
            log.error(message)
            throw new IllegalArgumentException(message)
        }

        return unitIndex
    }

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
     * Converts a byte value to another byte unit scale.
     * For example, 1048576 becomes [value: 1.0, unit: 'MB'].
     *
     * @param value Amount of respective unit
     * @param unit Starting unit, defaults to 'B'/bytes
     * @param targetUnit Target unit, optional
     * @return The value and the unit in a Map when converted to right scale.
     */
    static Quantity convertByteUnits(double value, String unit='B', String targetUnit=null) {
        Quantity quantity = new Quantity(value, unit)
        final List<String> units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
        int unitIndex = matchUnit(unit, units)

        int targetUnitIndex
        int difference
        // Either choose custom target unit,
        if (targetUnit) {
            targetUnitIndex = matchUnit(targetUnit, units)
            difference = targetUnitIndex - unitIndex
        }
        // or use $floor(log_{1024}(value))$ to determine the number of steps that are taken to return a number above 0
        else {
            difference = Math.floor(Math.log(value) / Math.log(1024)) as int
            targetUnitIndex = unitIndex + difference
        }

        // Adjust value to the target unit
        quantity.set(value / 1024**difference,  units[targetUnitIndex])

        return quantity
    }

    /**
     * Converts a byte value to a human-readable string with binary prefixes,
     * starting from a specified unit.
     * For example, 1 with currentUnit='GB' becomes '1 GB'.
     *
     * @param value Amount of bytes
     * @param unit Starting unit
     * @param targetUnit Target unit
     * @return String of the value together with the appropriate unit
     */
    static String toReadableByteUnits(double value, String unit='B', String targetUnit=null) {
        return convertByteUnits(value, unit, targetUnit).getReadable()
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
     * Recursively breaks down the value into the largest possible units.
     *
     * @param value The time as a number in original given unit
     * @param unit Given unit of time (default: 'ms')
     * @param smallestUnit The smallest unit to convert to (default: 's')
     * @param largestUnit The largest unit to convert to (default: 'years')
     * @param threshold The minimum value for the conversion to be included in the output (optional)
     * @param numSteps The maximum number of conversion steps to perform (optional)
     * @param readableString The string to append the result to (for recursion, optional)
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
            final String formattedValue = (targetValueFormatted as BigDecimal).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
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