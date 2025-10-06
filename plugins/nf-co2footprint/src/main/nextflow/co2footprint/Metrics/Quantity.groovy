package nextflow.co2footprint.Metrics

import java.math.RoundingMode

/**
 * A number associated with a unit.
 */
class Quantity extends Metric {
    BigDecimal value
    String scale
    String unit
    String separator

    /**
     * Creator of a Quantity, combining the tracking and reporting of a number, associated with a unit.
     *
     * @param value The numerical value, saved in the quantity
     * @param scale The scale of the Quantity, defaults to ''
     * @param unit The unit of the quantity
     * @param separator The separator between value and scaled unit, defaults to ' '
     */
    Quantity(Number value, String scale='', String unit='', String separator=' ') {
        super(value, value?.class?.getSimpleName() ?: 'Number')
        this.value = value as BigDecimal
        this.scale = scale
        this.unit = unit
        this.separator = separator
    }

    /**
     * Set the contents of a Quantity.
     *
     * @param value Number of the quantity
     * @param scale Scale of the unit
     * @param unit Unit string
     */
    void set(Number value, String scale=this.scale, String unit=this.unit) {
        this.value = value as BigDecimal
        this.scale = scale
        this.unit = unit
    }

    /**
     * Round the value to a certain precision.
     *
     * @param precision, default: 2
     * @param roundingMode, How to round the number, default: RoundingMode.HALF_UP
     */
    Quantity round(Integer precision=2, RoundingMode roundingMode=RoundingMode.HALF_UP) {
        if (precision != null) {
            this.value = this.value.setScale(precision, roundingMode)
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
            this.value = this.value.setScale(precision, RoundingMode.FLOOR)
        }

        return this
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

    Map<String, ? extends Object> toMap() {
        return super.toMap() + [scale: scale, unit: unit]
    }
}
