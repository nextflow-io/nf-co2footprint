package nextflow.co2footprint.FileCreation

import nextflow.Session
import nextflow.co2footprint.CO2FootprintCalculator
import nextflow.co2footprint.Config.ProvenanceFileConfig
import nextflow.co2footprint.Config.ReportFileConfig
import nextflow.co2footprint.Config.SummaryFileConfig
import nextflow.co2footprint.Config.TraceFileConfig
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

class ReportFileCreatorTest extends Specification{
    @Shared
    Path tempPath = Files.createTempDirectory('tmpdir')

    @Shared
    Path reportPath = tempPath.resolve('report_test.html')

    @Shared
    CiRecordCollector timeCiRecordCollector

    static ReportFileCreator co2FootprintReport

    def setupSpec() {
        CO2FootprintConfig config = new CO2FootprintConfig(
                [
                        'trace': ['enabled': true, 'file': tempPath],
                        'summary': ['enabled': true, 'file': tempPath],
                        'report': ['enabled': true, 'file': reportPath],
                        'provenance': [enabled: true, file: tempPath],
                        'ci': 475.0
                ],
                Mock(TDPDataMatrix),
                Mock(CIDataMatrix),
                [:]
        )

        TraceRecord traceRecord = new TraceRecord()
        traceRecord.putAll(
            [
                'task_id': '111',
                'process': 'reportTestProcess',
                'realtime': (1 as Long) * (3600000 as Long), // 1 h
                'cpus': 1,
                'cpu_model': 'Unknown model',
                '%cpu': 100.0,
                'hash': 'ca/372f78',
                'status': 'COMPLETED',
                'memory': (7 as Long) * (1024**3 as Long), // 7 GB
                'name': 'testTask'
            ]
        )

        Session session = Mock(Session) {
            getConfig() >> [
                co2footprint: [
                    'trace': new TraceFileConfig(['enabled': true, 'file': tempPath]),
                    'summary': new SummaryFileConfig(['enabled': true, 'file': tempPath]),
                    'report': new ReportFileConfig(['enabled': true, 'file': reportPath]),
                    'provenance': new ProvenanceFileConfig([enabled: true, file: tempPath]),
                    'ci': 475.0
                ]
            ]
        }
        session.getExecService() >> Executors.newFixedThreadPool(1)

        timeCiRecordCollector = new CiRecordCollector(config)

        CO2Record co2Record = new CO2Record(
            traceRecord, 100.0d, 10.0d, null, 475.0, 100.0, 7,
            1.0d, 1, 12, 'Unknown model', 0.5d, 0.5d
        )

        // Define Record treee
        CO2RecordTree sessionStats = new CO2RecordTree('session', [level: 'session'])
        CO2RecordTree workflowStats = sessionStats.addChild(new CO2RecordTree('workflow', [level: 'workflow']))
        CO2RecordTree processTree =  workflowStats.addChild(new CO2RecordTree('process', [level: 'process']))
        processTree.addChild(new CO2RecordTree('task', [level: 'task'], co2Record))

        // Define calculator
        CO2FootprintCalculator calculator = new CO2FootprintCalculator(Mock(TDPDataMatrix), config)

        // Define report file
        ReportFileConfig reportFileConfig = new ReportFileConfig([file: reportPath])
        co2FootprintReport = new ReportFileCreator(reportFileConfig)
        co2FootprintReport.addEntries(sessionStats, calculator, config, timeCiRecordCollector, session)

        sessionStats.summarize()
        sessionStats.collectAdditionalMetrics()
    }

    def 'Test correct value rendering for totalsJson' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        ReportFileConfig reportFileConfig = new ReportFileConfig([file: tempPath.resolve('report_test.html')])
        ReportFileCreator co2FootprintReport = new ReportFileCreator(reportFileConfig)

        when:
        CO2RecordTree sessionStats = new CO2RecordTree('session', [level: 'session'])
        CO2RecordTree workflowStats = sessionStats.addChild(new CO2RecordTree('workflow', [level: 'workflow']))
        CO2RecordTree processTree =  workflowStats.addChild(new CO2RecordTree('process', [level: 'process']))
        CO2Record co2Record = new CO2Record(
                new TraceRecord(), 100.0, co2e,
                null, null, null, null, null, null, null, null, null, null
        )

        processTree.addChild(new CO2RecordTree('task', [level: 'task'], co2Record))
        sessionStats.summarize()
        sessionStats.collectAdditionalMetrics()

        CO2FootprintCalculator co2FootprintCalculator = new CO2FootprintCalculator(Mock(TDPDataMatrix), null)
        co2FootprintReport.addEntries(sessionStats, co2FootprintCalculator, null, timeCiRecordCollector)
        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson == totalsJsonResult
        where:
        co2e                || totalsJsonResult
        0.01d               || [co2e: '10 mg', energy:'100 kWh', car: '5.71E-5', tree: '28.69s', plane_percent: '2.00E-5 %', plane_flights: null,
                                co2e_non_cached:'10 mg', energy_non_cached:'100 kWh', car_non_cached: '5.71E-5', tree_non_cached: '28.69s', plane_percent_non_cached: '2.00E-5 %', plane_flights_non_cached: null]
        10_000_000.0d       || [co2e: '10 Mg', energy:'100 kWh', car: '5.71E4', tree: '908years 9months 3days 19h 38min 55.88s', plane_percent: null, plane_flights: '200',
                                co2e_non_cached:'10 Mg', energy_non_cached:'100 kWh', car_non_cached: '5.71E4', tree_non_cached: '908years 9months 3days 19h 38min 55.88s', plane_percent_non_cached: null, plane_flights_non_cached: '200']
    }

    def 'Test data JSON generation' () {
        when:
        String payloadJson = co2FootprintReport.renderDataJson()

        then:
        String expectedPayloadJson = this.class.getResource('/report/test_payload.json').text
        payloadJson == expectedPayloadJson
    }

    def 'Test options JSON generation' () {
        when:
        String optionsJson = co2FootprintReport.renderOptionsJson()

        then:
        optionsJson ==
                '[' +
                    '{"option":"ci","value":"475.0"},'+
                    '{"option":"ciMarket","value":null},' +
                    '{"option":"customCpuTdpFile","value":null},' +
                    '{"option":"ignoreCpuModel","value":"false"},' +
                    '{"option":"location","value":null},' +
                    '{"option":"machineType","value":null},' +
                    '{"option":"powerdrawCpuDefault","value":null},' +
                    '{"option":"powerdrawMem","value":"0.3725"},' +
                    "{\"option\":\"provenanceFile\",\"value\":\"${tempPath}\"}," +
                    '{"option":"pue","value":"1.0"},' +
                    "{\"option\":\"reportFile\",\"value\":\"${reportPath}\"}," +
                    "{\"option\":\"summaryFile\",\"value\":\"${tempPath}\"}," +
                    "{\"option\":\"traceFile\",\"value\":\"${tempPath}\"}" +
                ']'
    }

    

    def 'Test totals JSON generation' () {
        when:
        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson ==
            [
                co2e: "10 g",
                energy:  "100 kWh",
                car: "0.057",
                tree: "7h 58min 10.08s",
                plane_percent: "0.02 %",
                plane_flights: null,
                co2e_non_cached: "10 g",
                energy_non_cached:  "100 kWh",
                car_non_cached: "0.057",
                tree_non_cached: "7h 58min 10.08s",
                plane_percent_non_cached: '0.02 %',
                plane_flights_non_cached: null
            ]
    }

}

