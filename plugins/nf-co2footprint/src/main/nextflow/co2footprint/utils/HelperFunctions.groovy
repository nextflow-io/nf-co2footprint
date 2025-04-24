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
     * Converts the given time to another unit of time
     *
     * @param value The time as a number in original given unit
     * @param unit Given unit of time
     * @param targetUnit Unit of time to be converted to
     * @return Number of converted time
     */
    static BigDecimal convertTime(def value, String unit='ms', String targetUnit='s') {
        value = value as BigDecimal
        List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']   // Units of time
        List<Double> steps = [1000.0, 1000.0, 1000.0, 60.0, 60.0, 24.0, 7.0, 4.35, 12.0]                // (Average) magnitude change between units

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
     * Converts the given unit to a readable time string with options to limit what is shown.
     *
     * @param value The time as a number in original given unit
     * @param unit Given unit of time
     * @param smallestUnit Smallest desired unit to be reported
     * @param largestUnit Largest desired unit to be reported
     * @param threshold Smallest value to be reported (remainders are passed on to smaller units)
     * @param maximumSteps Maximum number of valid time steps to be reported
     * @return String of the readable time
     */
    static String convertTimeToReadableUnits(
            def value, String unit='ms',
            String smallestUnit='s', String largestUnit='years',
            Double threshold=null, Integer numSteps=null,
            String readableString=''
    ) {
        List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']   // Units of time

        // Determine number of remaining steps
        numSteps = numSteps == null ? units.indexOf(largestUnit) - units.indexOf(smallestUnit) : numSteps - 1

        // Calculate the time in the target unit
        String targetUnit = largestUnit
        BigDecimal targetValue = convertTime(value as BigDecimal, unit, targetUnit)
        int targetValueRound = Math.floor(targetValue) as Integer

        // Remove 's' from larger units if value is exactly 1
        if (targetValueRound == 1 && ['days', 'weeks', 'months', 'years'].contains(targetUnit)) {
            targetUnit = targetUnit.dropRight(1)
        }

        // Extend String & adjust value to avoid inaccuracies by ensuring conversion between closest units
        if (threshold == null || targetValueRound > threshold) {
            value = targetValue - targetValueRound
            unit = largestUnit
            readableString +=  numSteps == 0 ? " ${targetValue}${targetUnit}" : " ${targetValueRound}${targetUnit}"
        }

        // When smallest unit or maximum steps are reached, return remaining
        if (numSteps == 0) {
            return readableString.trim()
        }
        else {
            return convertTimeToReadableUnits(
                    value, unit,
                    smallestUnit, units[units.indexOf(largestUnit) - 1],
                    threshold, numSteps, readableString
            )
        }
    }
}