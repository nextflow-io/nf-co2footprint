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
        assert treeMap == ['name':'tiny_meninsky-session', 'metaData':['level':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str'], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'tiny_meninsky-session', 'type':'str'], 'readable':'tiny_meninsky-session'], 'energy_consumption':['raw':['value':0.20202046739258245, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'202.02 mWh'], 'CO2e':['raw':['value':0.0202020467392582454447643851520000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'20.2 mg'], 'CO2e_market':['raw':['value':0.0202020467392582454447643851520000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'20.2 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':203.1459057530932400, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'203.15 %'], 'memory':['raw':['value':4294967296, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'4 GB'], 'realtime':['raw':['value':21097, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'21s 97ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':''], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.14666854561075574, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'146.67 mWh'], 'raw_energy_memory':['raw':['value':0.008731813922, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'8.73 mWh']], 'children':[
            ['name':'tiny_meninsky', 'metaData':['level':'workflow'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.00036475188493743997, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'364.75 uWh'], 'CO2e':['raw':['value':0.00003647518849374400000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'36.48 ug'], 'CO2e_market':['raw':['value':0.00003647518849374400000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'36.48 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':93.1670454546, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'93.17 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':88, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'88ms'], 'cpus':['raw':['value':1.1818181818, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.1818181818'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0002805783730288, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'280.58 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                ['name':'sayHello', 'metaData':['level':'process'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.00036475188493743997, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'364.75 uWh'], 'CO2e':['raw':['value':0.00003647518849374400000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'36.48 ug'], 'CO2e_market':['raw':['value':0.00003647518849374400000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'36.48 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':93.1670454546, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'93.17 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':88, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'88ms'], 'cpus':['raw':['value':1.1818181818, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.1818181818'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0002805783730288, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'280.58 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                    ['name':'1', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':1, 'type':'str'], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str'], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.00011584939766399999, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'115.85 uWh'], 'CO2e':['raw':['value':0.0000115849397664000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'11.58 ug'], 'CO2e_market':['raw':['value':0.0000115849397664000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'11.58 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':93.0, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'93 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':28, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'28ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00008911492127999999, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'89.11 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                    ['name':'3', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':3, 'type':'str'], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str'], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.00009921466426872, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'99.21 uWh'], 'CO2e':['raw':['value':0.0000099214664268720000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'9.92 ug'], 'CO2e_market':['raw':['value':0.0000099214664268720000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'9.92 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':76.9, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'76.9 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':29, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'29ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0000763189725144, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'76.32 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                    ['name':'2', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':2, 'type':'str'], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str'], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.000062062496496, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'62.06 uWh'], 'CO2e':['raw':['value':0.0000062062496496000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.21 ug'], 'CO2e_market':['raw':['value':0.0000062062496496000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.21 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':93.0, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'93 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':15, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'15ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00004774038192, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'47.74 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                    ['name':'4', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':4, 'type':'str'], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str'], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.00008762532650872, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'87.63 uWh'], 'CO2e':['raw':['value':0.00000876253265087200000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'8.76 ug'], 'CO2e_market':['raw':['value':0.00000876253265087200000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'8.76 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':123.1, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'123.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':16, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'16ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':''], 'readable':'2'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0000674040973144, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'67.4 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]]
                ]]
            ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
