package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.TestHelpers.FileChecker
import nextflow.trace.TraceRecord
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
                      trace: [file: tracePath],
                      summary: [file: summaryPath],
                      report: [file: reportPath]
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
                this.class.getResource('/execution-trace-regular.tsv').path as Path, [:]
        )

        then:
        co2Records.size() == 8
        co2Records[7].getReadableEntries() == ['VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '100 %', '1 GB', '1s 0ms', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh']
        co2Records[7].additionalMetrics == [co2e_non_cached:3.2729169285E-4, energy_non_cached:3.2729169285E-6, co2e_market:null, energy_market:3.2729169285E-6]

        // Check whether all files exist
        ['trace', 'summary',  'report'].each { String fileType ->
            Path filePath = Path.of(extension.factory.config.value(fileType).value('file') as String)
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
                this.class.getResource('/execution-trace-regular.tsv').path as Path, [trace: [file: tracePath]]
        )

        then:
        co2Records.size() == 8
        co2Records[7].getReadableEntries() == ['VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '100 %', '1 GB', '1s 0ms', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh']
        co2Records[7].additionalMetrics == [co2e_non_cached:3.2729169285E-4, energy_non_cached:3.2729169285E-6, co2e_market:null, energy_market:3.2729169285E-6]
        fileChecker.checkIsFile(tracePath)
    }
}
