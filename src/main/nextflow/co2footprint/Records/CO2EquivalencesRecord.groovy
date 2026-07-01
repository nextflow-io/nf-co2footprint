package nextflow.co2footprint.Records


import nextflow.co2footprint.Metrics.Duration
import nextflow.co2footprint.Metrics.Percentage
import nextflow.co2footprint.Metrics.Quantity

/**
 * Stores equivalence values for CO₂ emissions:
 *
 * - Kilometers by car
 * - Months for a tree to sequester
 * - Percent of a Paris-London flight
 */
class CO2EquivalencesRecord {
    private final BigDecimal carKilometers
    private final BigDecimal treeMonths
    private final BigDecimal planePercent

    /**
     * Create a record of CO₂ Equivalences
     *
     * @param carKilometers  Distance in km
     * @param treeMonths     Months for a tree
     * @param planePercent   Percent of a flight
     */
    CO2EquivalencesRecord(BigDecimal carKilometers=null, BigDecimal treeMonths=null, BigDecimal planePercent=null) {
        this.carKilometers = carKilometers
        this.treeMonths = treeMonths
        this.planePercent = planePercent
    }

    BigDecimal getCarKilometers() { carKilometers }
    String getCarKilometersReadable() { new Quantity(carKilometers).toScientificNotation() }

    BigDecimal getTreeMonths() { treeMonths }
    String getTreeMonthsReadable() { new Duration(treeMonths, 'months').toReadable('s', 'years', 0.0) }

    BigDecimal getPlanePercent() { planePercent }
    String getPlanePercentReadable() { new Percentage(planePercent).toScientificNotation() }

    Integer getPlaneFlights() { planePercent / 100 as Integer }
    String getPlaneFlightsReadable() { new Quantity(planeFlights).toScientificNotation() }

    /**
     * Returns a list of readable equivalence strings.
     */
    List<String> getReadableEquivalences() {
        List<String> readableEquivalences = new ArrayList<String>()

        String outStr = ''
        ['carKilometers', 'treeMonths', 'planePercent'].each{ property ->
            def value = this.getProperty(property)
            switch (property as String) {
                case 'carKilometers' ->
                    outStr = "- ${this.getCarKilometersReadable()} km travelled by car"
                case 'treeMonths' ->
                    outStr = "- It takes one tree ${this.getTreeMonthsReadable()} to sequester the equivalent amount of CO₂ from the atmosphere"
                case 'planePercent' ->
                    if (value < 100) {
                        outStr = "- ${this.getPlanePercentReadable()} of a flight from Paris to London"
                    }
                    else {
                        outStr = "- ${this.getPlaneFlightsReadable()} flights from Paris to London"
                    }
            }
            readableEquivalences.add(outStr)
        }

        return readableEquivalences
    }
}