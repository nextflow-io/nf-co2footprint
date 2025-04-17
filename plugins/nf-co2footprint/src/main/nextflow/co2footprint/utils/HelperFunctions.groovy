package nextflow.co2footprint.utils

import java.text.DecimalFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

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

    static String convertBytesToReadableUnits(double value) {
        def units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
        int unitIndex=0

        while (value >= 1024 && unitIndex < units.size() - 1) {
            value /= 1024
            unitIndex++
        }

        return "${value} ${units[unitIndex]}"
    }

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

    // Helper function to return bold text
    static String bold(String text) {
        return "\033[1m${text}\033[0m"
    }

    static transformTimestamp(String isoTimestamp) {
        // Parse the ISO 8601 timestamp
        ZonedDateTime dateTime = ZonedDateTime.parse(isoTimestamp)

        // Convert to local time zone
        ZonedDateTime localTime = dateTime.withZoneSameInstant(ZoneId.systemDefault())

        // Define a user-friendly format (e.g., "April 8, 2025, 07:43 AM UTC")
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy, hh:mm a z")

        // Format the timestamp
        return localTime.format(formatter)
    }
}