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
        Path provenancePath = tempPath.resolve('provenance_extension_test.yaml')

        return new Session(
            [ co2footprint:
                  [
                      ci: 100.0,
                      trace: [file: tracePath],
                      summary: [file: summaryPath],
                      report: [file: reportPath],
                      provenance: [file: provenancePath, enabled: true]
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
        CO2FootprintExtension.Output output = extension.calculateCO2(
                this.class.getResource('/execution-trace-regular.tsv').path as Path, [:]
        )

        then:
        output.co2Records.size() == 8
        output.co2Records[7].getReadableEntries() == ['VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '100 %', '1 GB', '1s 0ms', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh']
        output.co2Records[7].additionalMetrics == [co2e_non_cached:3.2729169285E-4, energy_non_cached:3.2729169285E-6, co2e_market:null, energy_market:3.2729169285E-6]

        // Check whether all files exist
        fileChecker.checkIsFile(output.config.trace.file)
        fileChecker.checkIsFile(output.config.summary.file)
        fileChecker.checkIsFile(output.config.report.file)
        fileChecker.checkIsFile(output.config.provenance.file)
    }

    def 'Should modify the output paths'() {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')

        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        CO2FootprintExtension.Output output = extension.calculateCO2(
                this.class.getResource('/execution-trace-regular.tsv').path as Path, [trace: [file: tracePath]]
        )

        then:
        output.co2Records.size() == 8
        output.co2Records[7].getReadableEntries() == ['VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '100 %', '1 GB', '1s 0ms', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh']
        output.co2Records[7].additionalMetrics == [co2e_non_cached:3.2729169285E-4, energy_non_cached:3.2729169285E-6, co2e_market:null, energy_market:3.2729169285E-6]
        fileChecker.checkIsFile(tracePath)
    }
}
