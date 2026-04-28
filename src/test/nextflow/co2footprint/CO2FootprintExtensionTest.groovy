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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s 0ms', '1', '1', '11.41 W', '372.5 mW', '-', '-', '3.17 mWh', '103.47 uWh']
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
        co2Records[7].getReadableEntries() == ['8', 'COMPLETED', 'VALUE_TESTING', '3.27 mWh', '327.29 ug', '-', '100 gCO₂e/kWh', '-', '100 %', '1 GB', '1s 0ms', '1', '1', '11.41 W', '372.5 mW', '-', '-', '3.17 mWh', '103.47 uWh']
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
        fileChecker.checkIsFile(provenancePath)

        // Due to rounding errors in large calculations, there can be small deviations to the original file, but it remains constant.
        // Also realtime seems to sometimes loose a second, but I don't know why.
        fileChecker.compareChecksums(provenancePath, '893aebabe163721e8d21913539b7fd63')
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
        assert treeMap == ['name':'adoring_goldberg-session', 'metaData':['level':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str'], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'adoring_goldberg-session', 'type':'str'], 'readable':'adoring_goldberg-session'], 'energy_consumption':['raw':['value':0.1463454359886484, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'146.35 mWh'], 'CO2e':['raw':['value':0.0146345435988648407741235166720000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'14.63 mg'], 'CO2e_market':['raw':['value':0.0146345435988648407741235166720000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'14.63 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':184.91625293562532, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'184.92 %'], 'memory':['raw':['value':4294967296, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'4 GB'], 'realtime':['raw':['value':16697.0001600000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'16s 697ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':''], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.1056627094549603, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'105.66 mWh'], 'raw_energy_memory':['raw':['value':0.0069107028440000004, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'6.91 mWh']], 'children':[
                ['name':'adoring_goldberg', 'metaData':['level':'workflow'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.0009044784180432, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'904.48 uWh'], 'CO2e':['raw':['value':0.000090447841804320000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'90.45 ug'], 'CO2e_market':['raw':['value':0.000090447841804320000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'90.45 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':79.1066238496126887431256916331791872697474, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'79.11 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':257.0004000000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'257ms'], 'cpus':['raw':['value':1.077821513118265750325680426596954712911, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.077821513118265750325680426596954712911'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000695752629264, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'695.75 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                        ['name':'sayHello', 'metaData':['level':'process'], 'values':['status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'energy_consumption':['raw':['value':0.0009044784180432, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'904.48 uWh'], 'CO2e':['raw':['value':0.000090447841804320000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'90.45 ug'], 'CO2e_market':['raw':['value':0.000090447841804320000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'90.45 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':79.1066238496126887431256916331791872697474, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'79.11 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':257.0004000000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'257ms'], 'cpus':['raw':['value':1.077821513118265750325680426596954712911, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.077821513118265750325680426596954712911'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000695752629264, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'695.75 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[
                                ['name':'4', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':4, 'type':'str'], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str'], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.0000958298332992, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'95.83 uWh'], 'CO2e':['raw':['value':0.000009582983329920000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'9.58 ug'], 'CO2e_market':['raw':['value':0.000009582983329920000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'9.58 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':107.7, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'107.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':20.0001600000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'20ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':''], 'readable':'2'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000073715256384, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'73.72 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                                ['name':'1', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':1, 'type':'str'], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str'], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.0006818549465728, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'681.85 uWh'], 'CO2e':['raw':['value':0.00006818549465728000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'68.19 ug'], 'CO2e_market':['raw':['value':0.00006818549465728000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'68.19 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':82.4, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'82.4 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':186.0001200000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'186ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000524503805056, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'524.5 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                                ['name':'2', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':2, 'type':'str'], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str'], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.0000609678781712, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'60.97 uWh'], 'CO2e':['raw':['value':0.00000609678781712000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.1 ug'], 'CO2e_market':['raw':['value':0.00000609678781712000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.1 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':57.1, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'57.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':24.0001200000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'24ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000046898367823999995, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'46.9 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]],
                                ['name':'3', 'metaData':['level':'task'], 'values':['task_id':['raw':['value':3, 'type':'str'], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str'], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str'], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.00006582576, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'65.83 uWh'], 'CO2e':['raw':['value':0.00000658257600000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.58 ug'], 'CO2e_market':['raw':['value':0.00000658257600000, 'type':'Number', 'unit':'g', 'scale':''], 'readable':'6.58 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':''], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':54.8, 'type':'Percentage', 'unit':'', 'scale':'%'], 'readable':'54.8 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':''], 'readable':'0 B'], 'realtime':['raw':['value':27.0000000000, 'type':'Duration', 'unit':'ms', 'scale':''], 'readable':'27ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':''], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'12.32 W'], 'powerdraw_memory':['raw':['value':0.3725, 'type':'Number', 'unit':'W', 'scale':''], 'readable':'372.5 mW'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str'], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0000506352, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'50.64 uWh'], 'raw_energy_memory':['raw':['value':0E-14, 'type':'Number', 'unit':'Wh', 'scale':''], 'readable':'0 Wh']], 'children':[]]
                        ]]
                ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
