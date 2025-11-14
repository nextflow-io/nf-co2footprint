package nextflow.co2footprint.Metrics

class Duration extends Quantity {
    /**
     * Creator of a Time quantity, combining the tracking and reporting of a duration, associated with a unit.
     *
     * @param value         Numerical value, saved in the quantity
     * @param unit          Unit of the quantity
     * @param type          Type of he value
     * @param description   A human-readable description of the value
     */
    Duration(Object value, String unit='ms', String type='Duration', String description = null) {
        super(value, '', unit, type, description)
        separator = ''
    }

    /**
     * Creator of a Time quantity, combining the tracking and reporting of a duration, associated with a unit.
     *
     * @param value         Numerical value, saved in the quantity
     * @param unit          Unit of the quantity
     * @param type          Type of he value
     * @param description   A human-readable description of the value
     */
    static Metric of(Object value, String unit='ms', String type='Duration', String description = null) {
        if (value instanceof Number) {
            return new Duration(value, unit, type, description)
        }
        else {
            return new Metric(value, type, unit, description)
        }
    }

    /**
     * Converts a duration from one unit to another.
     * E.g., 120000 ms to minutes returns 2.
     *
     * @param targetUnit Unit of time to be converted to (e.g. 'min')
     * @return           Number of converted time as BigDecimal
     */
    @Override
    Duration scale(String targetUnit='s') {
        if (value) {
            // Supported time units
            final List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']
            // Conversion factors between units (e.g., 1000 ms = 1 s, 60 s = 1 min, etc.)
            final List<BigDecimal> steps = [1000.0, 1000.0, 1000.0, 60.0, 60.0, 24.0, 7.0, 4.35, 12.0]

            int from = units.indexOf(unit)
            int to = units.indexOf(targetUnit)

            // Convert up (e.g., ms to min)
            if (to > from) {
                steps.subList(from, to).each { BigDecimal it -> value /= it }
            // Convert down (e.g., min to ms)
            } else if (to < from) {
                steps.subList(to, from).each { BigDecimal it ->  value *= it }
            }

            // Remove 's' for single time units
            if (value == 1 && ['days', 'weeks', 'months', 'years'].contains(targetUnit)) {
                targetUnit = targetUnit.dropRight(1)
            }
        }

        unit = targetUnit
        return this
    }

    /**
     * Converts a time value to a human-readable string, e.g. "2 days 3 h 4 min".
     * Recursively breaks down the value into the largest possible units.
     *
     * @param value The time as a number in original given unit
     * @param unit Given unit of time (default: 'ms')
     * @param smallestUnit The smallest unit to convert to (default: 's')
     * @param largestUnit The largest unit to convert to (default: 'years')
     * @param threshold The minimum value for the conversion to be included in the output (optional)
     * @return A human-readable string representation of the time value
     */
    String toReadable(
            String smallestUnit = 's',
            String largestUnit = 'years',
            Double threshold = 0.0
    ) {
        BigDecimal value = new BigDecimal(value)
        // Ordered list of supported time units
        List<String> units = ['ns', 'mus', 'ms', 's', 'min', 'h', 'days', 'weeks', 'months', 'years']

        // Filter out excluded time scales and sort form largest to smallest
        units = units.subList(getIdx(smallestUnit, units), getIdx(largestUnit, units) + 1).reverse()

        // Iterate from largest to smallest unit and include those that meet the threshold size to be included
        String timeString = ''
        for (String targetUnit : units) {
            Duration currentTime = new Duration(value, unit).scale(targetUnit)

            // Handle the last unit differently
            if( targetUnit == smallestUnit ) {
                currentTime.round()     // Keep last two decimals

                timeString += "${currentTime.getReadable()} "
            } else {
                currentTime.floor()     // Keep only round numbers

                // Add to string and remove added value, if the threshold is reached
                if ( (threshold == null || currentTime.value > threshold) ) {
                    value -= new Duration(currentTime.value, targetUnit).scale(unit).value
                    timeString += "${currentTime.getReadable()} "

                    // Finish execution if value is 0
                    if (value == 0) { break }
                }
            }
        }

        return timeString.trim()
    }
}
