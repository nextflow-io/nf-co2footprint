package nextflow.co2footprint.FileCreation

import nextflow.Session
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Config.FileSubConfig
import nextflow.co2footprint.DataContainers.CIDataMatrix
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
    CO2RecordTree workflowStats

    @Shared
    CiRecordCollector timeCiRecordCollector

    static ReportFileCreator co2FootprintReport

    def setupSpec() {
        CO2FootprintConfig config = new CO2FootprintConfig(
                [
                        'trace': ['enabled': true, 'file': tempPath],
                        'summary': ['enabled': true, 'file': tempPath],
                        'report': ['enabled': true, 'file': reportPath],
                        'ci': 475.0
                ],
                Mock(TDPDataMatrix),
                Mock(CIDataMatrix),
                [:]
        )

        Session session = Mock(Session) {
            getConfig() >> [
                    co2footprint: [
                            'trace': new FileSubConfig(['enabled': true, 'file': tempPath], 'trace'),
                            'summary': new FileSubConfig(['enabled': true, 'file': tempPath], 'summary'),
                            'report': new FileSubConfig(['enabled': true, 'file': reportPath], 'report', 'html'),
                            'ci': 475.0
                    ]
            ]
        }
        session.getExecService() >> Executors.newFixedThreadPool(1)

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

        session.getExecService() >> Executors.newFixedThreadPool(1)

        timeCiRecordCollector = new CiRecordCollector(config)

        CO2Record co2Record = new CO2Record(
            traceRecord, 100.0d, 10.0d, null, 475.0, 100.0, 7,
            1.0d, 1, 12, 'Unknown model', 0.5d, 0.5d
        )
        workflowStats = new CO2RecordTree('workflow', [level: 'workflow'])
        CO2RecordTree processTree =  workflowStats.addChild(new CO2RecordTree('process', [level: 'process']))
        processTree.addChild(new CO2RecordTree('task', [level: 'task'], co2Record))

        co2FootprintReport = new ReportFileCreator(reportPath, false, 10_000)
        co2FootprintReport.addEntries(
                workflowStats, new CO2FootprintComputer(Mock(TDPDataMatrix), config),
                config, 'test-version', session, timeCiRecordCollector
        )

        workflowStats.summarize()
        workflowStats.collectAdditionalMetrics()
    }

    def 'Test correct value rendering for totalsJson' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        ReportFileCreator co2FootprintReport = new ReportFileCreator(tempPath.resolve('report_test.html'))

        when:
        CO2RecordTree workflowStats = new CO2RecordTree('workflow', [level: 'workflow'])
        CO2RecordTree processTree =  workflowStats.addChild(new CO2RecordTree('process', [level: 'process']))
        CO2Record co2Record = new CO2Record(
                new TraceRecord(), 100.0, co2e,
                null, null, null, null, null, null, null, null, null, null
        )

        processTree.addChild(new CO2RecordTree('task', [level: 'task'], co2Record))
        workflowStats.summarize()
        workflowStats.collectAdditionalMetrics()

        co2FootprintReport.addEntries(
                workflowStats, new CO2FootprintComputer(Mock(TDPDataMatrix), null),
                null, null, null, timeCiRecordCollector
        )
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
        String expectedPayloadJson = this.class.getResource('/test_payload.json').text
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

