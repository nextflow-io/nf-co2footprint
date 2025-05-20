package nextflow.co2footprint

import nextflow.NextflowMeta
import nextflow.Session
import nextflow.co2footprint.FileCreators.CO2FootprintReport
import nextflow.co2footprint.utils.Converter
import nextflow.executor.NopeExecutor
import nextflow.processor.TaskHandler
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.script.WorkflowMetadata
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.util.concurrent.Executors

class CO2FootprintReportTest extends Specification {
    def 'Test correct value rendering for totalsJson' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        CO2FootprintReport co2FootprintReport = new CO2FootprintReport(
                tempPath.resolve('report_test.html')
        )
        co2FootprintReport.total_co2 = 10
        co2FootprintReport.total_energy = 10

        when:
        CO2EquivalencesRecord equivalences = new CO2EquivalencesRecord(carKm, treeMonths, planePercent)
        co2FootprintReport.equivalences = equivalences

        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson == totalsJsonResult

        where:
        carKm   || treeMonths   || planePercent  || totalsJsonResult
        10.0    || 10.0         || 10.0          || [ co2: '10.0 mg', energy:'10.0 mWh', car: '10.0', tree: '10months', plane_percent: '10.0', plane_flights: null]
        10.0    || 10.0         || 100.0         || [ co2: '10.0 mg', energy:'10.0 mWh', car: '10.0', tree: '10months', plane_percent: null, plane_flights: '1.0']
    }
}
