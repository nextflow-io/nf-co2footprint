

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
        Path dataPath = outPath.resolve('data_test.json')

        // Set permissions and run preparation script
        Process permissions = [
                "chmod", "+x", preparationPath.toString()
        ].execute()
        permissions.waitFor()
        println("-Permissions-")
        println("STDERR: ${permissions.err.text}")
        println("STDOUT: ${permissions.text}")

        Process preparation = [
                "bash", "-c", preparationPath.toString()
        ].execute()
        int preparationExitCode = preparation.waitFor()
        println("-Preparation-")
        println("STDERR: ${preparation.err.text}")
        String preparationText = preparation.text
        println("STDOUT: ${preparationText}")

        when:
        println("Attempting run in directory: ${tempPath.toString()}")
        ProcessBuilder processBuilder = new ProcessBuilder(["nextflow", "run", "nf-core/demo", "-r", "1.1.0", "-profile", "test,docker", "--outdir", "out", "-c", configPath.toString()])
        processBuilder.directory(tempPath.toFile())
        Map<String, String> env = processBuilder.environment()
        env.put( 'NXF_PLUGINS_DEV', Path.of( this.class.getResource('/').toURI() ).resolve( Path.of("..", "..") ).complete().toString() )
        Process run = processBuilder.start()

        int runExitCode = run.waitFor()
        println("-Run-")
        println("STDERR: ${run.err.text}")
        println("STDOUT: ${run.text}")

        println("Output directory: ${outPath.listFiles()}")

        then:
        // check whether the scripts ran as expected
        preparationExitCode in [0, 1] // Allow for exit code 1 if permissions were already set
        runExitCode == 0


        // Check all files exist
        [tracePath, summaryPath, reportPath, dataPath].each { path ->
            fileChecker.checkIsFile(path)
        }

        // Check line count
        fileChecker.compareNumLines(tracePath, 8)
        fileChecker.compareNumLines(summaryPath, 30)
        fileChecker.compareNumLines(reportPath, 1863)
        (fileChecker.compareNumLines(dataPath, 1667) || fileChecker.compareNumLines(dataPath, 1668))
    }
}
