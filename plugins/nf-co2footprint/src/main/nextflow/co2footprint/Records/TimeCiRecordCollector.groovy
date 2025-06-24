package nextflow.co2footprint.Records

import groovy.util.logging.Slf4j
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.utils.Converter
import nextflow.exception.MissingValueException
import nextflow.trace.TraceRecord

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

@Slf4j
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
        this.timeCIs.putAll( [(timeCi['time'] as LocalDateTime): timeCi['ci'] as Double] )
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
            log.trace("Started periodically fetching the CI every ${Converter.toReadableTimeUnits(period, 'ms')}")
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

        // Obtain recorded star, end, and duration
        LocalDateTime start = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(trace.get('start') as Long),
                ZoneId.systemDefault()
        )
        LocalDateTime end = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(trace.get('complete') as Long),
                ZoneId.systemDefault()
        )
        Long duration = start.until(end, ChronoUnit.MILLIS)

        // Construct before, during and after key sets
        Set<LocalDateTime> during = new HashSet<LocalDateTime>(timeCIs.keySet())

        // Remove values from before & after out of the fully covered CI values
        Set<LocalDateTime> before = during.findAll {LocalDateTime time -> time <= start}
        during.removeAll(before)
        Set<LocalDateTime> after = during.findAll {LocalDateTime time -> time >= end}
        during.removeAll(after)

        /**
         * Return the weight of as the covered fraction of the total duration
         */
        Closure<Double> getWeight = { LocalDateTime time1, LocalDateTime time2 ->
            (time1.until(time2, ChronoUnit.MILLIS)) / duration
        }

        // Calculation of average carbon intensity
        Double averageCi = 0d

        if (during) {
            if (before) {
                averageCi += timeCIs.get(before.max()) * getWeight(start, during.min())
            }
            else {
                averageCi += timeCIs.get(during.min()) * getWeight(start, during.min())
            }

            LocalDateTime last = during.max()
            during.remove(last)
            if (during) {
                // Add weighted average of fully covered duration
                averageCi += timeCIs.subMap(during).values().average() * getWeight(during.min(), last)
            }

            // Add overhang
            averageCi += timeCIs.get(last) * getWeight(last, end)
        }
        else if (before) {
            averageCi += timeCIs.get(before.max()) * getWeight(start, end)
        }
        else if (after) {
            averageCi += timeCIs.get(after.min()) * getWeight(start, end)
        }
        else {
            String message = "Found no timestamps in timeCIs '${timeCIs}' that are " +
                    "before, during or after the frame of the traceRecord: '${trace}' (${start}-${end})."
            log.error(message)
            throw new MissingValueException(message)
        }

        return averageCi
    }
}
