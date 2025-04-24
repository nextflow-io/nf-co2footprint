package nextflow.co2footprint.utils

import java.text.DecimalFormat

class HelperFunctions {

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

    static String convertToReadableUnits(double value, int unitIndex=4, String unit='') {
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
        return "${value} ${units[unitIndex]}${unit}"
    }

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
     * Converts the given unit to a readable time string with options to limit what is shown.
     *
     * @param value Time as double
     * @param unit Unit of time
     * @param smallestUnit Smallest desired unit to be reported
     * @param largestUnit Largest desired unit to be reported
     * @param smallestValue Smallest value to be reported
     * @param maximumSteps Maximum number of valid time steps to be reported
     * @return String of the readable time
     */
    static String convertTimeToReadableUnits(
            def value, String unit='ms',
            String smallestUnit='s', String largestUnit='years',
            Double includeOnlyBigger=null, Integer maximumSteps=null
    ) {
        value = value as BigDecimal
        List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']   // Units of time
        List<Double> steps = [1000.0, 1000.0, 1000.0, 60.0, 60.0, 24.0, 7.0, 4.35, 12.0]                // (Average) magnitude change between units

        int givenUnitPos = units.indexOf(unit)
        int numSteps = 0
        String readableString = ''

        // Iterate over all units between largest and smallest desired output
        for (
                int currentUnitPos = units.indexOf(largestUnit);
                currentUnitPos >= units.indexOf(smallestUnit);
                currentUnitPos--
        ) {
            String currentUnit = units[currentUnitPos]

            // Obtain conversion rates in the given range
            BigDecimal conversionRate = 1.0
            if (currentUnitPos > givenUnitPos) {
                steps.subList(givenUnitPos, currentUnitPos).each { step -> conversionRate *= step }
            }
            else if (currentUnitPos < givenUnitPos) {
                steps.subList(currentUnitPos, givenUnitPos).each { step -> conversionRate /= step }
            }

            // Calculate the Value of the current unit with the remaining value
            BigDecimal currentExactUnitValue = value / conversionRate
            int currentUnitValue = Math.floor(currentExactUnitValue) as Integer

            // Remove 's' from larger units if value is exactly 1
            if (currentUnitValue == 1 && currentUnitPos > 5) {
                currentUnit = currentUnit.dropRight(1)
            }

            // When smallest unit or maximum steps are reached, return remaining
            if (currentUnit == smallestUnit || (maximumSteps != null && numSteps > maximumSteps)) {
                if (
                        (includeOnlyBigger == null || currentExactUnitValue > includeOnlyBigger) &&
                        (maximumSteps == null || numSteps < maximumSteps)
                ) {
                    readableString = "${readableString} ${currentExactUnitValue}${currentUnit}"
                }
                break
            }
            // Report value only if larger or equal to 1
            else if (includeOnlyBigger == null || currentUnitValue > includeOnlyBigger) {
                numSteps += 1
                value = value - currentUnitValue * conversionRate
                readableString = "${readableString} ${currentUnitValue}${currentUnit}"
            }
        }

        return readableString.trim()
    }
}