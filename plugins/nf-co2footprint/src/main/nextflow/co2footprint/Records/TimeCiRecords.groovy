package nextflow.co2footprint.Records

import nextflow.co2footprint.CO2FootprintConfig
import nextflow.trace.TraceRecord

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class TimeCiRecords {
    // Timer for cyclical tasks
    private Timer timer = new Timer(true) // true = daemon thread

    // CI values
    private ConcurrentHashMap<LocalDateTime, Double> timeCi

    // Config
    private CO2FootprintConfig config

    TimeCiRecords(CO2FootprintConfig config, ConcurrentHashMap<LocalDateTime, Double> timeCi=[:] as ConcurrentHashMap) {
        this.config = config
        this.timeCi = timeCi as ConcurrentHashMap
    }

    ConcurrentHashMap<LocalDateTime, Double> getTimeCi() { timeCi }

    /**
     * Adds time CI pairs to CI record
     *
     * @param timeCi Map of LocalDateTime and Double that is added to the CI record
     */
    private void add(Map<LocalDateTime, Double> timeCi=this.config.getTimeCi()) {
        this.timeCi.putAll(timeCi)
    }

    /**
     * Start the update process with new CI values if ci is a function
     *
     * @param config Configuration instance with ci value / function
     */
    void start(CO2FootprintConfig config=this.config) {
        if (config.ci instanceof Closure) {
            timer.scheduleAtFixedRate(new TimerTask() {
                void run() {
                    add(config.getTimeCi())
                }
            }, 0, 1000 * 60 * 60) // Delay = 0ms, repeat every 1 hour
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
     * Returns the average carbon intensity
     * @param trace
     * @return
     */
    Double getAverageCI(TraceRecord trace, Map<LocalDateTime, Double> timeCi=this.timeCi) {
        LocalDateTime start = LocalDateTime.parse(trace.get('start') as String)
        LocalDateTime end = LocalDateTime.parse(trace.get('complete') as String)
        Long duration = start.until(end, ChronoUnit.MILLIS)

        Double averageCi = 0d

        // Construct before, during and after key sets
        Set<LocalDateTime> during = timeCi.keySet().clone() as Set<LocalDateTime>
        Set<LocalDateTime> before = during.findAll {LocalDateTime time -> time < start}
        during.removeAll(before)
        Set<LocalDateTime> after = during.findAll {LocalDateTime time -> time > end}
        during.removeAll(after)

        // Add edge cases (start & end) to average ci by weight
        averageCi += timeCi.get(before.max()) * (start.until(during.min(), ChronoUnit.MILLIS) / duration)
        averageCi += timeCi.get(after.min()) * (during.max().until(end, ChronoUnit.MILLIS) / duration)

        // Add weighted average of fully covered duration
        averageCi += timeCi.subMap(during).values().average() * (during.min().until(during.max(), ChronoUnit.MILLIS) / duration)


        return averageCi
    }
}
