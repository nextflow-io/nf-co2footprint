---
title: Parameters
description: Customising parameters for the CO2e calculation.
---

The following parameters are currently available:

- **`traceFile`**  
  Name of the `.txt` carbon footprint report containing the energy consumption, the estimated CO₂ emission, and other relevant metrics for each task.  
  **Default**: `co2footprint_trace_<timestamp>.txt`

- **`summaryFile`**  
  Name of the `.txt` carbon footprint summary file containing the total energy consumption and the total estimated CO₂ emission of the pipeline run.  
  **Default**: `co2footprint_summary_<timestamp>.txt`

- **`reportFile`**  
  Name of the HTML report containing information about the entire carbon footprint, overview plots and more detailed task-specific metrics.  
  **Default**: `co2footprint_report_<timestamp>.html`

- **`location`**  
  Specifies the zone code for the location where computations are run, based on the available zone codes in the [zones overview](https://portal.electricitymaps.com/docs/getting-started#zonesoverview) section on the Electricity Maps website. If the provided location does not match any listed zone codes, it will be set to `null`.  
  **Default**:  `null`

- **`ci`**  
  Set this parameter only if you know the carbon intensity (ci) value for your location and prefer not to use the Electricity Maps API. However, using the API is recommended to retrieve real-time data for more accurate calculations.  
  **Default**:  `null`

- **`apiKey`**  
  Your Electricity Maps API token.  
  First, create a Nextflow secret for your API key using:  
  `nextflow secrets set YOUR_API_KEY "paste_api_key_here"`. Then, set the config parameter to `secrets.YOUR_API_KEY`.

- **`pue`** 
  Power usage effectivenes efficiency coefficient of the data centre. For local cluster you can usually find out your specific PUE at the system administrators or system managers. Also the current [yearly worldwide average](https://www.statista.com/statistics/1229367/data-center-average-annual-pue-worldwide/) could be used.
  **Default**: 1.00

- **`powerdrawMem`**  
  power draw from memory.  
  **Default**: 0.3725.
  
- **`customCpuTdpFile`**  
  Input CSV file containing custom CPU TDP data. This should contain the following columns: `name`, `tdp (W)`, `cores`. Note that this overwrites TDP values for already provided CPU models. You can find the by default used TDP data [here](https://nextflow-io.github.io/nf-co2footprint/plugins/nf-co2footprint/src/resources/CPU_TDP.csv).  
  **Default**: `null`.

    Example custom CPU TDP file:

    | name                          | tdp (W) | cores |
    |-------------------------------|---------|-------|
    | Intel(R) Xeon(R) CPU E5-2670 v3 | 120     | 12    |
    | AMD EPYC 7742                 | 225     | 64    |
    | Intel(R) Core(TM) i7-9700K    | 95      | 8     |

- **`ignoreCpuModel`**  
  Ignore the retrieved Nextflow trace `cpu_model` name and use the default CPU power draw value. This is useful, if the cpu model information provided by the linux kernel is not correct, for example, in the case of VMs emulating a different CPU architecture.  
  **Default**: `false`.

- **`powerdrawCpuDefault`**  
  The default value used as the power draw from a computing core.
  This is only applied if the parameter `ignoreCpuModel` is set or if the retrieved `cpu_model` could not be found in the given CPU TDP data.  
  **Default**: 12.0.
  
- **`machineType`**  
  The type of machine the computation is executed upon. Has to be in ['compute cluster', 'local', ''].
  The Nextflow config option `process.executor` is used to infer the `machineType` when it is not included in the config. The `machineType` determines the default power usage effectiveness (PUE) if not explicitly specified.
  Default: 'local' (sets `pue` to 1.0)
