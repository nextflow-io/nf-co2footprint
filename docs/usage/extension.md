### Extension points
The plugin provides some extension points to facilitate interaction with the plugin from within Nextflow pipeline runs.

The interaction follows the [Nextflow extension function schema](https://nextflow.io/docs/latest/plugins/developing-plugins.html).
```Nextflow
include { calculateCO2 } from 'plugin/nf-co2footprint'

process calculate_CO2 {
  input:
    val executionTracePath
  output:
    stdout
  script:
    def co2Records = calculateCO2(
      Path.of(executionTracePath),
      [
        traceFile: './out/pipeline_info/post-run_trace.txt',
        summaryFile: './out/pipeline_info/post-run_summary.txt',
        reportFile: './out/pipeline_info/post-run_report.html',
      ]
  )
  """
  echo "Finished CO2 calculation."
  """
}

workflow {
  calculate_CO2('<Path_to_your_execution_trace>')
}
```

### Functions
#### `parseTraceFile`
Parse the trace file into a list of TraceRecord instances. 
- **tracePath**:
  Path to a trace file

- **delimiter**:
  Deliming character of the tabular trace file. Defaults to `'\t'`.

#### `calculateCO2`
Can be used to calculate the emissions of a trace.
!!! warning

    This is not the recommended way to estimate the footprint.
    Trace files contain the same metrics as the internal trace, but is often lacking accuracy, especially when they are written in human-readable mode.


- **tracePath**:
  Path to the trace file 

- **configModifications**:
  Which changes should be made to the given config. Can be used to change the file output path and carbon-intensity for post-run estimations. Defaults to `[:]`.
  Example: `[traceFile: <Path_to_your_output_trace_file>, ci: <Your_carbon_intensity>]`

- **timeCIs**:
  A map of times linked to CI values. Can be used to infer the CI during the run which produced the trace file. Defaults to `null`.

!!! note

    If neither `carbonIntensity` nor `timeCIs` is given, the global default carbon intensity is used.