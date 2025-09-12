package nextflow.co2footprint.Metrics

class Calculator {
    static max(Object o1, Object o2) {
        if (o1 > o2) {
            return  o1
        }
        return o2
    }

    static Number weightedAverage(List<? extends Object> values, List<? extends Object> weights) {
        assert values.size() == weights.size()
        Number norm = weights.sum() as Number
        Number total = new BigDecimal(0)
        values.eachWithIndex { Object value, Integer index ->
            total += value * weights[index]
        }
        return total / norm
    }
}
