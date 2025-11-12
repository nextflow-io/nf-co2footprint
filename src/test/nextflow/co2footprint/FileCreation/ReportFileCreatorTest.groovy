package nextflow.co2footprint.FileCreation

import nextflow.Session
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Config.FileSubConfig
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.co2footprint.Records.CiRecordCollector
import nextflow.processor.TaskId
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
        TaskId taskId = new TaskId(111)

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
                        'memory': (7 as Long) * (1024**3 as Long) // 7 GB
                ]
        )

        Session session = Mock(Session) {
            getConfig() >> [
                co2footprint: [
                    'trace': new FileSubConfig('trace', ['enabled': true, 'file': tempPath]),
                    'summary': new FileSubConfig('summary', ['enabled': true, 'file': tempPath]),
                    'report': new FileSubConfig('report', ['enabled': true, 'file': reportPath]),
                    'ci': 475.0
                ]
            ]
        }
        session.getExecService() >> Executors.newFixedThreadPool(1)

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

        timeCiRecordCollector = new CiRecordCollector(config)

        CO2Record co2Record = new CO2Record(
                1.0d, 1.0d, null, 1.0d, 475.0,
                1, 12, 100.0, 7, 'testTask', 'Unknown model',
                0.5d, 0.5d
        )

        

        CO2RecordAggregator aggregator = new CO2RecordAggregator()
        aggregator.add(traceRecord, co2Record)

        co2FootprintReport = new ReportFileCreator(reportPath, false, 10_000)
        co2FootprintReport.addEntries(
                aggregator.computeProcessStats(),
                [co2e: 10.0d, energy: 100.0d, co2e_non_cached: 10.0d, energy_non_cached: 100.0d],
                new CO2FootprintComputer(Mock(TDPDataMatrix), config), config,
                'test-version', session, [(taskId): traceRecord], [(taskId): co2Record], timeCiRecordCollector
        )
    }

    def 'Test correct value rendering for totalsJson' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        ReportFileCreator co2FootprintReport = new ReportFileCreator(
                tempPath.resolve('report_test.html')
        )

        when:
        co2FootprintReport.addEntries(
                null,
                [co2e: co2e, energy: 10d, co2e_non_cached: co2e, energy_non_cached: 10d],
                new CO2FootprintComputer(Mock(TDPDataMatrix), null),
                null, null, null, null, null, timeCiRecordCollector
        )
        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson == totalsJsonResult
        where:
        co2e                || totalsJsonResult
        0.01d               || [co2e: '10 mg', energy:'10 kWh', car: '5.71E-5', tree: '28.69s', plane_percent: '2.00E-5 %', plane_flights: null,
                                co2e_non_cached:'10 mg', energy_non_cached:'10 kWh', car_non_cached: '5.71E-5', tree_non_cached: '28.69s', plane_percent_non_cached: '2.00E-5 %', plane_flights_non_cached: null]
        10_000_000.0d       || [co2e: '10 Mg', energy:'10 kWh', car: '5.71E4', tree: '908years 9months 3days 19h 38min 55.88s', plane_percent: null, plane_flights: '200',
                                co2e_non_cached:'10 Mg', energy_non_cached:'10 kWh', car_non_cached: '5.71E4', tree_non_cached: '908years 9months 3days 19h 38min 55.88s', plane_percent_non_cached: null, plane_flights_non_cached: '200']
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
                            '"task_id":{"raw":"111","readable":"111"},' +
                            '"process":{"raw":"reportTestProcess","readable":"reportTestProcess"},' +
                            '"realtime":{"raw":3600000,"readable":"1h"},' +
                            '"cpus":{"raw":1,"readable":"1"},' +
                            '"cpu_model":{"raw":"Unknown model","readable":"Unknown model"},' +
                            '"%cpu":{"raw":100.0,"readable":"100.0%"},' +
                            '"hash":{"raw":"ca/372f78","readable":"<div class=\\"script_block short\\"><code>ca/372f78</code></div>"},' +
                            '"status":{"raw":"COMPLETED","readable":"<span class=\\"badge badge-success\\">COMPLETED</span>"},' +
                            '"memory":{"raw":7,"readable":"7 GB"},' +
                            '"native_id":{"raw":null,"readable":"-"},' +
                            '"module":{"raw":null,"readable":"-"},' +
                            '"container":{"raw":null,"readable":"-"},' +
                            '"tag":{"raw":null,"readable":"-"},' +
                            '"name":{"raw":"testTask","readable":"testTask"},' +
                            '"exit":{"raw":null,"readable":"-"},' +
                            '"submit":{"raw":null,"readable":"-"},' +
                            '"start":{"raw":null,"readable":"-"},' +
                            '"complete":{"raw":null,"readable":"-"},' +
                            '"duration":{"raw":null,"readable":"-"},' +
                            '"%mem":{"raw":null,"readable":"-"},' +
                            '"rss":{"raw":null,"readable":"-"},' +
                            '"vmem":{"raw":null,"readable":"-"},' +
                            '"peak_rss":{"raw":null,"readable":"-"},' +
                            '"peak_vmem":{"raw":null,"readable":"-"},' +
                            '"rchar":{"raw":null,"readable":"-"},' +
                            '"wchar":{"raw":null,"readable":"-"},' +
                            '"syscr":{"raw":null,"readable":"-"},' +
                            '"syscw":{"raw":null,"readable":"-"},' +
                            '"read_bytes":{"raw":null,"readable":"-"},' +
                            '"write_bytes":{"raw":null,"readable":"-"},' +
                            '"attempt":{"raw":null,"readable":"-"},' +
                            '"workdir":{"raw":null,"readable":"-"},' +
                            '"script":{"raw":null,"readable":"-"},' +
                            '"scratch":{"raw":null,"readable":"-"},' +
                            '"queue":{"raw":null,"readable":"-"},' +
                            '"disk":{"raw":null,"readable":"-"},' +
                            '"time":{"raw":1.0,"readable":"3600s"},' +
                            '"env":{"raw":null,"readable":"-"},' +
                            '"error_action":{"raw":null,"readable":"-"},' +
                            '"vol_ctxt":{"raw":null,"readable":"-"},' +
                            '"inv_ctxt":{"raw":null,"readable":"-"},' +
                            '"hostname":{"raw":null,"readable":"-"},' +
                            '"energy":{"raw":1.0,"readable":"1 kWh"},' +
                            '"co2e":{"raw":1.0,"readable":"1 g"},' +
                            '"co2eMarket":{"raw":null,"readable":"-"},' +
                            '"ci":{"raw":475.0,"readable":"475 gCO\\u2082e/kWh"},' +
                            '"powerdrawCPU":{"raw":12.0,"readable":"12 W"},' +
                            '"cpuUsage":{"raw":100.0,"readable":"100 %"},' +
                            '"rawEnergyProcessor":{"raw":0.5,"readable":"500 Wh"},' +
                            '"rawEnergyMemory":{"raw":0.5,"readable":"500 Wh"}' +
                        '}' +
                    '],' +
                '"summary":' +
                    '{' +
                        '"reportTestProcess":' +
                        '{' +
                            '"co2e":' +
                                '{' +
                                    '"all":[1.0],"total":1.0,"mean":1.0,"minLabel":"testTask","min":1.0,"q1Label":"testTask","q1":1.0,"q2Label":"testTask","q2":1.0,"q3Label":"testTask","q3":1.0,"maxLabel":"testTask","max":1.0' +
                                '},' +
                            '"energy":' +
                                '{' +
                                    '"all":[1.0],"total":1.0,"mean":1.0,"minLabel":"testTask","min":1.0,"q1Label":"testTask","q1":1.0,"q2Label":"testTask","q2":1.0,"q3Label":"testTask","q3":1.0,"maxLabel":"testTask","max":1.0' +
                                '},' +
                            '"co2e_non_cached":' +
                                '{' +
                                    '"all":[1.0],"total":1.0,"mean":1.0,"minLabel":"testTask","min":1.0,"q1Label":"testTask","q1":1.0,"q2Label":"testTask","q2":1.0,"q3Label":"testTask","q3":1.0,"maxLabel":"testTask","max":1.0' +
                                '},' +
                            '"energy_non_cached":' +
                                '{' +
                                    '"all":[1.0],"total":1.0,"mean":1.0,"minLabel":"testTask","min":1.0,"q1Label":"testTask","q1":1.0,"q2Label":"testTask","q2":1.0,"q3Label":"testTask","q3":1.0,"maxLabel":"testTask","max":1.0' +
                                '},' +
                            '"co2e_market":{},' +
                            '"energy_market":' +
                                '{' +
                                    '"all":[1.0],"total":1.0,"mean":1.0,"minLabel":"testTask","min":1.0,"q1Label":"testTask","q1":1.0,"q2Label":"testTask","q2":1.0,"q3Label":"testTask","q3":1.0,"maxLabel":"testTask","max":1.0' +
                                '}' +
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

