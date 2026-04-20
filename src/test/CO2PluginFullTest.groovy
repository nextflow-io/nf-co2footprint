import nextflow.co2footprint.CO2FootprintPlugin
import nextflow.co2footprint.TestHelpers.FileChecker
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class CO2PluginFullTest extends Specification {
    @Shared
    FileChecker fileChecker = new FileChecker('/integration')

    def "Test the complete execution of a workflow run via bash"() {
        given:
        Path pluginPath = Path.of( this.class.getResource('/').toURI() ).resolve( Path.of("..", "..", '..', '..') ).complete()
        Path distributionPath = pluginPath.resolve( Path.of('build', 'distributions') )

        Path configPath = Path.of( this.class.getResource('/integration/nextflow.config').toURI() ).complete()
        Path preparationPath = Path.of(this.class.getResource('/integration/prepare-environment.sh').toURI()).complete()

        Path tempPath = Files.createTempDirectory('tmpdir')
        Path pluginsPath = tempPath.resolve('plugins')
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

        // Copy newest version of plugin into plugins path
        pluginsPath.toFile().mkdirs()
        String pluginName = "nf-co2footprint-${CO2FootprintPlugin.readPluginVersion()}"
        distributionPath.resolve("${pluginName}.zip").copyTo(pluginsPath)
        Path nfco2footprintPath = pluginsPath.resolve(pluginName)
        nfco2footprintPath.toFile().mkdirs()
        unzip(pluginsPath.resolve("${pluginName}.zip"), nfco2footprintPath)

        when:
        println("Attempting run in directory: ${tempPath.toString()}")
        ProcessBuilder processBuilder = new ProcessBuilder(["nextflow", "run", "nf-core/demo", "-r", "1.1.0", "-profile", "test,docker", "--outdir", "out", "-c", configPath.toString()])
        processBuilder.directory(tempPath.toFile())
        Map<String, String> env = processBuilder.environment()
        env.put( 'NXF_PLUGINS_MODE', 'prod')
        env.put( 'NXF_PLUGINS_DIR', pluginsPath.toString())
        Process run = processBuilder.start()

        int runExitCode = run.waitFor()
        println("-Run-")
        String runErrors = run.err.text
        println("STDERR: ${runErrors}")
        println("STDOUT: ${run.text}")

        println("Output directory: ${outPath.listFiles()}")

        println("Nextflow log:")
        tempPath.resolve('.nextflow.log').readLines().each { String line -> println(line) }

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
        long lines = dataPath.countLines()
        lines == 3918
    }

    def unzip(Path inZip, Path outputDir){
        byte[] buffer = new byte[1024]
        ZipInputStream zis = new ZipInputStream(new FileInputStream(inZip.toFile()))
        ZipEntry zipEntry = zis.getNextEntry()
        while (zipEntry != null) {
            File newFile = outputDir.resolve(zipEntry.name).toFile()
            if (zipEntry.isDirectory()) {
                if (!newFile.isDirectory() && !newFile.mkdirs()) {
                    throw new IOException("Failed to create directory " + newFile)
                }
            } else {
                // fix for Windows-created archives
                File parent = newFile.parentFile
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Failed to create directory " + parent)
                }
                // write file content
                FileOutputStream fos = new FileOutputStream(newFile)
                int len = 0
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len)
                }
                fos.close()
            }
            zipEntry = zis.getNextEntry()
        }
        zis.closeEntry()
        zis.close()
    }
}
