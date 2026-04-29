package nextflow.co2footprint

import nextflow.co2footprint.TestHelpers.FileChecker
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.nio.file.Path

@Stepwise
class CO2FootprintCLITest extends  Specification {
    @Shared
    FileChecker fileChecker = new FileChecker('/cli')

    private Path outPath = Path.of(this.class.getResource('.').toURI()).complete().resolve('cli').resolve('out')
    private File outputDirectory = outPath.toFile()

    List<Path> outPaths = [
            outPath.resolve('trace_test.txt'),
            outPath.resolve('summary_test.txt'),
            outPath.resolve('report_test.html'),
            outPath.resolve('provenance_test.json')
    ]

    def cleanup() {
        outputDirectory.deleteDir()
    }

    def 'test CLI post run'() {
        when:
        Map<String, Object> parsedArgs = [
                tracePath: Path.of(this.class.getResource('/cli/execution-trace-raw.tsv').toURI()).complete().toString(),
                config: Path.of(this.class.getResource('/cli/test.config').toURI()).complete().toString(),
                delimiter: '\t'
        ]

        int exitCode = CO2FootprintCLI.postRun(parsedArgs)

        then:
        exitCode == 0

        for(Path outPath : outPaths) {
                fileChecker.runChecks(outPath)
        }
    }

    def 'test CLI post other delimiter'() {
        when:
        Map<String, Object> parsedArgs = [
                tracePath: Path.of(this.class.getResource('/cli/execution-trace-raw.csv').toURI()).complete().toString(),
                config: Path.of(this.class.getResource('/cli/test.config').toURI()).complete().toString(),
                delimiter: ','
        ]

        int exitCode = CO2FootprintCLI.postRun(parsedArgs)

        then:
        exitCode == 0

        for(Path outPath : outPaths) {
            fileChecker.runChecks(outPath)
        }
    }
}
