### Extension points
The plugin provides some extension points for post-run estimations of carbon footprints.

!!! warning

    Post-run estimation using trace files can be inaccurate due to missing or rounded values, especially when using human-readable output. For reliable results, enable raw tracing and include all required fields:
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
It is recommended to set the output paths and a fixed carbon intensity value (`ci`) in the config.
```bash
nextflow plugin nf-co2footprint:postRun --config <path_to_nextflow.config> --tracePath <path_to_execution_trace.txt>
```



#### Within a workflow through functions
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
        trace: [file: './out/pipeline_info/post-run_trace.txt'],
        summary: [file: './out/pipeline_info/post-run_summary.txt'],
        report: [file: './out/pipeline_info/post-run_report.html'],
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
  Temporarily overrides parameters in the `co2footprint` block of the current Nextflow configuration for a single extension function call. The provided map is merged with the existing configuration and does not affect the overall workflow run. Can be used to adjust settings such as output paths or carbon intensity for post-run estimations. Defaults to [:].
  Example: `[trace: [file: <Path_to_your_output_trace_file>], ci: <Your_carbon_intensity>]`
