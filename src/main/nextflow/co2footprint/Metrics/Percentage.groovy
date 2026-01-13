package nextflow.co2footprint.Metrics

import java.math.RoundingMode

class Percentage extends Quantity {
    /**
     * Creator of Percentage, a Quantity which represents a percentage.
     *
     * @param value         Numerical value.
     * @param unit          Unit of the value.
     * @param type          The datatype label. Defaults to 'Percentage'.
     * @param description   A human-readable description of the value.
     */
    Percentage(Object value, String unit='', String type='Percentage', String description = null) {
        super(value, '%', unit, type, description)
        scalingFactor = 1024
    }

    /**
     * Creates a {@link Metric} or a {@link Percentage}, if the value is numerical.
     *
     * @param value         The raw value
     * @param unit          Unit of the quantity.
     * @param type          The type label for the metric.
     * @param description   A human-readable description of the value.
     * @return              A Percentage or Metric object depending on the type of `value`.
     */
    static Metric of(Object value, String unit='', String type='Percentage', String description = null) {
        if (value instanceof Number) {
            return new Percentage(value, unit, type, description)
        }
        else {
            return new Metric(value, type, "%${unit}", description)
        }
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
     * @param precision Decimal places to round to, default of 2
     * @return Scaled value as a formatted String with appropriate scale and rounding
     */
    String toReadable(Integer precision=2) {
        return this.round(precision).getReadable()
    }
}
