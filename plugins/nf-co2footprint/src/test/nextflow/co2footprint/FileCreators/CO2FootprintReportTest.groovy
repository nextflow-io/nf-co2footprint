package nextflow.co2footprint.FileCreators

import nextflow.Session
import nextflow.co2footprint.CO2FootprintComputer
import nextflow.co2footprint.DataContainers.CIDataMatrix
import nextflow.co2footprint.Records.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Records.CO2RecordAggregator
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.DataContainers.TDPDataMatrix
import nextflow.processor.TaskId
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

class CO2FootprintReportTest extends Specification{
    @Shared
    Path tempPath = Files.createTempDirectory('tmpdir')

    @Shared
    Path reportPath = tempPath.resolve('report_test.html')

    static CO2FootprintReport co2FootprintReport

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
                        'memory': (7 as Long) * (1024**3 as Long) // 7 GB
                ]
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
        CO2Record co2Record = new CO2Record(
                1.0d, 1.0d, null, 1.0d, 475.0, null,
                1, 12, 100.0, 1024**3, 'testTask', 'Unknown model'
        )

        CO2EquivalencesRecord equivalencesRecord = new CO2EquivalencesRecord(10.0, 10.0, 10.0)

        CO2RecordAggregator aggregator = new CO2RecordAggregator()
        aggregator.add(traceRecord, co2Record)

        co2FootprintReport = new CO2FootprintReport(reportPath, false, 10_000)
        co2FootprintReport.addEntries(
                aggregator.computeProcessStats(),
                [co2e: 10.0d, energy: 100.0d, co2e_non_cached: 10.0d, energy_non_cached: 100.0d],
                new CO2FootprintComputer(Mock(TDPDataMatrix), config), config,
                'test-version', session, [(taskId): traceRecord], [(taskId): co2Record]
        )
    }

    def 'Test correct value rendering for totalsJson' () {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        CO2FootprintReport co2FootprintReport = new CO2FootprintReport(
                tempPath.resolve('report_test.html')
        )

        when:
        co2FootprintReport.addEntries(
                null,
                [co2e: co2e, energy: 10d, co2e_non_cached: co2e, energy_non_cached: 10d],
                new CO2FootprintComputer(Mock(TDPDataMatrix), null),
                null, null, null, null, null
        )
        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson == totalsJsonResult
        where:
        co2e                || totalsJsonResult
        10.0d               || [co2e: '10.0 mg', energy:'10.0 mWh', car: '5.71E-5', tree: '28.69s', plane_percent: '2.00E-5 %', plane_flights: null,
                                co2e_non_cached:'10.0 mg', energy_non_cached:'10.0 mWh', car_non_cached: '5.71E-5', tree_non_cached: '28.69s', plane_percent_non_cached: '2.00E-5 %', plane_flights_non_cached: null]
        10_000_000_000.0d   || [co2e: '10.0 Mg', energy:'10.0 mWh', car: '5.71E4', tree: '908years 9months 3days 19h 38min 55.87s', plane_percent: null, plane_flights: '200.0',
                                co2e_non_cached:'10.0 Mg', energy_non_cached:'10.0 mWh', car_non_cached: '5.71E4', tree_non_cached: '908years 9months 3days 19h 38min 55.87s', plane_percent_non_cached: null, plane_flights_non_cached: '200.0']
    }

    def 'Test payLoad JSON generation' () {
        when:
        String payloadJson = co2FootprintReport.renderPayloadJson()

        then:
        String expectedPayloadJson =
            '{' +
                '"trace":' +
                    '[' +
                        '{' +
                            '"task_id":"111","hash":"-","native_id":"-","process":"reportTestProcess","module":"-","container":"-",' +
                            '"tag":"-","name":"-","status":"-","exit":"-","submit":"-","start":"-","complete":"-","duration":"-",' +
                            '"realtime":"1h","%cpu":"100.0%","%mem":"-","rss":"-","vmem":"-","peak_rss":"-","peak_vmem":"-","rchar":"-",' +
                            '"wchar":"-","syscr":"-","syscw":"-","read_bytes":"-","write_bytes":"-","attempt":"-","workdir":"-","script":"-",' +
                            '"scratch":"-","queue":"-","cpus":"1","memory":"7 GB","disk":"-","time":"-","env":"-","error_action":"-",' +
                            '"vol_ctxt":"-","inv_ctxt":"-","hostname":"-","cpu_model":"Unknown model","energy":"1.0","co2e":"1.0",' +
                            '"personalEnergyMixco2e":"-","time":"1.0","ci":"475.0","personalEnergyMixCi":"-","cpus":"1",' +
                            '"powerdrawCPU":"12.0","cpuUsage":"100.0","memory":"1073741824","name":"testTask","cpu_model":"Unknown model"' +
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
                            '"co2e_personalEnergyMix":{},' +
                            '"energy_personalEnergyMix":' +
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
                    '{"option":"customCpuTdpFile","value":null},' +
                    '{"option":"ignoreCpuModel","value":"false"},' +
                    '{"option":"location","value":null},' +
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
                "co2e": "10.0 mg",
                "energy":  "100.0 mWh",
                "car": "5.71E-5",
                "tree": "28.69s",
                "plane_percent": "2.00E-5 %",
                "plane_flights": null,
                "co2e_non_cached": "10.0 mg",
                "energy_non_cached":  "100.0 mWh",
                car_non_cached: "5.71E-5",
                tree_non_cached: "28.69s",
                plane_percent_non_cached: '2.00E-5 %',
                plane_flights_non_cached: null
            ]
    }

}

