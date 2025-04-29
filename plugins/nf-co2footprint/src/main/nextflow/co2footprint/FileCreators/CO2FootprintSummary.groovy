package nextflow.co2footprint.FileCreators

import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.utils.Converter
import nextflow.trace.TraceHelper

import java.nio.file.Path

@Slf4j
/**
 * Class to generate the summary text file
 */
class CO2FootprintSummary extends CO2FootprintFile {

    PrintWriter co2eSummaryFile

    Agent<PrintWriter> summaryWriter

    /**
     * Constructor for summary file
     * @param path
     * @param overwrite
     */
    CO2FootprintSummary(Path path, boolean overwrite) {
        super(path, overwrite)
        this.co2eSummaryFile = new PrintWriter(TraceHelper.newFileWriter(path, overwrite, 'co2footprintsummary'))
    }

    /**
     * Make last changes to file and save it.
     *
     * @param total_energy Total expended energy during the run
     * @param total_co2 Total emitted CO2e during the run
     * @param equivalences Equivalences to CO2 emissions
     * @param config CO2FootprintConfiguration
     * @return
     */
    void write(Double total_energy, Double total_co2, CO2EquivalencesRecord equivalences, CO2FootprintConfig config, String version) {
        // launch the agent
        summaryWriter = new Agent<PrintWriter>(co2eSummaryFile)

        String outText = """\
        Total CO2e footprint measures of this workflow run
        CO2e emissions: ${Converter.toReadableUnits(total_co2,'m', 'g')}
        Energy consumption: ${Converter.toReadableUnits(total_energy,'m', 'Wh')}

        """.stripIndent()

        List<String> readableEquivalences = equivalences.getReadableEquivalences()
        if (readableEquivalences.any()) {
            outText += 'Which equals:\n'
            outText += String.join('\n', readableEquivalences)
        }

        outText += """\
        \n
        The calculation of these values is based on the carbon footprint computation method developed in the Green Algorithms project.
        Lannelongue, L., Grealey, J., Inouye, M., Green Algorithms: Quantifying the Carbon Footprint of Computation. Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707

        nf-co2footprint plugin version: ${version}

        nf-co2footprint options:
        """.stripIndent()
        config.collectInputFileOptions().each { key, value -> outText += "${key}: ${value}\n" }
        config.collectOutputFileOptions().each {key, value ->  outText += "${key}: ${value}\n" }
        config.collectCO2CalcOptions().each { key, value -> outText += "${key}: ${value}\n" }

        co2eSummaryFile.print(outText)

        co2eSummaryFile.flush()
    }

    void close() {
        co2eSummaryFile.close()
    }
}

