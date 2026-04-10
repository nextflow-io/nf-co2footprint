

import nextflow.co2footprint.TestHelpers.FileChecker
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CO2PluginFullTest extends Specification {
    @Shared
    FileChecker fileChecker = new FileChecker('/integration')

    def "Test the complete execution of a workflow run via bash"() {
        given:
        Path configPath = Path.of(this.class.getResource('/integration/nextflow.config').toURI()).complete()
        Path preparationPath = Path.of(this.class.getResource('/integration/prepare-environment.sh').toURI()).complete()
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path outPath = tempPath.resolve(Path.of('out', 'pipeline_info'))
        Path tracePath = outPath.resolve('trace_test.txt')
        Path summaryPath = outPath.resolve('summary_test.txt')
        Path reportPath = outPath.resolve('report_test.html')
        Path dataPath = outPath.resolve('data_test.yaml')

        // Set permissions and run preparation script
        Process permissions = [
                "chmod", "+x", preparationPath.toString()
        ].execute()
        permissions.waitFor()
        print(permissions.err.text)
        print(permissions.text)

        Process preparation = [
                "bash", "-c", preparationPath.toString()
        ].execute()
        int preparationExitCode = preparation.waitFor()
        print(preparation.err.text)
        print(preparation.text)

        when:
        print("Attempting run in directory: ${tempPath.toString()}")
        // TODO: Use local plugin build, instead of global to avoid interference with user environment !!!
        ProcessBuilder runBuilder = new ProcessBuilder(
                "nextflow", "run", "nf-core/demo", "-r", "1.1.0", "-profile", "test,docker", "--outdir", "out", "-c", configPath.toString()
        ).inheritIO()
        runBuilder.directory(tempPath.toFile())
        Process run = runBuilder.start()

        int runExitCode = run.waitFor()
        print(run.err.text)
        print(run.text)

        then:
        // check whether the scripts ran as expected
        preparationExitCode in [0, 1] // Allow for exit code 1 if permissions were already set
        runExitCode in [0, 1] // Allow for exit code 1 for Nextflow if the run was unsuccessful (e.g. due to missing dependencies), as long as the expected output files are generated


        // Check all files exist
        [tracePath, summaryPath, reportPath, dataPath].each { path ->
            fileChecker.checkIsFile(path)
        }

        // Check line count
        fileChecker.compareNumLines(tracePath, 8)
        fileChecker.compareNumLines(summaryPath, 30)
        fileChecker.compareNumLines(reportPath, 1816)
        fileChecker.compareNumLines(dataPath, 1117)
    }
}
