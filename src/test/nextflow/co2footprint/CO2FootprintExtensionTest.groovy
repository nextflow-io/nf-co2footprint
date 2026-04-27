package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.co2footprint.TestHelpers.FileChecker
import nextflow.co2footprint.TestHelpers.TestHelper
import nextflow.trace.TraceRecord
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class CO2FootprintExtensionTest extends Specification {
    @Shared
    FileChecker fileChecker = new FileChecker('/cli')

    Session createSession() {
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_extension_test.txt')
        Path summaryPath = tempPath.resolve('summary_extension_test.txt')
        Path reportPath = tempPath.resolve('report_extension_test.html')
        Path provenancePath = tempPath.resolve('provenance_extension_test.json')

        return new Session(
            [ co2footprint:
                  [
                      trace: [file: tracePath],
                      summary: [file: summaryPath],
                      report: [file: reportPath],
                      provenance: [file: provenancePath, enabled: true]
                  ]
            ]
        )
    }

    def 'Should calculate the CO2Footprint from an old trace file'() {
        given:
        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        CO2FootprintExtension.Output output = extension.calculateCO2(
                this.class.getResource('/cli/execution-trace-regular.tsv').path as Path, [ci: 100.0]
        )

        then:
        List<CO2Record> co2Records = output.co2RecordTree.descentTo('task').collect( { CO2RecordTree tree -> tree.co2Record } )
        co2Records.size() == 8
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '100 %', '1 GB', '1s 0ms', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh']
        co2Records[7].additionalMetrics == [CO2e_non_cached:3.2729169285E-4, energy_consumption_non_cached:3.2729169285E-6, CO2e_market:null, energy_consumption_market:3.2729169285E-6]

        // Check whether all files exist
        fileChecker.checkIsFile(output.config.trace.file)
        fileChecker.checkIsFile(output.config.summary.file)
        fileChecker.checkIsFile(output.config.report.file)
        fileChecker.checkIsFile(output.config.provenance.file)
    }

    def 'Should modify the output paths'() {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path tracePath = tempPath.resolve('trace_test.txt')

        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        CO2FootprintExtension.Output output = extension.calculateCO2(
                this.class.getResource('/cli/execution-trace-regular.tsv').path as Path, [trace: [file: tracePath], ci: 100.0]
        )

        then:
        List<CO2Record> co2Records = output.co2RecordTree.descentTo('task').collect( { CO2RecordTree tree -> tree.co2Record } )
        co2Records.size() == 8
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '100 %', '1 GB', '1s 0ms', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh']
        co2Records[7].additionalMetrics == [CO2e_non_cached:3.2729169285E-4, energy_consumption_non_cached:3.2729169285E-6, CO2e_market:null, energy_consumption_market:3.2729169285E-6]
        fileChecker.checkIsFile(tracePath)
    }

    def 'Should calculate the CO2Footprint from a provenance file without changes'() {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path provenancePath = tempPath.resolve('provenance-hello.json')

        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        CO2FootprintExtension.Output output = extension.calculateCO2(
                this.class.getResource('/cli/provenance-hello.json').path as Path,
                [provenance: [file: provenancePath, enabled: true, emissionMetricsOnly: false], location: 'DE', pue: 1.3, machineType: 'local', ciMarket: 100.0],
                'provenance'
        )

        then:
        fileChecker.runChecks(provenancePath)
    }

    def 'Should calculate the CO2Footprint from a provenance file with changes'() {
        given:
        Path tempPath = Files.createTempDirectory('tmpdir')
        Path provenancePath = tempPath.resolve('provenance_test.txt')

        Session session = createSession()
        CO2FootprintExtension extension = new CO2FootprintExtension()
        extension.init(session)

        when:
        CO2FootprintExtension.Output output = extension.calculateCO2(
                this.class.getResource('/cli/provenance-hello.json').path as Path,
                [provenance: [file: provenancePath], ci: 100.0],
                'provenance'
        )
        Map<String, Object> treeMap = output.co2RecordTree.toMap(true, false, false)

        then:
        assert treeMap == ['name':'insane_nobel-session', 'metaData':['level':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str'], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'insane_nobel-session', 'type':'str'], 'readable':'insane_nobel-session'], 'energy_consumption':['raw':['value':0.2692325747308085, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'269.23 mWh'], 'CO2e':['raw':['value':0.02692325747308085, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'26.92 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':333.8024412707263, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'333.8 %'], 'memory':['raw':['value':4294967296, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'4 GB'], 'realtime':['raw':['value':24490.0000800000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'24s 490ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':''], 'readable':'8'], 'powerdraw_cpu':['raw':['value':11.41, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.25909643580880853, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'259.1 mWh'], 'raw_energy_memory':['raw':['value':0.010136138922, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'10.14 mWh']], 'children':[
                    ['name':'Unknown workflow', 'metaData':['level':'workflow'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.000611245803728, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'611.25 uWh'], 'CO2e':['raw':['value':0.0000611245803728, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'61.12 ug'], 'carbon_intensity':['raw':['value':99.99999999999999, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':138.7452333655348, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'138.75 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':138.9999600000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'139ms'], 'cpus':['raw':['value':1.863308737642818380724713875521158423355, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.863308737642818380724713875521158423355'], 'powerdraw_cpu':['raw':['value':11.409999999999998, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000611245803728, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'611.25 uWh'], 'raw_energy_memory':['raw':['value':0.0, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                            ['name':'sayHello', 'metaData':['level':'process'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.000611245803728, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'611.25 uWh'], 'CO2e':['raw':['value':0.0000611245803728, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'61.12 ug'], 'carbon_intensity':['raw':['value':99.99999999999999, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':138.7452333655348, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'138.75 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':138.9999600000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'139ms'], 'cpus':['raw':['value':1.863308737642818380724713875521158423355, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.863308737642818380724713875521158423355'], 'powerdraw_cpu':['raw':['value':11.409999999999998, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000611245803728, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'611.25 uWh'], 'raw_energy_memory':['raw':['value':0.0, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                                    ['name':'4', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':4, 'type':'str'], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str'], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.000133496466012, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'133.5 uWh'], 'CO2e':['raw':['value':0.0000133496466012, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'13.35 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':140.4, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'140.4 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':29.9998800000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'30ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':''], 'readable':'2'], 'powerdraw_cpu':['raw':['value':11.41, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000133496466012, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'133.5 uWh'], 'raw_energy_memory':['raw':['value':0.0, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                                    ['name':'2', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':2, 'type':'str'], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str'], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.000052391137260000004, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'52.39 uWh'], 'CO2e':['raw':['value':0.000005239113726, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'5.24 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':87.0, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'87 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':19.0000800000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'19ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'powerdraw_cpu':['raw':['value':11.41, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000052391137260000004, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'52.39 uWh'], 'raw_energy_memory':['raw':['value':0.0, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                                    ['name':'3', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':3, 'type':'str'], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str'], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.000195154453844, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'195.15 uWh'], 'CO2e':['raw':['value':0.0000195154453844, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'19.52 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':181.1, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'181.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':33.9998400000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'34ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':''], 'readable':'2'], 'powerdraw_cpu':['raw':['value':11.41, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000195154453844, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'195.15 uWh'], 'raw_energy_memory':['raw':['value':0.0, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                                    ['name':'1', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':1, 'type':'str'], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str'], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.000230203746612, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'230.2 uWh'], 'CO2e':['raw':['value':0.0000230203746612, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'23.02 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':129.7, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'129.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':56.0001600000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'56ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':''], 'readable':'2'], 'powerdraw_cpu':['raw':['value':11.41, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'11.41 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000230203746612, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'230.2 uWh'], 'raw_energy_memory':['raw':['value':0.0, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]]
                            ]]
                    ]]
        ]] , "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
