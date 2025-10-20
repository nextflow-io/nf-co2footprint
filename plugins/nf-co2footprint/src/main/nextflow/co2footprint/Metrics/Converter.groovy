package nextflow.co2footprint.Metrics

import groovy.util.logging.Slf4j

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

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
     * @param element The element to search for
     * @param list The list in which to search
     * @return The index of the element in the list
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
    static String toScientificNotation(Number value) {
        value = value as BigDecimal
        if (value == 0) {
            return value.toString()
        } else if (value <= 999 && value >= 0.001) {
            return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        } else if (value == null) {
            return value
        } else {
            def formatter = new DecimalFormat("0.00E0", DecimalFormatSymbols.getInstance(Locale.US))
            return formatter.format(value)
        }
    }

    /**
     * Converts a numeric value to the closest or given scale with SI prefixes.
     * Scales the value up or down by a given factor and adjusts the scale prefix accordingly.
     * The method is structured to take the quantity as in writing e.g. scaleUnits(2048, 'k', 'B', 'M'), with
     * the last argument denoting the target to which the quantity is to be scaled.
     * In this example, 2048 kB is scaled to 2 MB, which is reported as a {@Link Quantity} Object
     * with the number 2.0, the scale 'M' and the unit 'Wh'.
     *
     * @param value Value that should be converted (e.g. 10.1)
     * @param scale Symbol for the scale of the unit (e.g. kilo = k), default of ''
     * @param unit Name / symbol for the unit (e.g. B), default of ''
     * @param targetScale The scale that should be converted to (e.g. G), default of null (optional)
     * @return Converted quantity with appropriate scale
     */
    static Quantity scaleUnits(Double value, String scale='', String unit='', String targetScale=null) {
        if (value == null) { return new Quantity(value, targetScale ?: scale, unit) }
        int scalingFactor = unit == 'B' ? 1024 : 1000
        final List<String> scales = ['p', 'n', 'u', 'm', '', 'k', 'M', 'G', 'T', 'P', 'E']  // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
        int scaleIndex = getIdx(scale, scales)

        int targetScaleIndex
        int difference
        // Either choose custom target scale,
        if (targetScale != null) {
            targetScaleIndex = getIdx(targetScale, scales)
            difference = targetScaleIndex - scaleIndex
        }
        // or use logarithm to determine the number of steps to return a number above 0
        else {
            // -scaleIndex <= floor( log_1000(|value|) ) <= maxIndex - scaleIndex
            difference = Math.min( scales.size() - 1 - scaleIndex, Math.max( -1 * scaleIndex,
                Math.floor(Math.log(Math.abs(value)) / Math.log(scalingFactor)) as int
            ))
            targetScaleIndex = scaleIndex + difference
        }

        return new Quantity(value / scalingFactor**difference, scales[targetScaleIndex], unit)
    }

    /**
     * Converts a numeric value to a human-readable string with SI prefixes.
     * Uses {@link #scaleUnits} to scale the unit to a readable format, which is
     * subsequently rounded and combined to a String.
     * For example, 1200 with unit 'Wh' becomes '1.2 kWh'.
     *
     * @param value Value to convert
     * @param scale Symbol for scale of the unit (e.g. kilo = k), default of ''
     * @param unit Name / symbol for the unit (e.g. Watt-hours/Wh), default of ''
     * @param targetScale Target scale to convert to, default of null (optional)
     * @param precision Decimal places to round to, default of 2
     * @return Scaled value as a formatted String with appropriate scale and rounding
     */
    static String toReadableUnits(Double value, String scale='', String unit='', String targetScale=null, Integer precision=2) {
        Quantity converted = scaleUnits(value, scale, unit, targetScale)

        return converted.round(precision).getReadable()
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

        // Filter out excluded time scales and sort form largest to smallest
        units = units.subList(getIdx(smallestUnit, units), getIdx(largestUnit, units) + 1).reverse()

        // Iterate from largest to smallest unit and begin including those that meet the threshold size to be included
        String timeString = ''
        for (String targetUnit : units) {
            Quantity currentTime = scaleTime(value, unit, targetUnit)

            // Handle the last unit differently
            if( targetUnit == smallestUnit ) {
                currentTime.round()     // Keep last two decimals
                
                timeString += "${currentTime.getReadable()} "
            } else {
                currentTime.floor()     // Keep only round numbers

                // Add to string and remove added value, if the threshold is reached
                if ( (threshold == null || currentTime.value > threshold) ) {
                    value -= scaleTime(currentTime.value, targetUnit, unit).value
                    timeString += "${currentTime.getReadable()} "

                    // Finish execution if value is 0
                    if (value == 0) { break }
                }
            }
        }

       return timeString.trim()
    }
}