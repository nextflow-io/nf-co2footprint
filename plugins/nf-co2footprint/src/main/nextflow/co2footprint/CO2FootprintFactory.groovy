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

import groovy.transform.PackageScope
import nextflow.trace.TraceRecord

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import nextflow.processor.TaskId

import java.util.concurrent.ConcurrentHashMap

/**
 * Implements the validation observer factory
 *
 * @author Sabrina Krakau <sabrinakrakau@gmail.com>
 */
@Slf4j
@CompileStatic
class CO2FootprintFactory implements TraceObserverFactory {

    private CO2FootprintConfig config
    private Session session
    public  CO2Records recs = new CO2Records()
    public int co2eVal  // NOTE: works somehow! setProperty different?

    @Override
    Collection<TraceObserver> create(Session session) {
        this.session = session
        this.config = new CO2FootprintConfig(session.config.navigate('co2footprint') as Map)

        final result = new ArrayList(2)
        // Generate CO2 footprint text output files
        def co2eFile = (this.config.getFile() as Path).complete()
        def co2eSummaryFile = (this.config.getSummaryFile() as Path).complete()

        // USe closure to update CO2eRecords from CO2FootprintTextFileObserver class
        // (and pass over to CO2FootprintReportObserver to avoid redundant computations)
        def co2eCl = { String task_id, Float co2e ->
//                def newRecs = new CO2Records()
                recs.co2eRecords['a'] = (Float)44.5
//                recs.co2eRecords = newRecs.co2eRecords
        }
        co2eCl.resolveStrategy = Closure.DELEGATE_FIRST
        co2eCl.delegate = this
        result.add( new CO2FootprintTextFileObserver(co2eFile, co2eSummaryFile, co2eCl) )
        log.info "Test 2  ${this.recs.co2eRecords}"

        // Generate CO2 footprint report with box-plot
        def co2eReport = (CO2FootprintReportObserver.DEF_FILE_NAME as Path).complete()
        result.add( new CO2FootprintReportObserver(co2eReport) )

        return result
    }

}
