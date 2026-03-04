package nextflow.co2footprint

import nextflow.co2footprint.TestHelpers.FileChecker
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

class CO2FootprintCLITest extends  Specification {
    @Shared
    FileChecker fileChecker = new FileChecker()

    private Path outPath = Path.of(this.class.getResource('.').toURI()).complete().resolve('cli').resolve('out')
    private File outputDirectory = outPath.toFile()

    List<Path> outPaths = [
            outPath.resolve('co2footprint_trace_test.txt'),
            outPath.resolve('co2footprint_summary_test.txt'),
            outPath.resolve('co2footprint_report_test.html'),
            outPath.resolve('co2footprint_data_test.yaml')
    ]

    def cleanup() {
        outputDirectory.deleteDir()
    }

    def 'test CLI post run'() {
        setup:
       List<String> checksums = ['e189a632eb5ce166d07ba61da3df0a60', '5f3dbf6bef9404c10d99901f562e7632', '64baeb9ff7bf4f12afc47d95d3f67a88', '43d792fc7cc3e031196e08f30a190ca2']
        when:
        Map<String, Object> parsedArgs = [
                tracePath: Path.of(this.class.getResource('/execution-trace-raw.tsv').toURI()).complete().toString(),
                config: Path.of(this.class.getResource('/cli/test.config').toURI()).complete().toString()
        ]
        int exitCode = CO2FootprintCLI.postRun(parsedArgs)

        then:
        exitCode == 0

        List<String> checksumErrors = []
        for(int i = 0; i < outPaths.size(); i++) {
            Path outPath = outPaths[i]
            fileChecker.checkIsFile(outPath)
            try {
                fileChecker.compareChecksums(outPath, checksums[i])
                checksumErrors.add(null)
            }
            catch (AssertionError e) {
                checksumErrors.add(e.message)
            }
        }

        checksumErrors== [null, null, null, null]

    }
}
