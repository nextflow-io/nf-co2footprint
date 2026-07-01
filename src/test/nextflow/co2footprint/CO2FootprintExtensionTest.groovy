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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s', '1', '1', '11.41 W', '372.5 mW', '-', '-', '3.17 mWh', '103.47 uWh', 'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'runtime_h * memory * 0.3725']
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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s', '1', '1', '11.41 W', '372.5 mW', '-', '-', '3.17 mWh', '103.47 uWh', 'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'runtime_h * memory * 0.3725']
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
        assert treeMap == ['name':'irreverent_wright-session', 'metaData':['level':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str'], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'irreverent_wright-session', 'type':'str'], 'readable':'irreverent_wright-session'], 'energy_consumption':['raw':['value':0.1796403380907474895898160000, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'179.64 mWh'], 'CO2e':['raw':['value':0.01796403380907474895898160000000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'17.96 mg'], 'CO2e_market':['raw':['value':0.01796403380907474895898160000000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'17.96 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':257.4493173285650600, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'257.45 %'], 'memory':['raw':['value':4294967296, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'4.29 GB'], 'realtime':['raw':['value':14931, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'14s 931ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':''], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.131549392574011545838320000, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'131.55 mWh'], 'raw_energy_memory':['raw':['value':0.00663548288040960000, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'6.64 mWh']], 'children':[
            ['name':'irreverent_wright', 'metaData':['level':'workflow'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.0005376827520064, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'537.68 uWh'], 'CO2e':['raw':['value':0.00005376827520064000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'53.77 ug'], 'CO2e_market':['raw':['value':0.00005376827520064000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'53.77 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':72.3700598802, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'72.37 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':167, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'167ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000413602116928, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'413.6 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                ['name':'sayHello', 'metaData':['level':'process'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.0005376827520064, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'537.68 uWh'], 'CO2e':['raw':['value':0.00005376827520064000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'53.77 ug'], 'CO2e_market':['raw':['value':0.00005376827520064000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'53.77 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':72.3700598802, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'72.37 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':167, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'167ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000413602116928, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'413.6 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                    ['name':'1', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':1, 'type':'str'], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str'], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.0001582376844048, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'158.24 uWh'], 'CO2e':['raw':['value':0.00001582376844048000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'15.82 ug'], 'CO2e_market':['raw':['value':0.00001582376844048000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'15.82 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':74.1, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'74.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':48, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'48ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000121721295696, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'121.72 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                    ['name':'3', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':3, 'type':'str'], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str'], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.000135468357024, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'135.47 uWh'], 'CO2e':['raw':['value':0.0000135468357024000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'13.55 ug'], 'CO2e_market':['raw':['value':0.0000135468357024000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'13.55 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':87.0, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'87 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':35, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'35ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00010420642848, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'104.21 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                    ['name':'4', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':4, 'type':'str'], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str'], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.000154954800, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'154.95 uWh'], 'CO2e':['raw':['value':0.0000154954800000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'15.5 ug'], 'CO2e_market':['raw':['value':0.0000154954800000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'15.5 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':64.5, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'64.5 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':54, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'54ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00011919600, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'119.2 uWh'], 'raw_energy_memory':['raw':['value':0E-13, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                    ['name':'2', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':2, 'type':'str'], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str'], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.0000890219105776, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'89.02 uWh'], 'CO2e':['raw':['value':0.00000890219105776000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'8.9 ug'], 'CO2e_market':['raw':['value':0.00000890219105776000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'8.9 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':66.7, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'66.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':30, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'30ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000068478392752, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'68.48 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]]
                ]]
            ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
