package nextflow.co2footprint.utils

class HelperFunctions {

    // Helper function to return bold text
    static String bold(String text) {
        return "\033[1m${text}\033[0m"
    }

}