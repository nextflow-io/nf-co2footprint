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
     * Checks whether a element was found in a list and throws an error if not.
     *
     * @param unit Unit as a String
     * @param units List of units that are available
     */
    static int getIdx(Object element, List<Object> list) {
        int idx = list.indexOf(element)

        if (idx < 0) {
            String message = "Element `${element}` not found in list `${list}`"
            log.error(message)
            throw new IllegalArgumentException(message)
        }

        return idx
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
     * For example, 1200 with unit 'Wh' becomes '1.2 kWh'.
     * Scales the value up or down by a given factor and adjusts the scale prefix accordingly.
     *
     * @param value Value that should be converted (e.g. 10.1)
     * @param scope Symbol for scope of the unit (e.g. kilo = k), default ''
     * @param unit Name / symbol for the unit (e.g. B), default ''
     * @param targetScale The scale that should be converted to (e.g. G)
     * @param scalingFactor The factor with which to scale (e.g. 1024), default 1000
     * @return Converted quantity with appropriate scale
     */
    static Quantity scaleUnits(double value, String scale='', String unit='', String targetScale=null, int scalingFactor=1000) {
        final List<String> scales = ['p', 'n', 'u', 'm', '', 'k', 'M', 'G', 'T', 'P', 'E']  // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
        int scaleIndex = getIdx(scale, scales)

        int targetScaleIndex
        int difference
        // Either choose custom target scale,
        if (targetScale != null) {
            targetScaleIndex = getIdx(targetScale, scales)
            difference = targetScaleIndex - scaleIndex
        }
        // or use $floor(log_{1024}(value))$ to determine the number of steps that are taken to return a number above 0
        else {
            difference = Math.floor(Math.log(value) / Math.log(scalingFactor)) as int
            targetScaleIndex = scaleIndex + difference
        }

        return new Quantity(value / scalingFactor**difference, scales[targetScaleIndex], unit)
    }

    /**
     * Converts a numeric value to a human-readable string with SI prefixes.
     * For example, 1200 with unit 'Wh' becomes '1.2 kWh'.
     * Scales the value up or down by factors of 1000 and adjusts the prefix accordingly.
     *
     * @param value Value that should be converted
     * @param scope Symbol for scope of the unit (e.g. kilo = k)
     * @param unit Name / symbol for the unit
     * @param targetScale Target scale to convert to
     * @param scalingFactor Factor by which to scale
     * @param precision Precision to round the value(s) to
     * @return Converted String with appropriate scale and rounding
     */
    static String toReadableUnits(double value, String scale='', String unit='', String targetScale=null, int scalingFactor=1000, Integer precision=2) {
        Quantity converted = scaleUnits(value, scale, unit, targetScale, scalingFactor)

        return converted.round(precision).getReadable(true)
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
    static Quantity scaleTime(Number value, String unit='ms', String targetUnit='s') {
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

        // Remove 's' for single time units
        if (value == 1 && ['days', 'weeks', 'months', 'years'].contains(targetUnit)) {
            targetUnit = targetUnit.dropRight(1)
        }

        return new Quantity(value, targetUnit, '', '')
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
     * @return A human-readable string representation of the time value
     */
    static String toReadableTimeUnits(
        Number value,
        String unit = 'ms',
        String smallestUnit = 's',
        String largestUnit = 'years',
        Double threshold = 0.0
    ) {
        // Ordered list of supported time units
        List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']

        // Filter out excluded time scales
        units = units.subList(getIdx(smallestUnit, units), getIdx(largestUnit, units) + 1)

        // Iterate from largest to smallest unit and begin including those that meet the threshold size to be included
        String timeString = ''
        for (String targetUnit : units.reversed()) {
            Quantity currentTime = scaleTime(value, unit, targetUnit)

            if( targetUnit == smallestUnit ) {
                currentTime.round()
                timeString += "${currentTime.getReadable()} "
                break
            } else {
                currentTime.floor()
            }

            if ( (threshold == null || currentTime.value > threshold) ) {
                value -= scaleTime(currentTime.value, targetUnit, unit).value
                timeString += "${currentTime.getReadable()} "
            }

            if (value == 0) { break }
        }

       return timeString.trim()
    }
}