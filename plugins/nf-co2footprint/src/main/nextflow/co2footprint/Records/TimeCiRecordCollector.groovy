package nextflow.co2footprint.Records

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.trace.TraceRecord

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class TimeCiRecordCollector {
    // Timer for cyclical tasks
    private Timer timer = new Timer(true) // true = daemon thread

    // CI values
    private ConcurrentHashMap<LocalDateTime, Double> timeCIs

    // Config
    private CO2FootprintConfig config

    TimeCiRecordCollector(CO2FootprintConfig config, ConcurrentHashMap<LocalDateTime, Double> timeCIs=[:] as ConcurrentHashMap) {
        this.config = config
        this.timeCIs = timeCIs as ConcurrentHashMap
    }

    ConcurrentHashMap<LocalDateTime, Double> getTimeCIs() { timeCIs }
    Double getCI(TraceRecord traceRecord) { this.timeCIs ? getWeightedCI(traceRecord) : config.getCi() }

    /**
     * Adds time CI pairs to CI record
     *
     * @param timeCi Map of LocalDateTime and Double that is added to the CI record
     */
    protected void add(Map<String, ?> timeCi=this.config.getTimeCi()) {
        this.timeCIs.putAll(
                [(timeCi['time'] as LocalDateTime): timeCi['ci'] as Double]
        )
    }

    /**
     * Start the update process with new CI values if ci is a function
     *
     * @param config Configuration instance with ci value / function (default: this.config)
     * @param delay Delay until scheduling starts (default: 0)
     * @param period Period of scheduled repetition (default: 1 hour)
     */
    void start(CO2FootprintConfig config=this.config, Integer delay=0, Integer period=1000*60*60) {
        if ( config.isCIAPICalled() ) {
            timer.scheduleAtFixedRate(new TimerTask() {
                void run() {
                    add(config.getTimeCi())
                }
            }, delay, period)
        }
    }

    /**
     * Stops the CI value accumulation
     */
    void stop() {
        timer.cancel()
        timer.purge()
    }

    /**
     * Calculates the weighted average carbon intensity
     *
     * @param trace A trace record with starting and end time
     * @param timeCIs The timestamped CI values
     * @return The weighted averaged of the carbon intensity (CI)
     */
    Double getWeightedCI(TraceRecord trace, Map<LocalDateTime, Double> timeCIs=this.timeCIs) {
        LocalDateTime start = LocalDateTime.parse(trace.get('start') as String)
        LocalDateTime end = LocalDateTime.parse(trace.get('complete') as String)
        Long duration = start.until(end, ChronoUnit.MILLIS)

        Double averageCi = 0d

        // Construct before, during and after key sets
        Set<LocalDateTime> during = timeCIs.keySet()

        Set<LocalDateTime> before = during.findAll {LocalDateTime time -> time < start} // find all time-CI pairs before the start
        during.removeAll(before)    // Remove values from before from the fully covered CI values
        before ?= [during.min()]    // Catch if no CI values were recorded before the start

        Set<LocalDateTime> after = during.findAll {LocalDateTime time -> time > end}
        during.removeAll(after)
        after ?= [during.max()]

        Integer distance = 0
        // Add edge cases (start & end) to average ci by weight
        averageCi += timeCIs.get(before.max()) * (start.until(during.min(), ChronoUnit.MILLIS) / duration)
        averageCi += timeCIs.get(after.min()) * (during.max().until(end, ChronoUnit.MILLIS) / duration)

        // Add weighted average of fully covered duration
        averageCi += timeCIs.subMap(during).values().average() * (during.min().until(during.max(), ChronoUnit.MILLIS) / duration)


        return averageCi
    }
}
