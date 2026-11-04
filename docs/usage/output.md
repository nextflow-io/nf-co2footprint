---
title: Output
description: Output of the nf-co2footprint plugin.
---

### Files:

The nf-co2footprint plugin creates three output files:

- **`trace`** ([sample](../assets/co2footprint_trace_sample.txt))  
  The trace file includes calculations for each task, similar to the Nextflow trace file. Within this file you can find resource usage details of specific tasks and also the hardware information of your CPU.

- **`summary`** ([sample](../assets/co2footprint_summary_sample.txt))  
  The summary file includes the total CO₂ footprint of the workflow run and the configuration used for the plugin. It is disabled if `enabled = true` is not set in the config.
  
- **`report`** ([sample](../assets/co2footprint_report_sample.html))  
  <a id="output-report"></a>
  The HTML report contains information about the carbon footprint of the whole pipeline run as well as plots showing the distributions of the CO₂ emissions for the different processes.
  The CO₂ emissions are separated into newly generated (i.e. from non-cached tasks) and total (including cached tasks). Furthermore, the report generates simple recommendations for the current pipeline run, with the aim of adjusting parameters to reduce the emissions of reruns or similar execution settings. For more information, please refer to the [optimization](./optimization.md) section.
  Additionally, it contains a table with the metrics for all individual tasks. The table is limited to 10000 entries by default. It finishes up with an overview plot of the carbon intensities during the workflow execution.

- **`provenance`** ([sample](../assets/co2footprint_provenance_sample.json))
  The provenance file contains all trace information contributing to the emission calculation in a tree structure with the levels in descending order `session -> head job & workflow -> process -> task`. Example: A workflow consists of multiple processes / has multiple `process` level children.
  The file design adheres to the javascript object notation linked-data (JSON-LD) format with type context definitions from schema.org and bioschemas.org.
  The head job emission estimation includes all processes except tasks from the point of plugin start to stop in a similar manner to how Nextflow defines a `TraceRecord`, but through the Java-native OSHI library.
  The workflow and head values are both accumulated to an overarching `session` value.
  *Example:* A workflow consists of multiple processes / has multiple `process` level children, which are listed under `isPart`.  
  <br>
  **Accumulation of provenance values:**
    - The total session emission estimation includes everything from the point of plugin start to stop in a similar manner to how Nextflow defines a `TraceRecord`, but through the Java-native OSHI library.  
    - The value accumulation from `tasks` over `process` to `workflow` level happens differently for different values:
        - Weighted average
          - In relation to energy: `carbon_intensity`, `powerdraw_cpu`, `carbon_intensity_market`, `powerdraw_memory`
          - In relation to runtime: `%cpu`, `%mem`, `vmem`, `rss`, `cpus`, `pue`
        - Maximum: `memory`, `cpus`, `complete`, `attempt`, `peak_vmem`, `peak_rss`
        - Minimum: `submit`, `start`
        - Accumulation in set: `cpu_model`, `status`, `name`, `cpu_power_model`
        - All other values are added up
  
    We hope that the accumulation style provides sensible values at every level and valuable insights for you. You can always suggest changes at our [Github issue page](https://github.com/nextflow-io/nf-co2footprint/issues).
  
    !!! note "Comparison head job vs. workflow"
        The tracking of resource usage, such as `%cpu` and `memory` differs slightly from the way Nextflow determines trace values. Specifically, the `memory` value is determined by the peak RSS value of the head-associated processes. Therefore, it might not be 100% comparable.
  
!!! note

    Column headers in tables displaying task-specific metrics use the same field names as Nextflow’s native trace output when representing the same metric. For field definitions, see the Nextflow documentation: [Trace file fields](https://www.nextflow.io/docs/latest/tracing.html#trace-file).

### Logging:
Log messages may indicate issues, successful steps, or warnings about potentially unwanted behavior. By default, identical messages triggered by multiple tasks are shown only once in the console, while every occurrence is recorded in the `.nextflow.log` file with a `[DUPLICATE]` tag.