package nextflow.co2footprint.FileCreation

import groovyx.gpars.agent.Agent
import nextflow.co2footprint.Config.ProvenanceFileConfig
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.trace.TraceHelper
import groovy.json.JsonBuilder

import java.time.Duration
import java.time.Instant

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
    private Map transformToJsonLd(Map<String, Object> treeMap, boolean root=true) {
        Map ldMap = [:]

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
                            '@type': 'schema:DateTime',
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
}
