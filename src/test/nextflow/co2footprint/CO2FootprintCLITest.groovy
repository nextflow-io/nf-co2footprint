package nextflow.co2footprint

import nextflow.co2footprint.TestHelpers.FileChecker
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path

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
                tracePath: Path.of(this.class.getResource('/execution-trace-raw.tsv').toURI()).complete().toString(),
                config: Path.of(this.class.getResource('/cli/test.config').toURI()).complete().toString()
        ]
        int exitCode = CO2FootprintCLI.postRun(parsedArgs)

        then:
        exitCode == 0

        for(Path outPath : outPaths) {
            fileChecker.runChecks(outPath)
        }

    }
}
