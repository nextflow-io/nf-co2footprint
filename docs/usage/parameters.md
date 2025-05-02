---
title: Parameters
description: Customising parameters for the CO2e calculation.
---

## Customising parameters

The following parameters are currently available:

- `traceFile`: Name of the TXT carbon footprint report containing the energy consumption, the estimated CO<sub>2</sub> emission and other relevant metrics for each task.
Default: `co2footprint_trace_<timestamp>.txt`.
- `summaryFile`: Name of the TXT carbon footprint summary file containing the total energy consumption and the total estimated CO<sub>2</sub> emission of the pipeline run.
Default: `co2footprint_summary_<timestamp>.txt`.
- `reportFile`: Name of the HTML report containing information about the entire carbon footprint, overview plots and more detailed task-specific metrics.
Default: `co2footprint_report_<timestamp>.html`.
- `ci`: Carbon intensity of the respective energy production. Available regions can be found [here](https://github.com/nextflow-io/nf-co2footprint/blob/1.0.0-beta/plugins/nf-co2footprint/src/resources/CI_aggregated.v2.2.csv), otherwise you can use data from [ElecricityMaps](https://app.electricitymaps.com/map/24h), to find out locally more accurate CI.
Mutually exclusive with the `location` parameter.
Default: 475.
- `location`: location code to automatically retrieve a location-specific CI value.
For countries, these are [ISO alpha-2 codes](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2). 
For regions, it’s the ISO alpha-2 code for the country, followed by an identifier for the state, e.g. US-CA for “California, USA”.
You can find the available data [here](https://github.com/nextflow-io/nf-co2footprint/blob/1.0.0-beta/plugins/nf-co2footprint/src/resources/CI_aggregated.v2.2.csv).
Mutually exclusive with the `ci` parameter.
Default: `null`.
- `pue`: Power usage effectiveness, efficiency coefficient of the data centre. For local cluster you can usually find out your specific PUE at the system administrators or system managers. Also the current [yearly worldwide average](https://www.statista.com/statistics/1229367/data-center-average-annual-pue-worldwide/) could be used.
Default: 1.00.
- `powerdrawMem`: power draw from memory.
Default: 0.3725.
- `customCpuTdpFile`: Input CSV file containing custom CPU TDP data.
This should contain the following columns: `model`,`TDP`,`n_cores`,`TDP_per_core`.
Note that this overwrites TDP values for already provided CPU models.
You can find the by default used TDP data [here](https://github.com/nextflow-io/nf-co2footprint/blob/1.0.0-beta/plugins/nf-co2footprint/src/resources/TDP_cpu.v2.2.csv).
Default: `null`.
- `ignoreCpuModel`: ignore the retrieved Nextflow trace `cpu_model` name and use the default CPU power draw value.
This is useful, if the cpu model information provided by the linux kernel is not correct, for example, in the case of VMs emulating a different CPU architecture.
Default: `false`.
- `powerdrawCpuDefault`: the default value used as the power draw from a computing core.
This is only applied if the parameter `ignoreCpuModel` is set or if the retrieved `cpu_model` could not be found in the given CPU TDP data.
Default: 12.0.
- `machineType`: the type of machine the computation is executed upon. Has to be in ['compute cluster', 'local', ''].
  The Nextflow config option `process.executor` is used to infer the `machineType` when no value is set. The `machineType` determines the default power usage effectiveness (PUE) if not explicitly specified.
  Default: 'local' / PUE of 1.0
