package nextflow.co2footprint

import nextflow.co2footprint.utils.HelperFunctions

class CO2EquivalencesRecord {
    Double carKilometers = null
    Double treeMonths = null
    Double planePercent = null

    CO2EquivalencesRecord(Double carKilometers=null, Double treeMonths=null, Double planePercent=null) {
        this.carKilometers = carKilometers
        this.treeMonths = treeMonths
        this.planePercent = planePercent
    }

    List<String> getReadable() {
        List<String> readableEquivalences = new ArrayList<String>()

        String outStr = ''
        this.getProperties().each {key, value ->
            switch (key as String) {
                case 'carKilometers' ->
                    outStr = "- ${HelperFunctions.convertToScientificNotation(carKilometers)} km travelled by car"
                case 'treeMonths' ->
                    outStr = "- Monthly co2 absorption of ${HelperFunctions.convertToScientificNotation(treeMonths)} trees"
                case 'planePercent' ->
                    if (value < 100) {
                        outStr = "- ${HelperFunctions.convertToScientificNotation(planePercent)}% of a flight from paris to london"
                    }
                    else {
                        outStr = "- ${HelperFunctions.convertToScientificNotation(planePercent / 100 as Integer)} flights from paris to london"
                    }
            }
            readableEquivalences.add(outStr)
        }

        return  readableEquivalences
    }
}
