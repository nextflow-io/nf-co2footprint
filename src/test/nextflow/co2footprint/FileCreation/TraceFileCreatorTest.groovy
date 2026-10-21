package nextflow.co2footprint.FileCreation


import nextflow.co2footprint.Config.TraceFileConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.trace.TraceRecord
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class TraceFileCreatorTest extends Specification {
    def 'Correctly convert execution trace values to '() {
        given:
        Path tempDir = Files.createTempDirectory('trace-file-test')
        Path outputPath = tempDir.resolve('trace_file_test.tsv')
        TraceFileCreator creator = new TraceFileCreator(
                new TraceFileConfig([enabled: true, file: outputPath])
        )

        TraceRecord traceRecord = new TraceRecord()
        traceRecord.putAll([
                task_id: '1',
                status: 'COMPLETED',
                name: 'task',
                submit: 1759849601467L,
                start: 1759849601467L,
                complete: 1759849601467L,
                realtime: 0,
                duration: 0,
                hash: '123',
                native_id: '1_ID',
                name: 'ONE',
                exit: 0,
                '%cpu': 90.0,
                peak_rss: 1024,
                peak_vmem: 1024,
                rchar: 1024,
                wchar: 1024,
                cpus: 1,
                memory: 1024,
                process: 'PROCESS',
                hostname: 'XYZ',
                cpu_model: 'MODEL',
                disk: 'DISK',
                read_bytes: 1024,
                write_bytes: 1024
        ])
        CO2Record co2Record = new CO2Record(traceRecord.store)
        
        String header = String.join('\t', TraceFileCreator.traceMetrics)
        String expected_content = String.join(
                '\t',
                ['1', 'COMPLETED', 'ONE', '-', '-', '-', '-', '-', '-', '90 %', '1.02 TB', '0ms', '1', '-', 'MODEL']
        )
        String expected_output = String.join('\n', [header, expected_content]) + '\n'

        when:
        creator.create()
        creator.write(co2Record)
        creator.close()
        String output = outputPath.text

        then:
        output == expected_output
    }
}
