package nextflow.co2footprint

import nextflow.co2footprint.utils.HelperFunctions

class CO2EquivalencesRecord {
    private final Double carKilometers
    private final Double treeMonths
    private final Double planePercent

    CO2EquivalencesRecord(Double carKilometers=null, Double treeMonths=null, Double planePercent=null) {
        this.carKilometers = carKilometers
        this.treeMonths = treeMonths
        this.planePercent = planePercent
    }

    Double getCarKilometers() { carKilometers }
    String getCarKilometersReadable() { HelperFunctions.convertToScientificNotation(carKilometers) }

    Double getTreeMonths() { treeMonths }
    String getTreeMonthsReadable() { HelperFunctions.convertToScientificNotation(treeMonths) }

    Double getPlanePercent() { planePercent }
    String getPlanePercentReadable() { HelperFunctions.convertToScientificNotation(planePercent) }

    Integer getPlaneFlights() { planePercent / 100 as Integer }
    String getPlaneFlightsReadable() { HelperFunctions.convertToScientificNotation(this.getPlaneFlights()) }

    List<String> getReadableEquivalences() {
        List<String> readableEquivalences = new ArrayList<String>()

        String outStr = ''
        ['carKilometers', 'treeMonths', 'planePercent'].each{ property ->
            def value = this.getProperty(property)
            switch (property as String) {
                case 'carKilometers' ->
                    outStr = "- ${this.getCarKilometersReadable()} km travelled by car"
                case 'treeMonths' ->
                    outStr = "- Monthly co2 absorption of ${this.getTreeMonthsReadable()} trees"
                case 'planePercent' ->
                    if (value < 100) {
                        outStr = "- ${this.getPlanePercentReadable()}% of a flight from paris to london"
                    }
                    else {
                        outStr = "- ${this.getPlaneFlightsReadable()} flights from paris to london"
                    }
            }
            readableEquivalences.add(outStr)
        }

        return  readableEquivalences
    }
}
