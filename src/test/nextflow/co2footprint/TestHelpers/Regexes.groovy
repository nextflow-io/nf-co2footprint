package nextflow.co2footprint.TestHelpers

enum Regexes {
    final static String readAbleDateTimeReport = /"type":"DateTime","unit":"ms","description":"Unix time","scale":""\},"readable":("\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.?\d*")}/
    
    final static String traceConfigPostRun = '<dd><pre class="nfcommand"><code>nextflow plugin nf-co2footprint:postRun --tracePath (.+?) --config (.+?)</code></pre></dd>'

    final static String summaryProvenanceFile = 'provenanceFile: (.+)$'
    final static String summaryTraceFile = 'traceFile: (.+)$'
    final static String summaryReportFile = 'reportFile: (.+)$'
    final static String summarySummaryFile = 'summaryFile: (.+)$'
    
    final static String filesReportFile = /\{"option":"provenanceFile","value":"([^"]*)"\}.*?/ +
        /\{"option":"reportFile","value":"([^"]*)"\}.*?/ +
        /\{"option":"summaryFile","value":"([^"]*)"\}.*?/ +
        /\{"option":"traceFile","value":"([^"]*)"\}/
    final static String workflowStartEndReportFile = '<span id="workflow_start">(.*?)</span> - <span id="workflow_complete">(.*?)</span>'
}
