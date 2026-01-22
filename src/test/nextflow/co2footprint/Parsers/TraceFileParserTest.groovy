package nextflow.co2footprint.Parsers

import nextflow.trace.TraceRecord
import spock.lang.Specification

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TraceFileParserTest extends Specification {
    Long convertEpochMillisToLocalZone(Long epochMillis, String originalZone) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(originalZone))   // Original Instant
                .withZoneSameLocal(ZoneId.systemDefault()).toInstant().toEpochMilli() // Converted to new EpochMilli
    }

    def 'Test parsing of regular trace file.'() {
        when:
        List<TraceRecord> traceRecords = TraceFileParser.parseExecutionTraceFile(
                this.class.getResource('/execution-trace-regular.tsv').path as Path
        )
        then:
        assert traceRecords.size() == 8
        traceRecords[0].store == [
                task_id:'3', hash:'cb/c73296', native_id:'72563',
                name   :'NFCORE_DEMO:DEMO:SEQTK_TRIM (SAMPLE2_PE)', status:'COMPLETED', exit:'0',
                submit : convertEpochMillisToLocalZone(1760017742707, 'Europe/Berlin'),
                duration:29500, realtime:14000, '%cpu':121.7d, peak_rss:9332326, peak_vmem:20342374,
                rchar  :33764147, wchar:33344716, process:'NFCORE_DEMO:DEMO:SEQTK_TRIM (SAMPLE2_PE)'
        ]
    }

    def 'Test parsing of raw trace file.' () {
        when:
        List<TraceRecord> traceRecords = TraceFileParser.parseExecutionTraceFile(
                this.class.getResource('/execution-trace-raw.tsv').path as Path
        )
        then:
        assert traceRecords.size() == 2
        traceRecords[0].store == [
                task_id:'6', hash:'c6/d3ff54', native_id:'117703',
                name:'NFCORE_RNASEQ:RNASEQ:FASTQ_QC_TRIM_FILTER_SETSTRANDEDNESS:FQ_LINT (RAP1_UNINDUCED_REP1)',
                status:'COMPLETED', exit:'0', submit:1759849601467, duration:524, realtime:0, '%cpu':97.0d, peak_rss:5767168, peak_vmem:10838016,
                rchar:2358732, wchar:1533, start:1759849601546, complete:1759849601991, cpus:2.0d, memory:12884901888,
                process:'NFCORE_RNASEQ:RNASEQ:FASTQ_QC_TRIM_FILTER_SETSTRANDEDNESS:FQ_LINT (RAP1_UNINDUCED_REP1)',
                disk:null, read_bytes:2625536, write_bytes:8192
        ]
    }

}
