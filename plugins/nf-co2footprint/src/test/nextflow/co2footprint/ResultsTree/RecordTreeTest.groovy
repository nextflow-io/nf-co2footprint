package nextflow.co2footprint.ResultsTree

import nextflow.co2footprint.Records.CO2Record
import nextflow.trace.TraceRecord
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class RecordTreeTest extends Specification {
    def 'Should construct a valid tree node'() {
        setup:
        Yaml yaml = new Yaml()

        CO2Record co2Record = new CO2Record(
                'test co2', 2.0, 200.0, null, 100.0,
                1.0, 10, 60*60*1000, 1, 7.0, 'Some model'
        )

        when:
        RecordTree parentNode = new RecordTree('parent')
        RecordTree childNode1 = parentNode.addChild(new RecordTree('child'))
        RecordTree childNode2 = parentNode.addChild(new RecordTree('child'))
        childNode1.addChild(new RecordTree('co2', co2Record))
        childNode1.addChild(new RecordTree('trace', new TraceRecord()))
        childNode2.addChild(new RecordTree('co2', co2Record))
        childNode2.addChild(new RecordTree('trace', new TraceRecord()))
        parentNode.summarize()

        then:
        yaml.dump(parentNode.toMap()) ==
                'name:\n' +
                '  raw:\n' +
                '    value: !!set {test co2: null}\n' +
                '    type: LinkedHashSet\n' +
                '  readable: \'[test co2]\'\n' +
                'attributes: {}\n' +
                'children:\n' +
                '- name:\n' +
                '    raw: {value: test co2, type: String}\n' +
                '    readable: test co2\n' +
                '  attributes: {}\n' +
                '  children:\n' +
                '  - name:\n' +
                '      raw: {value: test co2, type: String}\n' +
                '      readable: test co2\n' +
                '    attributes: {}\n' +
                '    children: []\n' +
                '    energy:\n' +
                '      raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '      readable: 2 kWh\n' +
                '    co2e:\n' +
                '      raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '      readable: 200 g\n' +
                '    co2eMarket:\n' +
                '      raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '      readable: \'-\'\n' +
                '    ci:\n' +
                '      raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '      readable: 100 gCO₂e/kWh\n' +
                '    cpuUsage:\n' +
                '      raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '      readable: 1 %\n' +
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer}\n' +
                '      readable: \'1\'\n' +
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
                '  - name: trace\n' +
                '    attributes: {}\n' +
                '    children: []\n' +
                '  energy:\n' +
                '    raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '    readable: 2 kWh\n' +
                '  co2e:\n' +
                '    raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '    readable: 200 g\n' +
                '  co2eMarket:\n' +
                '    raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '    readable: \'-\'\n' +
                '  ci:\n' +
                '    raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '    readable: 100 gCO₂e/kWh\n' +
                '  cpuUsage:\n' +
                '    raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '    readable: 1 %\n' +
                '  memory:\n' +
                '    raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '    readable: 10 GB\n' +
                '  time:\n' +
                '    raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '    readable: 12960000000s\n' +
                '  cpus:\n' +
                '    raw: {value: 1, type: Integer}\n' +
                '    readable: \'1\'\n' +
                '  powerdrawCPU:\n' +
                '    raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '    readable: 7 W\n' +
                '  cpu_model:\n' +
                '    raw: {value: Some model, type: String}\n' +
                '    readable: Some model\n' +
                '- name:\n' +
                '    raw: {value: test co2, type: String}\n' +
                '    readable: test co2\n' +
                '  attributes: {}\n' +
                '  children:\n' +
                '  - name:\n' +
                '      raw: {value: test co2, type: String}\n' +
                '      readable: test co2\n' +
                '    attributes: {}\n' +
                '    children: []\n' +
                '    energy:\n' +
                '      raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '      readable: 2 kWh\n' +
                '    co2e:\n' +
                '      raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '      readable: 200 g\n' +
                '    co2eMarket:\n' +
                '      raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '      readable: \'-\'\n' +
                '    ci:\n' +
                '      raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '      readable: 100 gCO₂e/kWh\n' +
                '    cpuUsage:\n' +
                '      raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '      readable: 1 %\n' +
                '    memory:\n' +
                '      raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '      readable: 10 GB\n' +
                '    time:\n' +
                '      raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '      readable: 12960000000s\n' +
                '    cpus:\n' +
                '      raw: {value: 1, type: Integer}\n' +
                '      readable: \'1\'\n' +
                '    powerdrawCPU:\n' +
                '      raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '      readable: 7 W\n' +
                '    cpu_model:\n' +
                '      raw: {value: Some model, type: String}\n' +
                '      readable: Some model\n' +
                '  - name: trace\n' +
                '    attributes: {}\n' +
                '    children: []\n' +
                '  energy:\n' +
                '    raw: {value: 2000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '    readable: 2 kWh\n' +
                '  co2e:\n' +
                '    raw: {value: 200.0, type: Double, scale: \'\', unit: g}\n' +
                '    readable: 200 g\n' +
                '  co2eMarket:\n' +
                '    raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '    readable: \'-\'\n' +
                '  ci:\n' +
                '    raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '    readable: 100 gCO₂e/kWh\n' +
                '  cpuUsage:\n' +
                '    raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '    readable: 1 %\n' +
                '  memory:\n' +
                '    raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '    readable: 10 GB\n' +
                '  time:\n' +
                '    raw: {value: 12960000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '    readable: 12960000000s\n' +
                '  cpus:\n' +
                '    raw: {value: 1, type: Integer}\n' +
                '    readable: \'1\'\n' +
                '  powerdrawCPU:\n' +
                '    raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '    readable: 7 W\n' +
                '  cpu_model:\n' +
                '    raw: {value: Some model, type: String}\n' +
                '    readable: Some model\n' +
                'energy:\n' +
                '  raw: {value: 4000.0, type: Double, scale: \'\', unit: Wh}\n' +
                '  readable: 4 kWh\n' +
                'co2e:\n' +
                '  raw: {value: 400.0, type: Double, scale: \'\', unit: g}\n' +
                '  readable: 400 g\n' +
                'co2eMarket:\n' +
                '  raw: {value: null, type: Number, scale: m, unit: g}\n' +
                '  readable: \'-\'\n' +
                'ci:\n' +
                '  raw: {value: 100.0, type: Double, scale: \'\', unit: gCO₂e/kWh}\n' +
                '  readable: 100 gCO₂e/kWh\n' +
                'cpuUsage:\n' +
                '  raw: {value: 1.0, type: Double, scale: \'%\', unit: \'\'}\n' +
                '  readable: 1 %\n' +
                'memory:\n' +
                '  raw: {value: 1.073741824E10, type: Double, scale: \'\', unit: B}\n' +
                '  readable: 10 GB\n' +
                'time:\n' +
                '  raw: {value: 25920000000000.0000, type: BigDecimal, scale: ms, unit: \'\'}\n' +
                '  readable: 25920000000s\n' +
                'cpus:\n' +
                '  raw: {value: 1, type: Integer}\n' +
                '  readable: \'1\'\n' +
                'powerdrawCPU:\n' +
                '  raw: {value: 7.0, type: Double, scale: \'\', unit: W}\n' +
                '  readable: 7 W\n' +
                'cpu_model:\n' +
                '  raw:\n' +
                '    value: !!set {Some model: null}\n' +
                '    type: LinkedHashSet\n' +
                '  readable: \'[Some model]\'\n'
    }
}
