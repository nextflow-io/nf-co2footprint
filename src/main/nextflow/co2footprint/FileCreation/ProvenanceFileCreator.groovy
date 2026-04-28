package nextflow.co2footprint.FileCreation

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.Config.ProvenanceFileConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.trace.TraceHelper

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

@Slf4j
class ProvenanceFileCreator extends BaseFileCreator {
    // Agent for thread-safe writing to the data file
    private Agent<PrintWriter> dataWriter

    // Whether or not only to write emission metrics
    private boolean emissionMetricsOnly = false

    /**
     * Constructor for the data/machine-readable file.
     *
     * @param config A {@link ProvenanceFileConfig} that defines the created file.
     */
    ProvenanceFileCreator(ProvenanceFileConfig config) {
        super(config)

        emissionMetricsOnly = config.emissionMetricsOnly

        if(!config.enabled) {
            this.metaClass.create = { -> null }
            this.metaClass.write = { CO2RecordTree X -> null }
            this.metaClass.close = {  -> null }
        }
    }

    /**
     * Create the data/machine-readable file.
     */
    void create() {
        super.create()

        writer = TraceHelper.newFileWriter(path, overwrite, 'co2footprintdata')
        file = new PrintWriter(writer)
    }

    /**
     * Write the data/machine-readable file with totals and options.
     *
     * @param co2RecordTree A hierarchically structured record tree
     */
    void write(CO2RecordTree co2RecordTree) {
        Map co2TreeMap = transformToJsonLd(co2RecordTree.toMap(emissionMetricsOnly, false, false))
        JsonBuilder jsonBuilder = new JsonBuilder(co2TreeMap)

        dataWriter = new Agent<PrintWriter>(file)

        file.print(jsonBuilder.toPrettyString())
        file.flush()
    }

    /**
     * Transform the map to JSON-LD format with schema.org/bioschema.org context and types.
     *
     * @param original The original map from CO2RecordTree.toMap()
     * @return The transformed map with JSON-LD elements
     */
    private Map<String, Object> transformToJsonLd(Map<String, Object> treeMap, boolean root=true) {
        Map<String, Object> ldMap = [:]

        // Add @context at root level
        if(root) {
            ldMap['@context'] = [
                    schema    : 'https://schema.org/',
                    bioschemas: 'https://bioschemas.org',
            ]
        }

        // Add @id
        ldMap['@id'] = "urn:co2footprint:${treeMap.name}"

        // Add @type based on metaData.level
        ldMap['@type'] = switch (treeMap.metaData?.level) {
            case 'session' -> 'schema:SoftwareApplication'
            case 'workflow' -> 'bioschemas:ComputationalWorkflow'
            case 'process' -> 'schema:SoftwareApplication'
            case 'task' -> 'schema:Action'
            default -> 'schema:Thing'
        }

        // Transform values to include @type for emissions
        if (treeMap.values) {
            for (Map.Entry<String, Object> entry: (treeMap.values as Map).entrySet()){
                String key = entry.getKey()
                Object value = entry.getValue()

                // Only record value with content
                if ( !(value instanceof Map && value.raw instanceof Map) ){ continue }
                Map<String, Object> raw = value.raw as Map

                // Adjust record according to type
                if (raw.type == 'str') {
                    ldMap[key] = [
                            '@type': 'schema:PropertyValue',
                            'value': raw.value,
                    ]
                }
                else if (raw.type == 'list') {
                    List<Map<String, Object>> itemElements = []
                    itemElements(raw.value as List).eachWithIndex { Object item, int index ->
                        itemElements.add(
                                ['@type': 'schema:ListItem',
                                'position': index + 1,
                                'item': [
                                      '@type': 'schema:PropertyValue',
                                      'value': item
                                    ]
                                ]
                        )
                    }
                    ldMap[key] = [
                            '@type': 'schema:ItemList',
                            'itemListElement': itemElements,
                            'numberOfItems': itemElements.size()
                    ]
                }
                else if (raw.type as String in ['Number', 'Percentage', 'Bytes']) {
                    ldMap[key] = [
                            '@type': 'schema:QuantitativeValue',
                            'value': raw.value,
                            'unitText': raw.scale + raw.unit
                    ]
                }
                else if (raw.type == 'Duration') {
                    ldMap[key] = [
                            '@type': 'schema:Duration',
                            'value': Duration.ofMillis(raw.value as Long).toString(),
                            'unitText': raw.scale + raw.unit

                    ]
                }
                else if (raw.type == 'DateTime') {
                    ldMap[key] = [
                            '@type': 'schema:DataTime',
                            'value':  Instant.ofEpochMilli(raw.value as Long).toString() ,
                            'unitText': raw.scale + raw.unit
                    ]
                }

                // Add readable representation as additional property
                ldMap[key] += [
                        additionalProperty: [
                                'readable': value.readable
                        ]
                ]

                // Add optional description
                if (raw.get('description', null)) {
                    ldMap[key] += [
                            description: raw.description
                    ]
                }
            }
        }

        // Recursively transform children
        List<Map<String, Object>> children = treeMap?.children as List<Map<String, Object>>?: []
        if (children) {
            ldMap['hasPart'] = children.collect { Map<String, Object> child ->
                    transformToJsonLd(child, false)
            }
        }

        return ldMap
    }

