package nextflow.co2footprint

import nextflow.co2footprint.utils.Converter

/**
 * Stores equivalence values for CO₂ emissions:
 *
 * - Kilometers by car
 * - Months for a tree to sequester
 * - Percent of a Paris-London flight
 */
class CO2EquivalencesRecord {
    private final Double carKilometers
    private final Double treeMonths
    private final Double planePercent

    /**
     * Create a record of CO2 Equivalences
     *
     * @param carKilometers  Distance in km
     * @param treeMonths     Months for a tree
     * @param planePercent   Percent of a flight
     */
    CO2EquivalencesRecord(Double carKilometers=null, Double treeMonths=null, Double planePercent=null) {
        this.carKilometers = carKilometers
        this.treeMonths = treeMonths
        this.planePercent = planePercent
    }

    Double getCarKilometers() { carKilometers }
    String getCarKilometersReadable() { Converter.toScientificNotation(carKilometers) }

    Double getTreeMonths() { treeMonths }
    String getTreeMonthsReadable() { Converter.toReadableTimeUnits(treeMonths, 'months', 's', 'years', 0) }

    Double getPlanePercent() { planePercent }
    String getPlanePercentReadable() { Converter.toScientificNotation(planePercent) }

    Integer getPlaneFlights() { planePercent / 100 as Integer }
    String getPlaneFlightsReadable() { Converter.toScientificNotation(this.getPlaneFlights()) }

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
                    outStr = "- It takes one tree ${this.getTreeMonthsReadable()} to sequester the equivalent amount of CO2 from the atmosphere"
                case 'planePercent' ->
                    if (value < 100) {
                        outStr = "- ${this.getPlanePercentReadable()}% of a flight from Paris to London"
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