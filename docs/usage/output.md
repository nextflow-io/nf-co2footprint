---
title: Output
description: Output of the nf-co2footprint plugin.
---

### Files:

The nf-co2footprint plugin creates three output files:

- **`traceFile`** ([sample](../assets/co2footprint_trace_sample.txt))  
  The trace file includes calculations for each task, similar to the Nextflow trace file. Within this file you can find resource usage details of specific tasks and also the hardware information of your CPU.

- **`summaryFile`** ([sample](../assets/co2footprint_summary_sample.txt))  
  The summary file includes the total CO₂ footprint of the workflow run and the configuration used for the plugin.
  
- **`reportFile`** ([sample](../assets/co2footprint_report_sample.html))  
  The HTML report contains information about the carbon footprint of the whole pipeline run as well as plots showing the distributions of the CO₂ emissions for the different processes. The CO2 emissions are separated into newly generated (i.e. from non-cached tasks) and total (including cached tasks). Additionally, it contains a table with the metrics for all individual tasks. The table is limited to 10000 entries by default.

!!! note

    Column headers in tables displaying task-specific metrics use the same field names as Nextflow’s native trace output when representing the same metric. For field definitions, see the Nextflow documentation: [Trace file fields](https://www.nextflow.io/docs/latest/tracing.html#trace-file).

### Logging:
Log messages may indicate issues, successful steps, or warnings about potentially unwanted behavior. By default, identical messages triggered by multiple tasks are shown only once in the console, while every occurrence is recorded in the `.nextflow.log` file with a `[DUPLICATE]` tag.