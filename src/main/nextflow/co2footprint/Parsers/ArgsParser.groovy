package nextflow.co2footprint.Parsers

/**
 * Parse command line arguments for the plugin
 */
class ArgsParser {
    /**
     * Determine the value for an argument, depending on the number of values as a list, single entry or true.
     *
     * @param values A list of value that was behind an argument key
     * @return The determined entry for the key
     */
    static def setValue(List<String> values) {
        return switch (values.size()) {
            case 0 -> true
            case 1 -> values[0]
            default -> values
        }
    }

    /**
     * Parse the arguments from a list of Strings. `['--a', '1', 'b', --b, '5', '--c']` results in [a: ['1', 'b'], b: '5', c: true].
     *
     * @param args A list of Strings from a command line
     * @return A map with all entries
     */
    static Map<String, Object> parse(List<String> args) {
        Map<String, Object> result = [:]
        String key = null
        List<String> values = []

        args.each { String arg ->
            if (arg.startsWith("--")) {
                // Store previous key if any
                if (key) { result[key] = setValue(values) }

                // Start new key
                key = arg.substring(2)
                values = []
            }
            else { values.add(arg) }
        }

        // Handle last key
        if (key) { result[key] = setValue(values) }

        return result
    }

}
