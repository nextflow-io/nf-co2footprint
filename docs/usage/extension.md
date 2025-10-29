### Extension points
The plugin provides some extension points to facilitate interaction with the plugin from within Nextflow pipeline runs.

The interaction follows the [Nextflow extension function schema](https://nextflow.io/docs/latest/plugins/developing-plugins.html):
```Nextflow
include { calculateCO2 } from 'plugin/nf-co2footprint'

ch_co2Records = channel.of( calculateCO2( Path.of(<Path_to_your_trace_file>, <Return_files>, <CI>) ) )
```
// TODO: Does the upper schema work? I have no idea for the part before the "="

### Functions
#### `parseTraceFile`
Parse the trace file into a list of TraceRecord instances. 
- **tracePath**:
  Path to a trace file

- **delimiter**:
  Deliming character of the tabular trace file. Defaults to `'\t'`.

#### `calculateCO2`
Can be used to calculate the emissions of a trace.
- **tracePath**:
  Path to the trace file 

- **renderFiles**:
  Whether the output files are saved. Only returns CO2Records if disabled. Defaults to `true`.

- **carbonIntensity**:
  The carbon intensity that should be used for the carbon estimation in g/kWh. Defaults to `null`.

- **timeCIs**:
  A map of times linked to CI values. Can be used to infer the CI during the run which produced the trace file. Defaults to `null`.

!!! note

    If neither `carbonIntensity` nor `timeCIs` is given, the global default carbon intensity is used.