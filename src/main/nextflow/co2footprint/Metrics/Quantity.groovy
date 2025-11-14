package nextflow.co2footprint.Metrics


import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * A number associated with a unit and other descriptors.
 */
class Quantity extends Metric<BigDecimal> {
    String scale
    boolean integerType
    String separator = ' '
    int scalingFactor = 1000

    /**
     * Creator of a Quantity, combining the tracking and reporting of a number, associated with a unit.
     *
     * @param value         Numerical value, saved in the quantity
     * @param scale         Scale of the Quantity, defaults to ''
     * @param unit          Unit of the metric
     * @param type          Type of he value
     * @param description   A human-readable description of the value
     */
    Quantity(Object value, String scale='', String unit='', String type='Number', String description = null) {
        super(value as BigDecimal, type, unit, description)
        this.scale = scale
        integerType = [Integer, Long, int, long, BigInteger].contains(value?.class)
    }

    /**
     * Creates a {@link Metric} or a {@link Quantity} if the value is numerical.
     *
     * @param value         Numerical value, saved in the quantity
     * @param scale         Scale of the Quantity, defaults to ''
     * @param unit          Unit of the quantity
     * @param type          Type of he value, defaults to 'Number'
     * @param description   A human-readable description of the value
     * @return
     */
    static Metric of(Object value, String scale='', String unit='', String type='Number', String description = null) {
        if (value instanceof Number) {
            return new Quantity(value, scale, unit, type, description)
        }
        else {
            return new Metric(value, type, "${scale}${unit}", description)
        }
    }

    /**
     * Round the value to a certain precision.
     *
     * @param precision, default: 2
     * @param roundingMode, How to round the number, default: RoundingMode.HALF_UP
     */
    Quantity round(Integer precision=2, RoundingMode roundingMode=RoundingMode.HALF_UP) {
        if (precision != null) {
            value = value.setScale(precision, roundingMode)
        }

        return this
    }

    /**
     * Round the value to a certain precision.
     *
     * @param precision, default: 0
     */
    Quantity floor(Integer precision=0) {
        if (precision != null) {
            value = value.setScale(precision, RoundingMode.FLOOR)
        }
        return this
    }

    /**
     * Converts a numeric value to the closest or given scale with SI prefixes.
     * Scales the value up or down by a given factor and adjusts the scale prefix accordingly.
     *
     * @param targetScale The scale that should be converted to (e.g. G), default of null (optional)
     * @return Converted quantity with appropriate scale
     */
    Quantity scale(String targetScale=null) {
        if (value) {
            final List<String> scales = ['p', 'n', 'u', 'm', '', 'k', 'M', 'G', 'T', 'P', 'E']
            // Units: pico, nano, micro, milli, 0, Kilo, Mega, Giga, Tera, Peta, Exa
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
                difference = Math.min(scales.size() - 1 - scaleIndex, Math.max(-1 * scaleIndex,
                        Math.floor(Math.log(Math.abs(value)) / Math.log(scalingFactor)) as int
                ))
                targetScaleIndex = scaleIndex + difference
            }
            value = (value / scalingFactor**difference) as BigDecimal
            targetScale = scales[targetScaleIndex]
        }

        scale = targetScale
        return this
    }

    /**
     * Converts a quantity into scientific notation (e.g., 1.23E3).
     *
     * @return String in scientific notation or rounded if in [0.001, 999]
     */
    String toScientificNotation() {
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
     * Converts a numeric value to a human-readable string with SI prefixes.
     * {@link #scale}s the quantity to a readable format, which is subsequently rounded and combined to a String.
     * For example, 1200 with unit 'Wh' becomes '1.2 kWh'.
     *
     * @param targetScale Target scale to convert to, default of null (optional)
     * @param precision Decimal places to round to, default of 2
     * @return Scaled value as a formatted String with appropriate scale and rounding
     */
    String toReadable(String targetScale=null, Integer precision=2) {
        return this.scale(targetScale).round(precision).getReadable()
    }

    /**
     * Get the readable representation of this quantity.
     * Example: '1 GB' for value = 1, scale = 'G', unit = 'B'
     *
     * @return String 'value scale+unit'
     */
    String getReadable() {
        // Remove trailing Zeros and convert to readable String
        String readable = this.value.stripTrailingZeros().toPlainString()

        // Add scale and unit with separator only if one of them is given
        String scaledUnit = (this.scale ?: '') + (this.unit ?: '')
        if (scaledUnit) { readable += this.separator + scaledUnit }

        return readable
    }

    /**
     * Returns the correct representation of the value. If the original value has been of an integer-associated type,
     * it will return the {@link BigInteger}, otherwise the {@link BigDecimal} representation.
     *
     * @return A representation of the current value
     */
    Number returnValue() {
        return  integerType ? value.toBigIntegerExact() : value
    }

    /**
     * Convert the Quantity object to a map with all metaData as named entries.
     *
     * @return A map with all entries from this Quantity
     */
    Map<String, ? extends Object> toMap() {
        Map<String, Object> map = super.toMap() + [unit: unit, scale: scale]
        map['value'] = returnValue()
        return map
    }
}
