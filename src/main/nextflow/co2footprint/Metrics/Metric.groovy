package nextflow.co2footprint.Metrics

class Metric {
    Object value
    String type
    String format

    /**
     * Creator of a Metric, to track non number metrics.
     *
     * @param value The value, saved in the metric
     */
    Metric(Object value, String type = value.class.getSimpleName(), String format = null) {
        this.value = value
        this.type = type
        this.format = format
    }

    String getReadable() {
        return value as String
    }

    Map<String, Object> toMap() {
        return format ? [value: value, type: type, format: format] : [value: value, type: type]
    }
}
