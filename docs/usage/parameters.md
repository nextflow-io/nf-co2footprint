---
title: Parameters
description: Customising parameters for the CO₂e calculation.
---

The following parameters are currently available:

## Output Files

- **`trace`**  
  Map containing:
    - `enabled`: Whether to produce this file
    - `file`: Name of the `.txt` carbon footprint report containing the energy consumption, estimated CO₂ emission, and other relevant metrics for each task.
    - `overwrite`: whether to overwrite the file, if it already exists.

    **Default**: `[enabled: true, file: co2footprint_trace_<timestamp>.txt, overwrite: true]`

- **`summary`**  
  Map containing:
    - `enabled`: Whether to produce this file
    - `file`: Name of the `.txt` carbon footprint summary file containing the total energy consumption and total estimated CO₂ emission of the pipeline run.
    - `overwrite`: Whether to overwrite the file, if it already exists.

    **Default**: `[enabled: true, file: co2footprint_summary_<timestamp>.txt, overwrite: true]`

- **`report`**  
  Map containing:
    - `enabled`: Whether to produce this file
    - `file`: Name of the HTML report containing information about the entire carbon footprint, overview plots, and more detailed task-specific metrics.
    - `overwrite`: Whether to overwrite the file, if it already exists.
    - `maxTasks`: Maximum number of tasks which are included into the report.

    **Default**: `[enabled: true, file: co2footprint_report_<timestamp>.html, overwrite: true, maxTasks: 10000]`

- **`provenance`**  
  Map containing:
    - `enabled`: Whether to produce this file
    - `file`: Name of the data/machine-actionable file containing all metrics that were used during footprint calculation in a structured way.
    - `overwrite`: Whether to overwrite the file, if it already exists.
    - `emissionMetricsOnly`: Whether to only include emission metrics, such as CO₂ equivalents and electricity consumption.

    **Default**: `[enabled: false, file: co2footprint_report_<timestamp>.html, overwrite: true, emissionMetricsOnly: true]`

    !!! warning "Preliminary feature"
        The data file is currently not in its final version. Changes in the near future are very likely.

## Location & Carbon Intensity

- **`location`**  
    Zone code of the geographical location of the computing machine. 
    
    Find your zone code on the [Electricity Maps zones overview](https://portal.electricitymaps.com/docs/getting-started#zonesoverview). If the provided code does not match one of the defined zones, it will be set to `null`, resulting in a fallback to the worldwide default value.
    
    **Default**: `null`

- **`emApiKey`**  
    Your Electricity Maps API token.
    
    To set up:
    
    1. Register in the [developer portal](https://portal.electricitymaps.com/auth/signup?return=/developer-hub/playground)
    2. Create a Nextflow secret with your API key:  
```bash
       nextflow secrets set EM_API_KEY "paste_api_key_here"
```
    3. Set the config parameter to `secrets.EM_API_KEY`
    
    **Default**: `null`

- **`ci`**  
    Location-based carbon intensity (CI) value in gCO₂eq/kWh.
    
    Set this parameter only if you know the CI for your location and prefer not to use the Electricity Maps API. However, using the API is recommended to retrieve real-time data for more accurate calculations.
    
    **Default**: `null`

- **`ciMarket`**  
    Market-based carbon intensity to account for individual differences in energy mix.
    
    This parameter adds an additional value to the final report and does not replace the location-based CI. It is strongly recommended to read the [Accounting for a personal energy mix](configuration.md#accounting-for-a-personal-energy-mix) section before using this parameter.
    
    **Default**: `null`


## Data Center & Machine Settings

- **`pue`**  
    Power usage effectiveness (PUE) of the data center.
    
    For local clusters, you can usually obtain your specific PUE from system administrators. Alternatively, you can use the current [yearly worldwide average](https://www.statista.com/statistics/1229367/data-center-average-annual-pue-worldwide/).
    
    The plugin uses provider-specific default PUE values for supported cloud platforms if the respective executor is registered by Nextflow. You can find these values in the [executor PUE mapping file](https://github.com/nextflow-io/nf-co2footprint/blob/master/src/resources/executor_machine_pue_mapping.csv).
    
    !!! warning
        If specified, this value will override any PUE determined by `machineType`.
    
    **Default**: `1.00`

- **`machineType`**  
    Type of machine used for computation. Determines the `pue` if not explicitly set.
    
    **Allowed values**:
    
    - `'compute cluster'` - Sets `pue` to 1.67
    - `'local'` - Sets `pue` to 1.0
    - `'cloud'` - Sets `pue` to 1.56 <sup>[[source]](https://datacenter.uptimeinstitute.com/rs/711-RIA-145/images/2024.GlobalDataCenterSurvey.Report.pdf)</sup>
    
    If not specified, the value is automatically inferred from the Nextflow `process.executor`.
    
    **Default**: Auto-detected from executor


## Hardware Power Draw
- **`powerdrawMem`**  
  Power draw from memory in Watts per GB..  
  **Default**: 0.3725.
  
- **`customCpuTdpFile`**  
  Input CSV file containing custom CPU TDP data. This should contain the following columns: `name`, `tdp (W)`, `logicalCores`. Note that this overwrites TDP values for already provided CPU models. You can find the by default used TDP data [here](https://github.com/nextflow-io/nf-co2footprint/blob/master/src/resources/cpu_tdp_data/CPU_TDP.csv).  
  **Default**: `null`.

    Example custom CPU TDP table: <a id="custom-tdp-table"></a>

    | name                            | tdp (W) | logicalCores |
    |---------------------------------|---------|--------------|
    | Intel(R) Xeon(R) CPU E5-2670 v3 | 120     | 24           |
    | AMD EPYC 7742                   | 225     | 128          |
    | Intel(R) Core(TM) i7-9700K      | 95      | 16           |

    !!! Note "File format"
        The table must be supplied in [CSV format](https://www.wikihow.com/Create-a-CSV-File).
    
    **Default**: `null`

- **`ignoreCpuModel`**  
    Ignore the CPU model name retrieved from Nextflow trace and use the default CPU power draw value instead.
    
    This is useful when the CPU model information provided by the Linux kernel is incorrect, such as in VMs emulating a different CPU architecture.
    
    **Default**: `false`

- **`powerdrawCpuDefault`**  

    Default power draw value (in Watts) for a single computing core.
    
    This value is applied when:
    
    - The parameter `ignoreCpuModel` is set to `true`, or
    - The retrieved `cpu_model` cannot be found in the CPU TDP data
    
    **Default**: `null` -> results in default value from CPU TDP data table

- **`cpuPowerModel`**  
    
    !!! warning "Experimental feature"
        The `cpuPowerModel` parameter is experimental and may change in future releases.

    A power model function that takes the parameter `coreUsage`.
    
    If specified, this overrides TDP-based power draw estimation for CPU cores. The function returns the **per-core power draw** (in Watts) as a function of core utilization (0–1).
    
    **Example**: `{coreUsage -> 0.5 * coreUsage + 10.0}`
    
    **Example visualization**:
    
    <img src="../assets/custom_powerdraw_function_linear_gh_pages.png" alt="Example custom CPU power model" width="500"/>    
    **Default**: `null`