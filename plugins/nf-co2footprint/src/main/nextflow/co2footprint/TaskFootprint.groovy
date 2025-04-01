package nextflow.co2footprint

import com.sun.management.OperatingSystemMXBean
import nextflow.trace.TraceRecord

import java.lang.management.ManagementFactory

class TaskFootprint {

    TraceRecord trace
    CO2FootprintConfig config

    Double energy
    Double co2Emissions
    Double realtime
    Double numberOfCores
    Double powerdrawCore
    Double cpuUsage
    Long memory

    TaskFootprint(TraceRecord trace, CO2FootprintConfig config) {
        this.trace = trace
        this.config = config
    }

    // Core function to compute CO2 emissions for each task
    void computeTaskCO2footprint() {
        // C = t * (nc * Pc * uc + nm * Pm) * PUE * CI * 0.001
        // as in https://doi.org/10.1002/advs.202100707
        // PSF: pragmatic scaling factor -> not used here since we aim at the CO2e of one pipeline run
        // Factor 0.001 needed to convert Pc and Pm from W to kW
    }

    void computeCO2footprintEquivalences() {
        /*
         * The following values were taken from the Green Algorithms publication (https://doi.org/10.1002/advs.202100707):
         * The estimated emission of the average passenger car is 175 gCO2e/Km in Europe and 251 gCO2/Km in the US
         * The estimated emission of flying on a jet aircraft in economy class is between 139 and 244 gCO2e/Km
         * The estimated sequestered CO2 of a mature tree is ~1 Kg per month (917 g)
         * A reference flight Paris to London spends 50000 gCO2
         */
    }
}
