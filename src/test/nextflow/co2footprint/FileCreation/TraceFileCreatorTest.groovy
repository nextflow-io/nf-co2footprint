package nextflow.co2footprint.FileCreation

import nextflow.co2footprint.Config.TraceFileConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.trace.TraceRecord
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

class TraceFileCreatorTest extends Specification {

    @Unroll
    def 'Trace file reports realtime of #realtime ms as "#readable"'() {
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
                realtime: realtime,
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
        
        Map<String, String> expectedValues = [
                task_id: '1', status: 'COMPLETED', name: 'ONE', '%cpu': '90 %',
                memory: '1.02 TB', realtime: readable, cpus: '1', cpu_model: 'MODEL'
        ]
        String expectedHeader = TraceFileCreator.traceMetrics.join('\t')
        String expectedContent = TraceFileCreator.traceMetrics.collect({ String tm -> expectedValues.get(tm, '-')} ).join('\t')
        String expectedOutput = [expectedHeader, expectedContent, ''].join('\n')

        when:
        creator.create()
        creator.write(co2Record)
        creator.close()
        String output = outputPath.text

        then:
        output == expectedOutput
        
        cleanup:
        tempDir.toFile().deleteDir()
        
        where:
        realtime    | readable
        0           | '0ms'
        1500        | '1s 500ms'
    }
}
