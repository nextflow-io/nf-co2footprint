---
title: Output
description: Output of the nf-co2footprint plugin.
---

## Output

The nf-co2footprint plugin creates three output files:

- `traceFile`: The trace file includes calculations for each task. Similar to the nextflow trace file. Within this file you can find resource usage details of specific tasks and also the hardware information of your CPU.
- `summaryFile`: The summary file includes the total CO<sub>2</sub> footprint of the workflow run and the configuration used for the plugin.
- `reportFile`: The html report file includes data from trace and summary file and also summary figures of the executed tasks.



