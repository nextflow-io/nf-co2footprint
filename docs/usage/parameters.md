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
  Specifies the zone code for the location where computations are run. You can find your `zone code` on the [zones overview](https://portal.electricitymaps.com/docs/getting-started#zonesoverview) section on the Electricity Maps website. It has to match one of those defined there to be used within the plugin, otherwise it will be set to `null`.     
  **Default**:  `null`

- **`ci`**  
  Location-based carbon intensity (CI). Set this parameter only if you know the CI for your location and prefer not to use the Electricity Maps API. However, using the API is recommended to retrieve real-time data for more accurate calculations.  
  **Default**:  `null`

- **`emApiKey`**  
  Your Electricity Maps API token.  
  To obtain your `emApiKey` you have to register in the [developer portal](https://portal.electricitymaps.com).Then, create a Nextflow secret with the name `EM_API_KEY` for your API key using:  
  `nextflow secrets set EM_API_KEY "paste_api_key_here"`. Then, set the config parameter to `secrets.EM_API_KEY`.  
  **Default**: `null`

- **`pue`**  
  Power usage effectiveness efficiency coefficient of the data centre. For local cluster you can usually find out your specific PUE at the system administrators or system managers. Also, the current [yearly worldwide average](https://www.statista.com/statistics/1229367/data-center-average-annual-pue-worldwide/) could be used.  
  **Default**: 1.00

- **`powerdrawMem`**  
  power draw from memory.  
  **Default**: 0.3725.
  
- **`customCpuTdpFile`**  
  Input CSV file containing custom CPU TDP data. This should contain the following columns: `name`, `tdp (W)`, `cores`. Note that this overwrites TDP values for already provided CPU models. You can find the by default used TDP data [here](https://nextflow-io.github.io/nf-co2footprint/plugins/nf-co2footprint/src/resources/cpu_tdp_data/CPU_TDP_wikichip.csv).  
  **Default**: `null`.

    Example custom CPU TDP file:

    | name                            | tdp (W) | cores |
    |---------------------------------|---------|-------|
    | Intel(R) Xeon(R) CPU E5-2670 v3 | 120     | 12    |
    | AMD EPYC 7742                   | 225     | 64    |
    | Intel(R) Core(TM) i7-9700K      | 95      | 8     |

- **`ignoreCpuModel`**  
  Ignore the retrieved Nextflow trace `cpu_model` name and use the default CPU power draw value. This is useful, if the cpu model information provided by the linux kernel is not correct, for example, in the case of VMs emulating a different CPU architecture.  
  **Default**: `false`.

- **`powerdrawCpuDefault`**  
  The default value used as the power draw from a computing core.
  This is only applied if the parameter `ignoreCpuModel` is set or if the retrieved `cpu_model` could not be found in the given CPU TDP data.  
  **Default**: 12.0.
  
- **`machineType`**  
  Specifies the type of machine used for computation. It determines the `pue` if the parameter is not explicitly specified in the config file. Must be one of: `'compute cluster'`, `'local'` or `'cloud'`.
  If not specified the plugin infers `machineType` from the Nextflow `process.executor` setting, mapping it to either `'compute cluster'`, `'local'` or `'cloud'`.
    - `'local'`: sets `pue` to 1.0  
    - `'compute cluster'`: sets `pue` to 1.67
    - `'cloud'`: sets `pue` to 1.56  
      <sup>Source: [Uptime Institute 2024 Global Data Center Survey](https://datacenter.uptimeinstitute.com/rs/711-RIA-145/images/2024.GlobalDataCenterSurvey.Report.pdf)</sup>
  **Default**:  `null`

- **`ciMarket`**  
  This parameter can be added to account for individual differences in the energy mix that is used for computation. It is strongly recommended to read the [Accounting for a personal energy mix](configuration.md#accounting-for-a-personal-energy-mix)
  section beforehand. This parameter does not replace the location-based CI, but adds another value to the final report.  
  **Default**:  `null`
