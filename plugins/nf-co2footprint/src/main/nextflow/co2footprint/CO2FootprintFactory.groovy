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
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

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

    // TODO add createCO2ReportObserver() -> html

    @Override
    Collection<TraceObserver> create(Session session) {
        this.session = session
        this.config = new CO2FootprintConfig(session.config.navigate('co2footprint') as Map)

        final result = new ArrayList(2)
        // Generate CO2 footprint text output files
        def co2eFile = (this.config.getFile() as Path).complete()
        def co2eSummaryFile = (this.config.getSummaryFile() as Path).complete()

        result.add( new CO2FootprintTextFileObserver(co2eFile, co2eSummaryFile) )

        // Generate CO2 footprint report with box-plot
        def co2eReport = (CO2FootprintReportObserver.DEF_FILE_NAME as Path).complete()
        result.add( new CO2FootprintReportObserver(co2eReport) )

        return result
    }

}
