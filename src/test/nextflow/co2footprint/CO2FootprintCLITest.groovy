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

    private Path tracePath =  outPath.resolve('trace_test.txt')
    private Path summaryPath = outPath.resolve('summary_test.txt')
    private Path reportPath = outPath.resolve('report_test.html')
    private Path provenancePath = outPath.resolve('provenance_test.json')

    def cleanup() {
        outputDirectory.deleteDir()
    }

    def 'test CLI post run'() {
        when:
        String tracePath2 = Path.of(this.class.getResource('/cli/execution-trace-raw.tsv').toURI()).complete().toString()
        String configPath = Path.of(this.class.getResource('/cli/test.config').toURI()).complete().toString()
        Map<String, Object> parsedArgs = [
                tracePath: tracePath2,
                config: configPath,
                delimiter: '\t'
        ]

        int exitCode = CO2FootprintCLI.postRun(parsedArgs)

        then:
        exitCode == 0

        // Check Trace File
        fileChecker.runChecks(tracePath)

        // Check Report File
        fileChecker.runChecks(reportPath, [
                '<dd><pre class="nfcommand"><code>nextflow plugin nf-co2footprint:postRun --tracePath (.+?) --config (.+?)</code></pre></dd>' : [
                        tracePath2, configPath
                ]
        ])

        // Check provenance file
        fileChecker.runChecks(provenancePath)
    }

    def 'test CLI post other delimiter'() {
        when:
        String tracePath2 = Path.of(this.class.getResource('/cli/execution-trace-raw.tsv').toURI()).complete().toString()
        String configPath = Path.of(this.class.getResource('/cli/test.config').toURI()).complete().toString()
        Map<String, Object> parsedArgs = [
                tracePath: tracePath2,
                config: configPath,
                delimiter: '\t'
        ]

        int exitCode = CO2FootprintCLI.postRun(parsedArgs)

        then:
        exitCode == 0
        
        // Check Trace File
        fileChecker.runChecks(tracePath)

        // Check Report File
        fileChecker.runChecks(reportPath, [
                '<dd><pre class="nfcommand"><code>nextflow plugin nf-co2footprint:postRun --tracePath (.+?) --config (.+?)</code></pre></dd>' : [
                        tracePath2, configPath
                ]
        ])

        // Check provenance file
        fileChecker.runChecks(provenancePath)
    }
}
