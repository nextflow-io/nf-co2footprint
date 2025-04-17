/*
 * Copyright 2021, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.co2footprint

import nextflow.Session
import nextflow.executor.NopeExecutor
import nextflow.processor.TaskId
import nextflow.processor.TaskProcessor
import nextflow.processor.TaskRun
import nextflow.trace.TraceRecord
import spock.lang.Specification

/**
 * This class implements various tests.
 *
 * For testing the actual calculations of the CO2e, energy consumption and equivalence values,
 * the results are compared against values retrieved from http://calculator.green-algorithms.org/ v2.2.
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
class CO2FootprintFactoryTest extends Specification {

    private static BigDecimal round( double value ) {
        Math.round( value * 100 ) / 100
    }

    def 'should return observer' () {
        when:
        def session = Mock(Session) { getConfig() >> [:] }
        def result = new CO2FootprintFactory().create(session)
        then:
        result.size()==2
        result[0] instanceof CO2FootprintFactory.CO2FootprintTextFileObserver
        result[1] instanceof CO2FootprintFactory.CO2FootprintReportObserver
    }

    def 'test co2e calculation with various configurations'() {
        given:
        // Create a TraceRecord object to simulate a task's resource usage
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long) // Task runtime in milliseconds (1 hour)
        traceRecord.cpus = 1 // Number of CPUs used
        traceRecord.cpu_model = cpuModel // CPU model (provided by the test case)
        traceRecord.'%cpu' = 100.0 // CPU utilization percentage
        traceRecord.memory = (7 as Long) * (1000000000 as Long) // Memory usage in bytes (7 GB)

        // Mock a Session object and configure it to return the provided configuration map
        def session = Mock(Session) { getConfig() >> configMap }

        // Create an instance of CO2FootprintFactory and initialize it with the mocked session
        def factory = new CO2FootprintFactory()
        factory.create(session)

        // Compute the CO2 footprint and energy consumption for the given trace record
        def results = factory.computeTaskCO2footprint(traceRecord)

        expect:
        // Assert that the energy consumption (in Wh) matches the expected value
        round(results[0] / 1000) == expectedEnergy

        // Assert that the CO2 emissions (in grams) match the expected value
        round(results[1] / 1000) == expectedCO2

        where:
        cpuModel              | configMap                              | expectedEnergy | expectedCO2
        "Unknown model"       | [:]                                    | 24.1           | 11.57
        "AMD EPYC 7251"       | [:]                                    | 29.11          | 13.97
        "Unknown model"       | [co2footprint: [pue: 1.4]]             | 20.2           | 9.7
        "Unknown model"       | [co2footprint: [location: 'DE']]       | 24.1           | 8.04
        "Unknown model"       | [co2footprint: [ci: 338.66]]           | 24.1           | 8.16

    }


    def 'test calculation of total CO2e and energy consumption' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.task_id = 111
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        def session = Mock(Session) { getConfig() >> [:] }
        // Create a handler
        def task = new TaskRun(id: TaskId.of(111))
        task.processor = Mock(TaskProcessor)
        def handler = new NopeExecutor().createTaskHandler(task)

        def factory = new CO2FootprintFactory()
        def textFileObserver = factory.create(session)[0]

        textFileObserver.onFlowCreate(session)
        textFileObserver.onProcessStart(handler, traceRecord)
        textFileObserver.onProcessComplete(handler, traceRecord)

        expect:
        // Energy consumption converted to Wh
        round(factory.total_energy/1000) == 24.1
        // Total CO2 in g
        round(factory.total_co2/1000) == 11.57
    }

    def 'test calculation of CO2 equivalences' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.task_id = 111
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        def session = Mock(Session) { getConfig() >> [:] }
        // Create a handler
        def task = new TaskRun(id: TaskId.of(111))
        task.processor = Mock(TaskProcessor)
        def handler = new NopeExecutor().createTaskHandler(task)

        def factory = new CO2FootprintFactory()
        def textFileObserver = factory.create(session)[0]

        textFileObserver.onFlowCreate(session)
        textFileObserver.onProcessStart(handler, traceRecord)
        textFileObserver.onProcessComplete(handler, traceRecord)

        def results = factory.computeCO2footprintEquivalences()

        expect:
        // Values compared to result from www.green-algorithms.org
        // Car Km
        results[0].round(7) == 0.0660904 as Double
        // Tree months
        results[1].round(7) == 0.0126127 as Double
        // Plane percent
        results[2].round(7) == 0.0231316 as Double
    }
}
