package nextflow.co2footprint.Metrics

/**
 * Utility functions for aggregating workflow metrics.
 * Provides null-safe max, add, and weighted average.
 */
class Calculator {
    /**
     * Return the maximum of two objects.
     * 
     * @param o1 First Object
     * @param o2 Second Object
     * @return The maximum of both objects
     */
    static max(Object o1, Object o2) {
        return (o1 > o2) ? o1 : o2
    }

    /**
     * Add two values, treating null as zero/ignored.
     *
     * @param o1 First Object
     * @param o2 Second Object
     * @return The sum of both objects
     */
    static Object add(Object o1, Object o2) {
        if (o1 == null && o2 == null) { return null }
        else if (o1 == null) { return o2 }
        else if (o2 == null) { return o1 }
        else { return o1 + o2 }
    }

    /**
     * Compute the weighted average of a list of values given a corresponding list of weights.
     * Null entries are removed before computation.
     *
     * @param values list of numeric values (nullable entries removed)
     * @param weights list of weights aligned with values (nullable entries removed)
     * @return weighted average as a Number, or null if inputs are empty
     */
    static Number weightedAverage(List<? extends Object> values, List<? extends Object> weights) {
        values.removeAll([null])
        weights.removeAll([null])
        assert values.size() == weights.size()

        if (values.isEmpty() || weights.isEmpty()) { return null }

        Number norm = weights.sum() as Number
        Number total = new BigDecimal(0)
        values.eachWithIndex { Object value, Integer index ->
            total += value * weights[index]
        }
        return total / norm
    }
}
