package nextflow.co2footprint.FileCreation

import groovy.util.logging.Slf4j
import groovyx.gpars.agent.Agent
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Metrics.Quantity
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.trace.TraceHelper

import java.nio.file.Path

/**
 * Generates the summary text file for the CO₂ footprint.
 *
 * Writes total energy, CO₂ emissions, equivalences, and plugin options
 * to a human-readable summary file at the end of the workflow.
 */
@Slf4j
class SummaryFileCreator extends BaseFileCreator {
    // Agent for thread-safe writing (not strictly needed for single write)
    Agent<PrintWriter> summaryWriter

    /**
     * Constructor for summary file.
     *
     * @param path      Path to the summary file
     * @param overwrite Whether to overwrite existing files
     */
    SummaryFileCreator(Path path, boolean overwrite) {
        super(path, overwrite)
    }

    /**
     * Create the summary file.
     */
    void create() {
        super.create()

        writer = TraceHelper.newFileWriter(path, overwrite, 'co2footprintsummary')
        file = new PrintWriter(writer)
    }

    /**
     * Write the summary file with totals and options.
     *
     * @param totalStats             Map containing total energy ('energy') in Wh and total CO₂ emissions ('co2e') in grams.
     * @param co2FootprintComputer   CO2FootprintComputer instance for calculating equivalences.
     * @param config                 CO2FootprintConfig instance with plugin configuration.
     * @param version                Plugin version string.
     */
    void write(CO2RecordTree workflowStats, CO2FootprintComputer co2FootprintComputer, CO2FootprintConfig config, String version) {
        if (!created) { return }
        Map<String, Object> totalStats = workflowStats.co2Record.store

        // Launch the agent (for thread safety, though only one write is performed)
        summaryWriter = new Agent<PrintWriter>(file)

        CO2EquivalencesRecord equivalences = co2FootprintComputer.computeCO2footprintEquivalences(totalStats['co2e'] as Double)

        String outText = """\
        Total CO₂e footprint measures of this workflow run (including cached tasks):
          CO₂e emissions: ${new Quantity(totalStats['co2e'],'', 'g').round().toReadable() }
          Energy consumption: ${new Quantity(totalStats['energy'], 'k', 'Wh').toReadable() }
          CO₂e emissions (market): ${totalStats['co2eMarket'] ? new Quantity(totalStats['co2eMarket'], '', 'g').toReadable() : "-"}

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

        file.print(outText)
        file.flush()
    }
}