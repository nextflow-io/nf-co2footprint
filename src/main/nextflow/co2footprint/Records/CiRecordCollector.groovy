package nextflow.co2footprint.Records

import groovy.util.logging.Slf4j
import nextflow.co2footprint.CO2FootprintConfig
import nextflow.co2footprint.Metrics.Duration
import nextflow.exception.MissingValueException
import nextflow.trace.TraceRecord

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects and manages time-resolved carbon intensity (CI) values for workflow tasks.
 *
 * The CiRecordCollector class:
 *   - Periodically fetches and stores carbon intensity values (e.g., from an API) with timestamps.
 *   - Maintains a time-indexed map of CI values for use in carbon footprint calculations.
 *   - Provides methods to add new CI records, start/stop periodic updates, and retrieve CI values.
 *   - Calculates the weighted average CI for a given task based on its runtime and the recorded CI values.
 */
@Slf4j
class CiRecordCollector {
    // Timer for cyclical tasks
    private Timer timer = new Timer(true) // true = daemon thread

    // CI values
    private ConcurrentHashMap<LocalDateTime, Number> timeCIs

    // Config
    private CO2FootprintConfig config
    
    /**
     * Constructor for CiRecordCollector.
     *
     * @param config Configuration instance with CI settings
     * @param timeCIs Optional initial time CI map (default: empty ConcurrentHashMap)
     */
    CiRecordCollector(CO2FootprintConfig config, ConcurrentHashMap<LocalDateTime, Number> timeCIs=[:] as ConcurrentHashMap) {
        this.config = config
        this.timeCIs = timeCIs as ConcurrentHashMap
    }

    /**
     * Returns the time CIs map.
     *
     * @return ConcurrentHashMap of LocalDateTime to Double representing carbon intensity values
     */
    ConcurrentHashMap<LocalDateTime, Number> getTimeCIs() { timeCIs }

    /**
     * Returns the carbon intensity (CI) for a given trace record.
     * If timeCIs is set, it calculates the weighted CI based on the trace record's runtime.
     * Otherwise, it returns the CI value set in the config.
     *
     * @param traceRecord The trace record containing task start and end times
     * @return The carbon intensity value for the trace record
     */
    Number getCi(TraceRecord traceRecord) { this.timeCIs ? getWeightedCI(traceRecord) : config.ci.value}

    /**
     * Adds time CI pairs to CI record
     *
     * @param timeCi Map of LocalDateTime and Double that is added to the CI record
     */
    protected void add(CiRecord ciRecord=this.config.ci) {
        ciRecord.update()
        this.timeCIs.putAll( [(ciRecord.time): ciRecord.value] )
    }

    /**
     * Start the update process with new CI values if ci is a function
     *
     * @param config Configuration instance with ci value / function (default: this.config)
     * @param delay Delay until scheduling starts (default: 0)
     * @param period Period of scheduled repetition (default: 1 hour)
     */
    void start(CO2FootprintConfig config=this.config, Integer delay=0, Integer period=1000*60*60) {
        if ( config.usesAPI() ) {
            log.trace("Started periodically fetching the CI every ${new Duration(period, 'ms').toReadable()}")
            timer.scheduleAtFixedRate(new TimerTask() {
                void run() {
                    add(config.ci)
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
    * Calculates the weighted average carbon intensity (CI) for a given task based on its runtime and the available time-resolved CI values.
    *
    * The method determines which CI values from the provided timeCIs map overlap with the task's execution window (from start to end time).
    * It then computes a weighted average, where each CI value is weighted by the fraction of the task's duration it covers.
    * - If the task overlaps multiple CI intervals, each interval's CI is weighted accordingly.
    * - If the task starts before or ends after the available CI intervals, the nearest CI value is used for the uncovered portion.
    * - If no suitable CI values are found, a MissingValueException is thrown.
    *
    * @param trace   The TraceRecord containing the task's start and end times (in milliseconds since epoch)
    * @param timeCIs Map of LocalDateTime to Double representing timestamped CI values (defaults to this.timeCIs)
    * @return        The weighted average carbon intensity for the task's runtime
    * @throws        MissingValueException if no CI values are available for the task's time window
    */
    Double getWeightedCI(TraceRecord trace, Map<LocalDateTime, Number> timeCIs=this.timeCIs) {

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
        List<LocalDateTime> timestamps = timeCIs.keySet().toList().sort()

        /**
         * Return the weight of as the covered fraction of the total duration
         */
        Closure<Double> getWeight = { LocalDateTime time1, LocalDateTime time2 ->
            (time1.until(time2, ChronoUnit.MILLIS)) / duration
        }

        if (!timestamps) {
            String message = "Found no timestamps in timeCIs '${timeCIs}' that are " +
                    "before, during or after the frame of the traceRecord: '${trace}' (${start}-${end})."
            log.error(message)
            throw new MissingValueException(message)
        }

        LocalDateTime activeTimestamp = timestamps.findAll { LocalDateTime time -> time <= start }.max()
        if (activeTimestamp == null) {
            activeTimestamp = timestamps.min()
        }

        List<LocalDateTime> changesDuringRun = timestamps.findAll { LocalDateTime time -> time > start && time < end }

        // Calculation of average carbon intensity
        Double averageCi = 0d
        LocalDateTime segmentStart = start

        changesDuringRun.each { LocalDateTime changeTime ->
            averageCi += (timeCIs.get(activeTimestamp) as Double) * getWeight(segmentStart, changeTime)
            segmentStart = changeTime
            activeTimestamp = changeTime
        }

        averageCi += (timeCIs.get(activeTimestamp) as Double) * getWeight(segmentStart, end)

        return averageCi
    }
}
