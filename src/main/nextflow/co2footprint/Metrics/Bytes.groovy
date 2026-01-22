package nextflow.co2footprint.Metrics

class Bytes extends Quantity {
    /**
     * Creator of Bytes, a Quantity which represents a number in bytes 
     *
     * @param value         The numeric value.
     * @param scale         The unit scale (e.g. '', 'K', 'M'). Defaults to ''.
     * @param type          The datatype label. Defaults to 'Bytes'.
     * @param description   Optional human-readable description of the value.
     */
    Bytes(Object value, String scale='', String type='Bytes', String description = null) {
        super(value, scale, 'B', type, description)
        scalingFactor = 1024
    }

    /**
     * Creates a {@link Metric} or a {@link Bytes}, if the value is numerical.
     *
     * @param value         The raw value.
     * @param scale         The unit scale (e.g. '', 'K', 'M'). Defaults to ''.
     * @param type          The type label for the metric. Defaults to 'Bytes'.
     * @param description   Optional human-readable description of the metric.
     * @return              A Bytes or Metric object depending on the input value.
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
