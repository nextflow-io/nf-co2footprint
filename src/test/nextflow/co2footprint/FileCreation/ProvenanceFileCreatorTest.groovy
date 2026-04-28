package nextflow.co2footprint.FileCreation

import nextflow.co2footprint.Config.ProvenanceFileConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.trace.TraceRecord
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class ProvenanceFileCreatorTest extends Specification {
    def 'writes DateTime values with schema DateTime type'() {
        given:
        Path tempDir = Files.createTempDirectory('provenance-test')
        Path outputPath = tempDir.resolve('provenance_test.json')
        ProvenanceFileCreator creator = new ProvenanceFileCreator(
            new ProvenanceFileConfig([enabled: true, file: outputPath, emissionMetricsOnly: false])
        )

        TraceRecord traceRecord = new TraceRecord()
        traceRecord.putAll([
            task_id: '1',
            status: 'COMPLETED',
            name: 'task',
            submit: 1759849601467L
        ])
        CO2Record co2Record = new CO2Record(traceRecord.store)
        CO2RecordTree recordTree = new CO2RecordTree('task', [level: 'task'], co2Record)

        when:
        creator.create()
        creator.write(recordTree)
        creator.close()
        String output = outputPath.text

        then:
        output.contains('"@type": "schema:DateTime"')
        !output.contains('"@type": "schema:DataTime"')
    }
}
