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
import nextflow.trace.TraceRecord
import spock.lang.Specification

/**
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
class CO2FootprintFactoryTest extends Specification {

    private BigDecimal round( double value ) {
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

    def 'test co2e calculation' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        def session = Mock(Session) { getConfig() >> [:] }
        def factory = new CO2FootprintFactory()
        factory.create(session)
        def results = factory.computeTaskCO2footprint(traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(results[0]/1000) == 24.39
        // CO2 converted to g
        round(results[1]/1000) == 11.59
    }

    def 'test co2e calculation with non-default pue' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        def session = Mock(Session) { getConfig() >> [co2footprint: [pue: 1.4]] }
        def factory = new CO2FootprintFactory()
        factory.create(session)
        def results = factory.computeTaskCO2footprint(traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(results[0]/1000) == 20.45
        // CO2 in g
        round(results[1]/1000) == 9.71
    }

    def 'test co2e calculation with CI value retrieved for Germany' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        def session = Mock(Session) { getConfig() >> [co2footprint: [country: 'Germany']] }
        def factory = new CO2FootprintFactory()
        factory.create(session)
        def results = factory.computeTaskCO2footprint(traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org
        round(results[0]/1000) == 24.39
        // CO2 in g
        round(results[1]/1000) == 8.26
    }

    def 'test co2e calculation for custom CI value' () {
        given:
        def traceRecord = new TraceRecord()
        traceRecord.realtime = (1 as Long) * (3600000 as Long)
        traceRecord.cpus = 1
        traceRecord.cpu_model = "Unknown model"
        traceRecord.'%cpu' = 100.0
        traceRecord.memory = (7 as Long) * (1000000000 as Long)

        // Using current CI value for Germany, but passed over directly as CI value
        def session = Mock(Session) { getConfig() >> [co2footprint: [ci: 338.66]] }
        def factory = new CO2FootprintFactory()
        factory.create(session)
        def results = factory.computeTaskCO2footprint(traceRecord)

        expect:
        // Energy consumption converted to Wh and compared to result from www.green-algorithms.org (for location Germany)
        round(results[0]/1000) == 24.39
        // CO2 in g
        round(results[1]/1000) == 8.26
    }
}
