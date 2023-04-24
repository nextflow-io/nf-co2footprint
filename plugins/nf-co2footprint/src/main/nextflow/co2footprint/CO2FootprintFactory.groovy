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

import java.nio.file.Path

import groovy.transform.CompileStatic
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import nextflow.trace.TraceFileObserver

/**
 * Implements the validation observer factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class CO2FootprintFactory implements TraceObserverFactory {

    private Map config
    private Session session

    // TODO add createCO2ReportObserver() -> html

    @Override
    Collection<TraceObserver> create(Session session) {
        this.session = session
        this.config = session.config

        String fileName = CO2FootprintObserver.DEF_FILE_NAME
        String summaryFileName = CO2FootprintObserver.DEF_SUMMARY_FILE_NAME
        def co2eFile = (fileName as Path).complete()
        def co2eSummaryFile = (summaryFileName as Path).complete()

        final result = new ArrayList(2)
        result.add( new CO2FootprintObserver(co2eFile, co2eSummaryFile) )

        return result
    }

}