    static CO2RecordTree read(Path path) {
        String jsonString = path.toFile().text
        Map<String, Object> jsonMap = new JsonSlurper().parseText(jsonString) as Map<String, Object>
        return readJsonLd(jsonMap)
    }

    /**
     * Recursively read a JSON-LD map and transform it back to the original CO2RecordTree map structure.
     * This is the inverse of transformToJsonLd, and assumes the same JSON-LD structure as produced by that function.
     *
     * @param treeMap The JSON-LD map to transform back to the original structure
     * @param root Whether this is the root level of the tree, used to determine the level of the root node
     */
    static CO2RecordTree readJsonLd(Map<String, Object> ldMap, boolean root=true) {

        // Extracts children recursively
        List<CO2RecordTree> children = []
        if (ldMap.containsKey('hasPart')){
            children = (ldMap.remove('hasPart') as List<Map<String, Object>>).collect { Map<String, Object> childLdMap ->
                readJsonLd(childLdMap, false)
            }
        }

        // Constructs CO2Record
        Map<String, Object> store = [:]
        for (Map.Entry<String, Object> entry: (ldMap).entrySet()){
            String key = entry.getKey()
            Object value = entry.getValue()

            // Skip JSON-LD elements
            if (key in ['@context', '@id', '@type'] || !(value instanceof Map && value.containsKey('value')) ) {
                continue
            }

            // Only record value with content


            // Adjust record according to type
            if (value['@type'] == 'schema:PropertyValue') {
                 store[key] = value['value']
            }
            else if (value['@type'] == 'schema:QuantitativeValue'){
                store[key] = value['value']
            }
            else if (value['@type'] == 'schema:Duration') {
                store[key] = Duration.parse(value['value'] as String).toMillis()
            }
            else if (value['@type'] == 'schema:DataTime') {
                store[key] = Instant.parse(value['value'] as String).toEpochMilli()
            }
            else if(value['@type'] == 'schema:ItemList') {
                List<Object> items = (value['itemListElement'] as List<Map<String, Object>>).collect { Map<String, Object> itemElement ->
                    itemElement['item']['value']
                }
                store[key] = items
            }
            else {
                String errorMessage = "Unknown @type ${value['@type']} for key ${key}, skipping value."
                log.error(errorMessage)
                throw new IllegalArgumentException(errorMessage)
            }
        }
        CO2Record co2Record = new CO2Record(store)

        // Construct a new tree with the name extracted from the @id (removing the "urn:co2footprint:" prefix)
        CO2RecordTree co2RecordTree = new CO2RecordTree((ldMap['@id'] as String).drop(17), [:], co2Record, null, children)


        // Add @type based on metaData.level
        if (ldMap['@type'] == 'schema:SoftwareApplication' && root) {
            co2RecordTree.metaData['level'] = 'session'
        }
        else {
            co2RecordTree.metaData['level'] = switch (ldMap['@type']) {
                case 'bioschemas:ComputationalWorkflow' -> 'workflow'
                case 'schema:SoftwareApplication' -> 'process'
                case 'schema:Action' -> 'task'
                default -> 'unknown'
            }
        }

        return co2RecordTree
    }
}
