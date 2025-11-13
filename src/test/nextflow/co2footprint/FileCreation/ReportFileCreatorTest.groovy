package nextflow.co2footprint.FileCreation

import nextflow.Session
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.co2footprint.Records.CO2RecordTree
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
                        'traceFile': tempPath,
                        'summaryFile': tempPath,
                        'reportFile': reportPath,
                        'ci': 475.0
                ],
                Mock(TDPDataMatrix),
                Mock(CIDataMatrix),
                [:]
        )

        Session session = Mock(Session) {
            getConfig() >> [
                    co2footprint: [
                            'traceFile': tempPath,
                            'summaryFile': tempPath,
                            'reportFile': reportPath,
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
        String expectedPayloadJson =
            '{' +
                '"trace":' +
                    '[' +
                        '{' +
                            '"task_id":{"raw":{"value":"111","type":"String"},"readable":"111"},' +
                            '"hash":{"raw":{"value":"ca/372f78","type":"String"},"readable":"ca/372f78","report":"<div class=\\"script_block short\\"><code>ca/372f78</code></div>"},' +
                            '"native_id":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"process":{"raw":{"value":"reportTestProcess","type":"String"},"readable":"reportTestProcess"},' +
                            '"module":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"container":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"tag":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"name":{"raw":{"value":"testTask","type":"String"},"readable":"testTask"},' +
                            '"status":{"raw":{"value":"COMPLETED","type":"String"},"readable":"COMPLETED","report":"<span class=\\"badge badge-success\\">COMPLETED</span>"},' +
                            '"exit":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"submit":{"raw":{"value":null,"type":"date"},"readable":"-"},' +
                            '"start":{"raw":{"value":null,"type":"date"},"readable":"-"},' +
                            '"complete":{"raw":{"value":null,"type":"date"},"readable":"-"},' +
                            '"duration":{"raw":{"value":null,"type":"time"},"readable":"-"},' +
                            '"realtime":{"raw":{"value":3600000,"type":"Long"},"readable":"1h"},' +
                            '"%cpu":{"raw":{"value":100.0,"type":"BigDecimal","scale":"%","unit":""},"readable":"100.0%"},' +
                            '"%mem":{"raw":{"value":null,"type":"perc"},"readable":"-"},' +
                            '"rss":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"vmem":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"peak_rss":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"peak_vmem":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"rchar":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"wchar":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"syscr":{"raw":{"value":null,"type":"num"},"readable":"-"},' +
                            '"syscw":{"raw":{"value":null,"type":"num"},"readable":"-"},' +
                            '"read_bytes":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"write_bytes":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"attempt":{"raw":{"value":null,"type":"num"},"readable":"-"},' +
                            '"workdir":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"script":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"scratch":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"queue":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"cpus":{"raw":{"value":1,"type":"Integer","scale":"","unit":""},"readable":"1"},' +
                            '"memory":{"raw":{"value":7.516192768E9,"type":"Double","scale":"","unit":"B"},"readable":"7 GB"},' +
                            '"disk":{"raw":{"value":null,"type":"mem"},"readable":"-"},' +
                            '"time":{"raw":{"value":3600000.0000,"type":"BigDecimal","scale":"ms","unit":""},"readable":"1h"},' +
                            '"env":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"error_action":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"vol_ctxt":{"raw":{"value":null,"type":"num"},"readable":"-"},' +
                            '"inv_ctxt":{"raw":{"value":null,"type":"num"},"readable":"-"},' +
                            '"hostname":{"raw":{"value":null,"type":"str"},"readable":"-"},' +
                            '"cpu_model":{"raw":{"value":"Unknown model","type":"String"},"readable":"Unknown model"},' +
                            '"energy":{"raw":{"value":100000.0,"type":"Double","scale":"","unit":"Wh"},"readable":"100 kWh"},' +
                            '"co2e":{"raw":{"value":10.0,"type":"Double","scale":"","unit":"g"},"readable":"10 g"},' +
                            '"co2eMarket":{"raw":{"value":null,"type":"Number","scale":"m","unit":"g"},"readable":"-"},' +
                            '"ci":{"raw":{"value":475.0,"type":"Double","scale":"","unit":"gCO\\u2082e/kWh"},"readable":"475 gCO\\u2082e/kWh"},' +
                            '"cpuUsage":{"raw":{"value":100.0,"type":"Double","scale":"%","unit":""},"readable":"100 %"},' +
                            '"powerdrawCPU":{"raw":{"value":12.0,"type":"Double","scale":"","unit":"W"},"readable":"12 W"},' +
                            '"rawEnergyProcessor":{"raw":{"value":0.5,"type":"Double"},"readable":"500 Wh"},' +
                            '"rawEnergyMemory":{"raw":{"value":0.5,"type":"Double"},"readable":"500 Wh"}' +
                        '}' +
                    '],' +
                '"summary":' +
                    '{' +
                        '"process":' +
                        '{' +
                            '"co2e":[10.0],' +
                            '"energy":[100.0],' +
                            '"co2e_non_cached":[10.0],' +
                            '"energy_non_cached":[100.0]' +
                        '}' +
                    '}' +
                '}'
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

