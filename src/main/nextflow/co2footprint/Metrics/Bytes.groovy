package nextflow.co2footprint.Metrics

class Bytes extends Quantity {
    /**
     * Creator of Bytes, combining the tracking and reporting of a number, associated with a unit.
     *
     * @param value         Numerical value, saved in the quantity
     * @param scale         Scale of the Quantity, defaults to ''
     * @param type          Type of he value, defaults to 'Bytes'
     * @param description   A human-readable description of the value
     */
    Bytes(Object value, String scale='', String type='Bytes', String description = null) {
        super(value, scale, 'B', type, description)
        scalingFactor = 1024
    }

    /**
     * Creator of Bytes, combining the tracking and reporting of a number, associated with a unit.
     *
     * @param value         Numerical value, saved in the quantity
     * @param scale         Scale of the Quantity, defaults to ''
     * @param type          Type of he value
     * @param description   A human-readable description of the value
     */
    static Metric of(Object value, String scale='', String type='Bytes', String description = null) {
        if (value instanceof Number) {
            return new Bytes(value, scale, type, description)
        }
        else {
            return new Metric(value, type, "${scale}B", description)
        }
    }

}
