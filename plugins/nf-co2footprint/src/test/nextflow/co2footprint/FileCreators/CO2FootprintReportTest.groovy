package nextflow.co2footprint.FileCreators

import nextflow.Session
import nextflow.co2footprint.CIDataMatrix
import nextflow.co2footprint.CO2EquivalencesRecord
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.CO2FootprintResourcesAggregator
import nextflow.co2footprint.CO2Record
import nextflow.co2footprint.TDPDataMatrix
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
    @Shared
    CO2FootprintReport co2FootprintReport

    def setupSpec() {
        TaskId taskId = new TaskId(111)

        TraceRecord traceRecord = new TraceRecord()
        traceRecord.putAll(
                [
                        'task_id': '111',
                        'process': 'reportTestProcess',
                        'realtime': (1 as Long) * (3600000 as Long), // 1 h
                        'cpus': 1,
                        'cpu_model': "Unknown model",
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
                1.0d, 1.0d, 1.0d, 475.0, 1, 12, 100.0, 1024**3, 'testTask', 'Unknown model'
        )

        CO2EquivalencesRecord equivalencesRecord = new CO2EquivalencesRecord(10.0, 10.0, 10.0)

        CO2FootprintResourcesAggregator aggregator = new CO2FootprintResourcesAggregator(session)
        aggregator.aggregate(co2Record, 'reportTestProcess')

        co2FootprintReport = new CO2FootprintReport(reportPath, false, 10_000)
        co2FootprintReport.addEntries(
                10.0d, 100.0d,
                equivalencesRecord, aggregator, config,
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
        CO2EquivalencesRecord equivalences = new CO2EquivalencesRecord(carKm, treeMonths, planePercent)
        co2FootprintReport.addEntries(
                10, 10, equivalences, null, null, null, null, null, null
        )
        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson == totalsJsonResult

        where:
        carKm   || treeMonths   || planePercent  || totalsJsonResult
        10.0    || 10.0         || 10.0          || [ co2: '10.0 mg', energy:'10.0 mWh', car: '10.0', tree: '10months', plane_percent: '10.0', plane_flights: null]
        10.0    || 10.0         || 100.0         || [ co2: '10.0 mg', energy:'10.0 mWh', car: '10.0', tree: '10months', plane_percent: null, plane_flights: '1.0']
    }

    def 'Test payLoad JSON generation' () {
        when:
        String payloadJson = co2FootprintReport.renderPayloadJson()

        then:
        payloadJson ==
                '{ ' +
                    '"trace":' +
                    '[\n' +
                        '{' +
                            '"task_id":"111","hash":"-","native_id":"-","process":"reportTestProcess","module":"-","container":"-",' +
                            '"tag":"-","name":"-","status":"-","exit":"-","submit":"-","start":"-","complete":"-","duration":"-",' +
                            '"realtime":"3600000","%cpu":"100.0","%mem":"-","rss":"-","vmem":"-","peak_rss":"-","peak_vmem":"-","rchar":"-",' +
                            '"wchar":"-","syscr":"-","syscw":"-","read_bytes":"-","write_bytes":"-","attempt":"-","workdir":"-","script":"-",' +
                            '"scratch":"-","queue":"-","cpus":"1","memory":"7516192768","disk":"-","time":"-","env":"-","error_action":"-",' +
                            '"vol_ctxt":"-","inv_ctxt":"-","hostname":"-","cpu_model":"Unknown model","energy":"1.0","co2e":"1.0",' +
                            '"time":"1.0","ci":"475.0","cpus":"1","powerdrawCPU":"12.0","cpuUsage":"100.0","memory":"1073741824","cpu_model":"Unknown model"' +
                        '}' +
                    '], ' +
                    '"summary":' +
                    '[' +
                        '{"co2e":' +
                            '{' +
                                '"mean":1.0,"min":1.0,"q1":1.0,"q2":1.0,"q3":1.0,"max":1.0,"minLabel":"testTask",' +
                                '"maxLabel":"testTask","q1Label":"testTask","q2Label":"testTask","q3Label":"testTask"' +
                            '},' +
                            '"process":"reportTestProcess",' +
                            '"energy":{' +
                                '"mean":1.0,"min":1.0,"q1":1.0,"q2":1.0,"q3":1.0,"max":1.0,' +
                                '"minLabel":"testTask","maxLabel":"testTask","q1Label":"testTask","q2Label":"testTask","q3Label":"testTask"' +
                            '}' +
                        '}' +
                    '] ' +
                '}'
    }

    def 'Test options JSON generation' () {
        when:
        String optionsJson = co2FootprintReport.renderOptionsJson()

        then:
        optionsJson ==
                '[' +
                    '{ "option":"ci", "value":"475.0" },' +
                    '{ "option":"customCpuTdpFile", "value":"null" },' +
                    '{ "option":"ignoreCpuModel", "value":"false" },' +
                    '{ "option":"location", "value":"null" },' +
                    '{ "option":"powerdrawCpuDefault", "value":"null" },' +
                    '{ "option":"powerdrawMem", "value":"0.3725" },' +
                    '{ "option":"pue", "value":"1.0" },' +
                    "{ \"option\":\"reportFile\", \"value\":\"${reportPath}\" }," +
                    "{ \"option\":\"summaryFile\", \"value\":\"${tempPath}\" }," +
                    "{ \"option\":\"traceFile\", \"value\":\"${tempPath}\" }" +
                ']'
    }

    def 'Test totals JSON generation' () {
        when:
        Map<String, String> totalsJson = co2FootprintReport.renderCO2TotalsJson()

        then:
        totalsJson ==
            [
                "co2": "100.0 mg",
                "energy":  "10.0 mWh",
                "car": "10.0",
                "tree": "10months",
                "plane_percent": "10.0",
                "plane_flights": null
            ]
    }

}

