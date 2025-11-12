### Extension points
The plugin provides some extension points for post-run estimations of carbon footprints.

!!! warning

    This is not the recommended way to estimate the footprint.
    Trace files contain the same metrics as the internal trace, but is often lacking accuracy, especially when they are written in human-readable mode.

!!! danger

    Keep in mind that the standard execution trace file does not report all necessary values and rounds values when using human-readable output.
    For best results use:
    ```
    trace {
      raw = true
      fields = [
          'task_id', 'hash', 'native_id', 'name', 'status', 'exit', 'submit', 'duration', 'realtime', '%cpu', 'peak_rss', 'peak_vmem', 'rchar', 'wchar', // Standard
          'start', 'complete', 'cpus', 'memory', 'process', 'cpu_model' // For Post-run calculation
        ]
    }
    ```

#### From the command line
The command line functionality utilizes Nextflow configs, like the regular plugin (see [parameters.md](./pa
rameters.md)).
It is recommended to set the output paths and a fixed carbon intensity value (`ci`) from within the config.
```bash
nextflow plugin nf-co2footprint:postRun --config <path_to_nextflow.config> --tracePath <path_to_execution_trace.txt>
```



#### From within a workflow run
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

##### Functions
###### `parseTraceFile`
Parse the trace file into a list of TraceRecord instances. 
- **tracePath**:
  Path to a trace file

- **delimiter**:
  Deliming character of the tabular trace file. Defaults to `'\t'`.

###### `calculateCO2`
Can be used to calculate the emissions of a trace.

- **tracePath**:
  Path to the trace file 

- **configModifications**:
  By default the extension point uses the Nextflow config of the current run, including parameters within the `co2footprint` field.
  This parameter is designed to change the config dynamically within workflow runs. By forming the union between the `co2footprint` field and a given map.
  The changes here are exclusive to one single run of an extension function and do not affect the overall workflow run parameters.
  In essence: This can be used to change the output paths of files, the carbon-intensity, and more for post-run estimations. Defaults to `[:]`.
  Example: `[traceFile: <Path_to_your_output_trace_file>, ci: <Your_carbon_intensity>]`
