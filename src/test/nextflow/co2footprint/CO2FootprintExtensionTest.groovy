package nextflow.co2footprint

import nextflow.Session
import nextflow.co2footprint.Records.CO2Record
import nextflow.co2footprint.Records.CO2RecordTree
import nextflow.co2footprint.TestHelpers.FileChecker
import nextflow.co2footprint.TestHelpers.TestHelper
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
                      summary: [file: summaryPath, enabled: true],
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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s', '1', '1', '11.41 W', '372.5 mW', '-', '-', '3.17 mWh', '103.47 uWh']
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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s', '1', '1', '11.41 W', '372.5 mW', '-', '-', '3.17 mWh', '103.47 uWh']
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
        extension.calculateCO2(
                this.class.getResource('/cli/provenance-hello.json').path as Path,
                [provenance: [file: provenancePath, enabled: true, emissionMetricsOnly: false], location: 'DE', pue: 1.3, ciMarket: 100.0],
                'provenance'
        )

        then:
        List searchExclusions = [
                /"readable": ("\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.?\d*")/
        ]
        fileChecker.runChecks(provenancePath, [:], searchExclusions)
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
        assert treeMap == ['name':'silly_kilby-session', 'metaData':['workflowLevel':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'silly_kilby-session', 'type':'str', 'description':null], 'readable':'silly_kilby-session'], 'energy_consumption':['raw':['value':0.1621600092064980371004424108800, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'162.16 mWh'], 'CO2e':['raw':['value':0.01621600092064980371004424108800000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'16.22 mg'], 'CO2e_market':['raw':['value':0.01621600092064980371004424108800000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'16.22 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':191.39104941446197, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'191.39 %'], 'memory':['raw':['value':286931316, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'286.93 MB'], 'realtime':['raw':['value':18959, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'18s 959ms'], 'cpus':['raw':['value':7.9678780526, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'7.9678780526'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.124178169871690725799878777600, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'124.18 mWh'], 'raw_energy_memory':['raw':['value':0.00056029874869237966200, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'560.3 uWh']], 'children':[
                ['name':'silly_kilby-head_job', 'metaData':['workflowLevel':'head'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'silly_kilby-head_job', 'type':'str', 'description':null], 'readable':'silly_kilby-head_job'], 'energy_consumption':['raw':['value':0.1618621874437795571004424108800, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'161.86 mWh'], 'CO2e':['raw':['value':0.01618621874437795571004424108800000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'16.19 mg'], 'CO2e_market':['raw':['value':0.01618621874437795571004424108800000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'16.19 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':191.91864168338194, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'191.92 %'], 'memory':['raw':['value':286931316, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'286.93 MB'], 'realtime':['raw':['value':18872, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'18s 872ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.123949076208061125799878777600, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'123.95 mWh'], 'raw_energy_memory':['raw':['value':0.00056029874869237966200, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'560.3 uWh']], 'children':[]],
                ['name':'silly_kilby', 'metaData':['workflowLevel':'workflow'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello', 'type':'str', 'description':null], 'readable':'sayHello'], 'energy_consumption':['raw':['value':0.000297821762718480, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'297.82 uWh'], 'CO2e':['raw':['value':0.0000297821762718480000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'29.78 ug'], 'CO2e_market':['raw':['value':0.0000297821762718480000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'29.78 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':76.9459770115, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'76.95 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':87, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'87ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00022909366362960, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'229.09 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[
                        ['name':'sayHello', 'metaData':['workflowLevel':'process'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello', 'type':'str', 'description':null], 'readable':'sayHello'], 'energy_consumption':['raw':['value':0.000297821762718480, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'297.82 uWh'], 'CO2e':['raw':['value':0.0000297821762718480000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'29.78 ug'], 'CO2e_market':['raw':['value':0.0000297821762718480000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'29.78 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':76.9459770115, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'76.95 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':87, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'87ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00022909366362960, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'229.09 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[
                                ['name':'4', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':4, 'type':'str', 'description':null], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str', 'description':null], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.0000695090428032, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'69.51 uWh'], 'CO2e':['raw':['value':0.00000695090428032000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'6.95 ug'], 'CO2e_market':['raw':['value':0.00000695090428032000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'6.95 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':74.4, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'74.4 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':21, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'21ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000053468494464, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'53.47 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]],
                                ['name':'1', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':1, 'type':'str', 'description':null], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str', 'description':null], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.000076342794528, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'76.34 uWh'], 'CO2e':['raw':['value':0.0000076342794528000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'7.63 ug'], 'CO2e_market':['raw':['value':0.0000076342794528000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'7.63 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':78.0, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'78 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':22, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'22ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00005872522656, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'58.73 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]],
                                ['name':'2', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':2, 'type':'str', 'description':null], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str', 'description':null], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.0000421758040704, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'42.18 uWh'], 'CO2e':['raw':['value':0.00000421758040704000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'4.22 ug'], 'CO2e_market':['raw':['value':0.00000421758040704000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'4.22 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':63.2, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'63.2 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':15, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'15ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000032442926208, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'32.44 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]],
                                ['name':'3', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':3, 'type':'str', 'description':null], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str', 'description':null], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.000109794121316880, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'109.79 uWh'], 'CO2e':['raw':['value':0.0000109794121316880000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'10.98 ug'], 'CO2e_market':['raw':['value':0.0000109794121316880000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'10.98 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':85.1, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'85.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':29, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'29ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00008445701639760, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'84.46 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]]
                        ]]
                ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
