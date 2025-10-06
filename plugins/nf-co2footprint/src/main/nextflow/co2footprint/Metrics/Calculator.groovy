package nextflow.co2footprint.Metrics

class Calculator {
    static max(Object o1, Object o2) {
        if (o1 > o2) {
            return  o1
        }
        return o2
    }

    static Object add(Object o1, Object o2) {
        if (o1 == null && o2 == null) { return null }
        else if (o1 == null) { return o2 }
        else if (o2 == null) { return o1 }
        else { return o1 + o2 }
    }

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
