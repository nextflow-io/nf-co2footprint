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
        assert treeMap == ['name':'fervent_noether-session', 'metaData':['workflowLevel':'session'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'fervent_noether-session', 'type':'str', 'description':null], 'readable':'fervent_noether-session'], 'energy_consumption':['raw':['value':0.13694001519007344453763877344000, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'136.94 mWh'], 'CO2e':['raw':['value':0.013694001519007344453763877344000000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'13.69 mg'], 'CO2e_market':['raw':['value':0.013694001519007344453763877344000000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'13.69 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':182.43031204922983, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'182.43 %'], 'memory':['raw':['value':273178624, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'273.18 MB'], 'realtime':['raw':['value':16797, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'16s 797ms'], 'cpus':['raw':['value':7.9591593737, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'7.9591593737'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.1048665374183448180431067488000, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'104.87 mWh'], 'raw_energy_memory':['raw':['value':0.00047193580478860083200, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'471.94 uWh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[
                ['name':'fervent_noether-head_job', 'metaData':['workflowLevel':'head'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'fervent_noether-head_job', 'type':'str', 'description':null], 'readable':'fervent_noether-head_job'], 'energy_consumption':['raw':['value':0.13648888133249904453763877344000, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'136.49 mWh'], 'CO2e':['raw':['value':0.013648888133249904453763877344000000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'13.65 mg'], 'CO2e_market':['raw':['value':0.013648888133249904453763877344000000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'13.65 mg'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':182.92654237487503, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'182.93 %'], 'memory':['raw':['value':273178624, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'273.18 MB'], 'realtime':['raw':['value':16696, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'16s 696ms'], 'cpus':['raw':['value':8, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'8'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.1045195113740568180431067488000, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'104.52 mWh'], 'raw_energy_memory':['raw':['value':0.00047193580478860083200, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'471.94 uWh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                ['name':'fervent_noether', 'metaData':['workflowLevel':'workflow'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello', 'type':'str', 'description':null], 'readable':'sayHello'], 'energy_consumption':['raw':['value':0.0004511338575744, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'451.13 uWh'], 'CO2e':['raw':['value':0.00004511338575744000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'45.11 ug'], 'CO2e_market':['raw':['value':0.00004511338575744000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'45.11 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':100.4000000000, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'100.4 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':101, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'101ms'], 'cpus':['raw':['value':1.2079207921, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.2079207921'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000347026044288, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'347.03 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[
                        ['name':'sayHello', 'metaData':['workflowLevel':'process'], 'values':['task_id':['raw':['value':'-1', 'type':'str', 'description':null], 'readable':'-1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello', 'type':'str', 'description':null], 'readable':'sayHello'], 'energy_consumption':['raw':['value':0.0004511338575744, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'451.13 uWh'], 'CO2e':['raw':['value':0.00004511338575744000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'45.11 ug'], 'CO2e_market':['raw':['value':0.00004511338575744000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'45.11 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':100.4000000000, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'100.4 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':101, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'101ms'], 'cpus':['raw':['value':1.2079207921, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.2079207921'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000347026044288, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'347.03 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[
                                ['name':'3', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':3, 'type':'str', 'description':null], 'readable':'3'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (3)', 'type':'str', 'description':null], 'readable':'sayHello (3)'], 'energy_consumption':['raw':['value':0.000149481812480, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'149.48 uWh'], 'CO2e':['raw':['value':0.0000149481812480000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'14.95 ug'], 'CO2e_market':['raw':['value':0.0000149481812480000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'14.95 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':160.0, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'160 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':21, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'21ms'], 'cpus':['raw':['value':2, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'2'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.0001149860096, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'114.99 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                                ['name':'2', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':2, 'type':'str', 'description':null], 'readable':'2'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (2)', 'type':'str', 'description':null], 'readable':'sayHello (2)'], 'energy_consumption':['raw':['value':0.0000776246014544, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'77.62 uWh'], 'CO2e':['raw':['value':0.00000776246014544000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'7.76 ug'], 'CO2e_market':['raw':['value':0.00000776246014544000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'7.76 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':72.7, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'72.7 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':24, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'24ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000059711231888, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'59.71 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                                ['name':'4', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':4, 'type':'str', 'description':null], 'readable':'4'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (4)', 'type':'str', 'description':null], 'readable':'sayHello (4)'], 'energy_consumption':['raw':['value':0.0000984358126752, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'98.44 uWh'], 'CO2e':['raw':['value':0.00000984358126752000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'9.84 ug'], 'CO2e_market':['raw':['value':0.00000984358126752000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'9.84 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':85.1, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'85.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':26, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'26ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000075719855904, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'75.72 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[]],
                                ['name':'1', 'metaData':['workflowLevel':'task'], 'values':['task_id':['raw':['value':1, 'type':'str', 'description':null], 'readable':'1'], 'status':['raw':['value':'COMPLETED', 'type':'str', 'description':null], 'readable':'COMPLETED'], 'name':['raw':['value':'sayHello (1)', 'type':'str', 'description':null], 'readable':'sayHello (1)'], 'energy_consumption':['raw':['value':0.0001255916309648, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'125.59 uWh'], 'CO2e':['raw':['value':0.00001255916309648000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'12.56 ug'], 'CO2e_market':['raw':['value':0.00001255916309648000, 'type':'Number', 'unit':'g', 'scale':'', 'description':null], 'readable':'12.56 ug'], 'carbon_intensity':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], 'carbon_intensity_market':['raw':['value':100.0, 'type':'Number', 'unit':'gCO₂e/kWh', 'scale':'', 'description':null], 'readable':'100 gCO₂e/kWh'], '%cpu':['raw':['value':94.1, 'type':'Percentage', 'unit':'', 'scale':'%', 'description':null], 'readable':'94.1 %'], 'memory':['raw':['value':0, 'type':'Bytes', 'unit':'B', 'scale':'', 'description':null], 'readable':'0 B'], 'realtime':['raw':['value':30, 'type':'Duration', 'unit':'ms', 'scale':'', 'description':null], 'readable':'30ms'], 'cpus':['raw':['value':1, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1'], 'pue':['raw':['value':1.3, 'type':'Number', 'unit':'', 'scale':'', 'description':null], 'readable':'1.3'], 'powerdraw_cpu':['raw':['value':12.32, 'type':'Number', 'unit':'W', 'scale':'', 'description':null], 'readable':'12.32 W'], 'cpu_model':['raw':['value':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz', 'type':'str', 'description':null], 'readable':'Intel(R) Core(TM) i5-1038NG7 CPU @ 2.00GHz'], 'raw_energy_processor':['raw':['value':0.000096608946896, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'96.61 uWh'], 'raw_energy_memory':['raw':['value':0E-17, 'type':'Number', 'unit':'Wh', 'scale':'', 'description':null], 'readable':'0 Wh'], 'cpu_energy_function':['raw':['value':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage', 'type':'str', 'description':null], 'readable':'runtime_h * numberOfCores * powerdrawPerCore * coreUsage'], 'memory_energy_function':['raw':['value':'runtime_h * memory * 0.3725', 'type':'str', 'description':null], 'readable':'runtime_h * memory * 0.3725']], 'children':[]]
                        ]]
                ]]
        ]], "Java-readable Map representation: ${TestHelper.printify(treeMap)}"
    }
}
