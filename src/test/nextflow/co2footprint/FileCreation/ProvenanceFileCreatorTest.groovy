package nextflow.co2footprint.FileCreation

import nextflow.co2footprint.Config.ProvenanceFileConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.trace.TraceRecord
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class ProvenanceFileCreatorTest extends Specification {

    def 'Test reading a provenance JSON-LD String' () {
        given:
        Path jsonLdPath = Path.of(this.class.getResource('/observer/provenance_test.json').toURI())
        CO2RecordTree expectedTaskTree = new CO2RecordTree(
                '111',
                [level: 'task'],
                new CO2Record([
                        task_id: '111',
                        status: 'COMPLETED',
                        energy_consumption: 14.0175 as BigDecimal,
                        CO2e: 6.7284 as BigDecimal,
                        carbon_intensity: 480.0 as BigDecimal,
                        '%cpu': 100.0 as BigDecimal,
                        memory: (7 as Long) * (1024**3 as Long) , // 7 GB
                        realtime: (1 as Long) * (3600000 as Long), // 1 h
                        cpus: 1,
                        powerdraw_cpu: 11.41 as BigDecimal,
                        cpu_model: "Unknown model",
                        raw_energy_processor: 11.41 as BigDecimal,
                        raw_energy_memory: 2.6075 as BigDecimal,
                        pue: 1.0,
                        powerdraw_memory: 0.3725,
                ]),
        )
        CO2RecordTree expectedProcessTree = new CO2RecordTree('observerTestProcess', [level: 'process'], null, null, [expectedTaskTree])
        CO2RecordTree expectedWorkflowTree = new CO2RecordTree('null', [level: 'workflow'], null, null, [expectedProcessTree])
        expectedWorkflowTree.summarize()


        when:
        CO2RecordTree provenanceTree = ProvenanceFileCreator.read(jsonLdPath)

        then:
        expectedWorkflowTree.toMap(true, false, false) == provenanceTree.toMap(true, false, false)
    }

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
