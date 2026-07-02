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
        assert treeMap == ['name':'distracted_cajal-session', 'metaData':['level':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str'], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'distracted_cajal-session', 'type':'str'], 'readable':'distracted_cajal-session'], 'energy_consumption':['raw':['value':0.39028706283982370476599794352000, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'390.29 mWh'], 'CO2e':['raw':['value':0.039028706283982370476599794352000000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'39.03 mg'], 'CO2e_market':['raw':['value':0.039028706283982370476599794352000000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'39.03 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':262.0112159548632300, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'262.01 %'], 'memory':['raw':['value':4294967296, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'4.29 GB'], 'realtime':['raw':['value':31901, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'31s 901ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':''], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.2860437001869127206483061104000, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'286.04 mWh'], 'raw_energy_memory':['raw':['value':0.01417711738218243686400, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'14.18 mWh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[
                ['name':'distracted_cajal', 'metaData':['level':'workflow'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.000402393776825040, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'402.39 uWh'], 'CO2e':['raw':['value':0.0000402393776825040000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'40.24 ug'], 'CO2e_market':['raw':['value':0.0000402393776825040000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'40.24 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':78.65043478261391, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'78.65 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':115, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'115ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00030953367448080, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'309.53 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[
                        ['name':'sayHello', 'metaData':['level':'process'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.000402393776825040, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'402.39 uWh'], 'CO2e':['raw':['value':0.0000402393776825040000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'40.24 ug'], 'CO2e_market':['raw':['value':0.0000402393776825040000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'40.24 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':78.65043478261391, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'78.65 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':115, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'115ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00030953367448080, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'309.53 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[
                                ['name':'3', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':3, 'type':'str'], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str'], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.0001410701227936, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'141.07 uWh'], 'CO2e':['raw':['value':0.00001410701227936000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'14.11 ug'], 'CO2e_market':['raw':['value':0.00001410701227936000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'14.11 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':85.7, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'85.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':37, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'37ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000108515479072, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'108.52 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                                ['name':'4', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':4, 'type':'str'], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str'], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.000069473857573120, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'69.47 uWh'], 'CO2e':['raw':['value':0.0000069473857573120000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.95 ug'], 'CO2e_market':['raw':['value':0.0000069473857573120000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.95 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':97.6, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'97.6 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':16, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'16ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00005344142890240, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'53.44 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                                ['name':'2', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':2, 'type':'str'], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str'], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.000107320551658320, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'107.32 uWh'], 'CO2e':['raw':['value':0.0000107320551658320000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'10.73 ug'], 'CO2e_market':['raw':['value':0.0000107320551658320000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'10.73 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':56.1, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'56.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':43, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'43ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.00008255427050640, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'82.55 uWh'], 'raw_energy_memory':['raw':['value':0E-19, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                                ['name':'1', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':1, 'type':'str'], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str'], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.000084529244800, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'84.53 uWh'], 'CO2e':['raw':['value':0.0000084529244800000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'8.45 ug'], 'CO2e_market':['raw':['value':0.0000084529244800000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'8.45 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':100.0, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'100 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':19, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'19ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000065022496, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'65.02 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str'], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str'], 'readable':'runtime_h * memory * 0.3725']], 'children':[]]
                        ]]
                ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
