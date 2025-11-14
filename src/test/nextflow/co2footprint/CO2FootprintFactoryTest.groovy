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
import nextflow.trace.TraceObserver
import spock.lang.Specification

/**
 * This class implements various tests.
 *
 * For testing the actual calculations of the CO₂e, energy consumption and equivalence values,
 * the results are compared against values retrieved from http://calculator.green-algorithms.org/ v2.2.
 *
 * @author Júlia Mir Pedrol <mirp.julia@gmail.com>, Sabrina Krakau <sabrinakrakau@gmail.com>
 */
class CO2FootprintFactoryTest extends Specification {

    def 'create observer' () {
        when:
        Session session = Mock(Session) { getConfig() >>  [:] }
        List<TraceObserver> observers = new CO2FootprintFactory().create(session)

        then:
        observers[0] instanceof  CO2FootprintObserver
        observers.size() == 1
    }

    def 'check version' () {
        when:
        CO2FootprintFactory factory = new CO2FootprintFactory()
        factory.setPluginVersion()
        String pluginVersion = factory.getPluginVersion()

        then:
        pluginVersion == "1.1.0"
    }
}
