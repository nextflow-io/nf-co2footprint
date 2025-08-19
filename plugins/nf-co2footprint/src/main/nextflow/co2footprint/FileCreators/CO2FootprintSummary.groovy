package nextflow.co2footprint.FileCreators

import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.utils.Converter
import nextflow.trace.TraceHelper

import java.nio.file.Path


/**
 * Generates the summary text file for the CO₂ footprint.
 *
 * Writes total energy, CO₂ emissions, equivalences, and plugin options
 * to a human-readable summary file at the end of the workflow.
 */
@Slf4j
class CO2FootprintSummary extends CO2FootprintFile {

    // Writer for the summary file
    PrintWriter co2eSummaryFile

    // Agent for thread-safe writing (not strictly needed for single write)
    Agent<PrintWriter> summaryWriter

    /**
     * Constructor for summary file.
     *
     * @param path      Path to the summary file
     * @param overwrite Whether to overwrite existing files
     */
    CO2FootprintSummary(Path path, boolean overwrite) {
        super(path, overwrite)
        this.co2eSummaryFile = new PrintWriter(TraceHelper.newFileWriter(path, overwrite, 'co2footprintsummary'))
    }

    /**
     * Write the summary file with totals and options.
     *
     * @param totalStats             Map containing total energy ('energy') in Wh and total CO₂ emissions ('co2e') in grams.
     * @param co2FootprintComputer   CO2FootprintComputer instance for calculating equivalences.
     * @param config                 CO2FootprintConfig instance with plugin configuration.
     * @param version                Plugin version string.
     */
    void write(Map<String, Double> totalStats, CO2FootprintComputer co2FootprintComputer, CO2FootprintConfig config, String version) {
        // Launch the agent (for thread safety, though only one write is performed)
        summaryWriter = new Agent<PrintWriter>(co2eSummaryFile)

        CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(totalStats['co2e'])

        String outText = """\
        Total CO2e footprint measures of this workflow run (including cached tasks):
          CO2e emissions: ${Converter.toReadableUnits(totalStats['co2e'],'m', 'g')}
          Energy consumption: ${Converter.toReadableUnits(totalStats['energy'],'m', 'Wh')}
          CO2e emissions (market): ${totalStats['co2eMarket'] ? Converter.toReadableUnits(totalStats['co2eMarket'], 'm', 'g') : "-"}

        """.stripIndent()
        List<String> readableEquivalences = equivalences.getReadableEquivalences()
        if (readableEquivalences.any()) {
            outText += 'Which equals:\n  '
            outText += String.join('\n  ', readableEquivalences)
        }

        outText += """\
        \n
        The calculation of these values is based on the carbon footprint computation method developed in the Green Algorithms project:
          Lannelongue, L., Grealey, J., Inouye, M., Green Algorithms: Quantifying the Carbon Footprint of Computation.
          Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707

        nf-co2footprint plugin version: ${version}

        nf-co2footprint options:
        """.stripIndent()
        config.collectCO2CalcOptions().each { key, value -> outText += "  ${key}: ${value}\n" }
        config.collectInputFileOptions().each { key, value -> outText += "  ${key}: ${value}\n" }
        config.collectOutputFileOptions().each { key, value -> outText += "  ${key}: ${value}\n" }

        co2eSummaryFile.print(outText)
        co2eSummaryFile.flush()
    }

    /**
     * Close the summary file writer.
     */
    void close() {
        co2eSummaryFile.close()
    }
}