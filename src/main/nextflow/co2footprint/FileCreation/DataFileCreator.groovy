package nextflow.co2footprint.FileCreation

import groovyx.gpars.agent.Agent
import nextflow.co2footprint.Config.DataFileConfig
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.trace.TraceHelper
import org.yaml.snakeyaml.Yaml

class DataFileCreator extends BaseFileCreator {
    // Yaml string creator
    Yaml yaml = new Yaml()

    // Agent for thread-safe writing to the data file
    private Agent<PrintWriter> dataWriter

    /**
     * Constructor for the data/machine-readable file.
     *
     * @param config A {@link DataFileConfig} that defines the created file.
     */
    DataFileCreator(DataFileConfig config) {
        super(config)

        if(!config.enabled) {
            this.metaClass.create = { -> null }
            this.metaClass.write = { -> null }
            this.metaClass.close = { -> null }
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
        Map co2TreeMap = co2RecordTree.toMap(true)
        String yamlString = yaml.dump(co2TreeMap)

        dataWriter = new Agent<PrintWriter>(file)

        file.print(yamlString)
        file.flush()
    }
}
