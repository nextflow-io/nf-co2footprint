package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CO2FootprintExtensionTest extends Specification {
    @Shared
    FileChecker fileChecker = new FileChecker()

    Session createSession() {
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_extension_test.txt')
        Path summaryPath = tempPath.resolve('summary_extension_test.txt')
        Path reportPath = tempPath.resolve('report_extension_test.html')

        return new Session(
            [ co2footprint:
                  [
                      ci: 100.0,
                      'traceFile': tracePath,
                      'summaryFile': summaryPath,
                      'reportFile': reportPath
                  ]
            ]
        )
    }

    def 'Should calculate the CO2Footprint from an old trace file'() {
        given:
        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        List<CO2Record> co2Records = extension.calculateCO2(
                this.class.getResource('/execution-trace-test.txt').path as Path, [:]
        )

        then:
        co2Records.size() == 8
        co2Records[7] == new CO2Record(
                3.2729169285E-6, 3.2729169285E-4, null, 2.777778E-4, 100.0,
                1, 11.41, 100.0, 1, 'VALUE_TESTING', null
        )
        // Check whether all files exist
        ['trace', 'summary',  'report'].each { String fileType ->
            Path filePath = Path.of(extension.factory.config.value(fileType + 'File') as String)
            fileChecker.checkIsFile(filePath)
        }
    }

    def 'Should modify the output paths'() {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')

        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        List<CO2Record> co2Records = extension.calculateCO2(
                this.class.getResource('/execution-trace-test.txt').path as Path, [traceFile: tracePath]
        )

        then:
        co2Records.size() == 8
        co2Records[7] == new CO2Record(
                3.2729169285E-6, 3.2729169285E-4, null, 2.777778E-4, 100.0,
                1, 11.41, 100.0, 1, 'VALUE_TESTING', null
        )
        fileChecker.checkIsFile(tracePath)
    }
}
