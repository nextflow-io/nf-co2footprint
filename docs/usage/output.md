---
title: Output
description: Output of the nf-co2footprint plugin.
---

The nf-co2footprint plugin creates three output files:

- **`traceFile`**  
  The trace file includes calculations for each task, similar to the Nextflow trace file. Within this file you can find resource usage details of specific tasks and also the hardware information of your CPU.

- **`summaryFile`**  
  The summary file includes the total CO₂ footprint of the workflow run and the configuration used for the plugin.
  
- **`reportFile`**  
  The HTML report contains information about the carbon footprint of the whole pipeline run as well as plots showing the distributions of the CO₂ emissions for the different processes. Additionally, it contains a table with the metrics for all individual tasks. The table is limited to 10000 entries by default.



