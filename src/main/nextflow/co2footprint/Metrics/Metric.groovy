package nextflow.co2footprint.Metrics

import groovy.util.logging.Slf4j

/**
 * A class to track metrics through the run.
 */
@Slf4j
class Metric<T> {
    T value
    String type
    String unit
    String description

    /**
     * A new Metric for a data entry.
     *
     * @param value         The value, saved in the metric
     * @param type          The type of the value (e.g. String)
     * @param unit          Unit of the metric
     * @param description   A human-readable description of the metric
     */
    Metric(T value, String type = null, String unit=null, String description = null) {
        type ?= value?.class?.getSimpleName()
        this.value = value
        this.type = type
        this.unit = unit
        this.description = description
    }

    /**
     * Checks whether a element was found in a list and throws an error if not.
     *
     * @param element The element to search for
     * @param list The list in which to search
     * @return The index of the element in the list
     */
    static int getIdx(Object element, List<Object> list) {
        int idx = list.indexOf(element)

        if (idx < 0) {
            String message = "Element `${element}` not found in list `${list}`"
            log.error(message)
            throw new IllegalArgumentException(message)
        }

        return idx
    }

    Metric scale(String target=null) { return this }

    String getReadable() {
        return value as String
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = [value: value, type: type]
        if (unit) { map.put('unit', unit) }
        if (description) { map.put('description', description) }
        return map
    }
}
