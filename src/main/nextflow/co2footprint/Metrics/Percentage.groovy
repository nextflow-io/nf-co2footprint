package nextflow.co2footprint.Metrics

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class Percentage extends Quantity {
    /**
     * Creator of a Quantity, combining the tracking and reporting of a number, associated with a unit.
     *
     * @param value         Numerical value, saved in the quantity
     * @param scale         Scale of the Quantity, defaults to ''
     * @param unit          Unit of the quantity
     * @param type          Type of he value
     * @param description   A human-readable description of the value
     */
    Percentage(Object value, String scale='%', String unit='', String type='Percentage', String description = null) {
        super(value, scale, unit, type, description)
        scalingFactor = 1024
    }

    /**
     * Converts a quantity into scientific notation (e.g., 1.23E3).
     *
     * @return String in scientific notation or rounded if in [0.001, 999]
     */
    String toScientificNotation() {
        return "${super.toScientificNotation()} %"
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
        return this.round(precision).getReadable()
    }
}
