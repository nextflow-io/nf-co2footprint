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
    FileChecker fileChecker = new FileChecker('/extension')

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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s', '1', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh', 'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'runtime_h * memory * 0.3725']
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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s', '1', '1', '11.41 W', '-', '3.17 mWh', '103.47 uWh', 'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'runtime_h * memory * 0.3725']
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
                this.class.getResource('/extension/provenance-hello.json').path as Path,
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
                this.class.getResource('/extension/provenance-hello.json').path as Path,
                [provenance: [file: provenancePath], ci: 100.0],
                'provenance'
        )
        Map<String, Object> treeMap = output.co2RecordTree.toMap(true, false, false)

        then:
        assert treeMap == ['name':'pensive_becquerel-session', 'metaData':['workflowLevel':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'],'name':['raw':['value':'pensive_becquerel-session', 'type':'str', 'description':null], 'readable':'pensive_becquerel-session'], 'energy_consumption':['raw':['value':0.20374734895534169511696652928, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'203.75 mWh'], 'CO2e':['raw':['value':0.020374734895534169511696652928000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'20.37 mg'], 'CO2e_market':['raw':['value':0.020374734895534169511696652928000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'20.37 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':193.29778480097133, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'193.3 %'], 'memory':['raw':['value':268251136, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'268.25 MB'], 'realtime':['raw':['value':23595, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'23s 595ms'], 'cpus':['raw':['value':7.9105742742, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'7.9105742742'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.1560828074930857395102819456, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'156.08 mWh'], 'raw_energy_memory':['raw':['value':0.00064592247256171827200, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'645.92 uWh']], 'children':[
                ['name':'pensive_becquerel-head_job', 'metaData':['workflowLevel':'head'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'pensive_becquerel-head_job', 'type':'str', 'description':null], 'readable':'pensive_becquerel-head_job'], 'energy_consumption':['raw':['value':0.20237789188075609511696652928, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'202.38 mWh'], 'CO2e':['raw':['value':0.020237789188075609511696652928000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'20.24 mg'], 'CO2e_market':['raw':['value':0.020237789188075609511696652928000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'20.24 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':194.66628990498424, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'194.67 %'], 'memory':['raw':['value':268251136, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'268.25 MB'], 'realtime':['raw':['value':23271, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'23s 271ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.1550293789741737395102819456, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'155.03 mWh'], 'raw_energy_memory':['raw':['value':0.00064592247256171827200, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'645.92 uWh']], 'children':[]],
                ['name':'pensive_becquerel', 'metaData':['workflowLevel':'workflow'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello', 'type':'str', 'description':null], 'readable':'sayHello'], 'energy_consumption':['raw':['value':0.00136945707458560, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'1.37 mWh'], 'CO2e':['raw':['value':0.000136945707458560000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'136.95 ug'], 'CO2e_market':['raw':['value':0.000136945707458560000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'136.95 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':95.0061728396, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'95.01 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':324, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'324ms'], 'cpus':['raw':['value':1.4876543209, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.4876543209'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0010534285189120, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'1.05 mWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[
                        ['name':'sayHello', 'metaData':['workflowLevel':'process'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello', 'type':'str', 'description':null], 'readable':'sayHello'], 'energy_consumption':['raw':['value':0.00136945707458560, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'1.37 mWh'], 'CO2e':['raw':['value':0.000136945707458560000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'136.95 ug'], 'CO2e_market':['raw':['value':0.000136945707458560000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'136.95 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':95.0061728396, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'95.01 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':324, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'324ms'], 'cpus':['raw':['value':1.4876543209, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.4876543209'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0010534285189120, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'1.05 mWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[
                                ['name':'4', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':4, 'type':'str', 'description':null], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str', 'description':null], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.0001979131346192, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'197.91 uWh'], 'CO2e':['raw':['value':0.00001979131346192000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'19.79 ug'], 'CO2e_market':['raw':['value':0.00001979131346192000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'19.79 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':76.7, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'76.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':58, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'58ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000152240872784, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'152.24 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]],
                                ['name':'3', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':3, 'type':'str', 'description':null], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str', 'description':null], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.0002896316991568, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'289.63 uWh'], 'CO2e':['raw':['value':0.00002896316991568000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'28.96 ug'], 'CO2e_market':['raw':['value':0.00002896316991568000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'28.96 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':75.7, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'75.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':86, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'86ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000222793614736, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'222.79 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]],
                                ['name':'2', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':2, 'type':'str', 'description':null], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str', 'description':null], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.00082171888358560, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'821.72 uWh'], 'CO2e':['raw':['value':0.000082171888358560000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'82.17 ug'], 'CO2e_market':['raw':['value':0.000082171888358560000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'82.17 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':116.9, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'116.9 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':158, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'158ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'2'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0006320914489120, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'632.09 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]],
                                ['name':'1', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':1, 'type':'str', 'description':null], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str', 'description':null], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.0000601933572240, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'60.19 uWh'], 'CO2e':['raw':['value':0.00000601933572240000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'6.02 ug'], 'CO2e_market':['raw':['value':0.00000601933572240000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'6.02 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':61.5, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'61.5 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':22, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'22ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000046302582480, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'46.3 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh']], 'children':[]]
                        ]]
                ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
