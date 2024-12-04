package nextflow.co2footprint

public class HelperFunctions {

    static public String convertToScientificNotation(double value) {
        if (value == 0) {
            return value.toString()
        } else if (value <= 999 && value >= 0.001) {
            return value.round(2).toString()
        } else if (value == null) {
            return value
        }
        String scientific
        Integer intPart = (Integer) value
        Integer exponential = 0
        while (value < 1 && value - intPart != 0 ) {
            value = value * 10
            exponential++
        }
        scientific = (String) value.round(2) + "e-" + exponential
        return scientific
    }

    static public String convertToReadableUnits(double value, int unitIndex=4) {
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

    static public String convertBytesToReadableUnits(double value) {
        def units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB']  // Units: Byte, Kilobyte, Megabyte, Gigabyte, Terabyte, Petabyte, Exabyte
        int unitIndex=0

        while (value >= 1024 && unitIndex < units.size() - 1) {
            value /= 1024
            unitIndex++
        }

        return "${value} ${units[unitIndex]}"
    }

    static public String convertMillisecondsToReadableUnits(double value) {
        if ( value < 1000 ) {
            return "${value}ms"
        } else {
            int h = Math.floor(value/3600000)
            int m = Math.floor((value % 3600000)/60000)
            int s = Math.floor((value % 60000)/1000)

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