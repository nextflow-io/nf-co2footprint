---
title: Configuration
description: Configuration of the nf-co2footprint plugin
---

# Configuration of the nf-co2footprint plugin

## General usage
To test if the plugin works on your system please follow the quick start guide ([Quick Start](https://nextflow-io.github.io/nf-co2footprint/#quick-start)) on a small pipeline like [nextflow-io/hello](https://github.com/nextflow-io/hello).

To customize the plugin settings to your computing environment and preferences, you can adjust the nf-co2footprint plugin parameters in your config file as follows:

```groovy title="nextflow.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}

// Optional example config settings for CO₂ reporting:

def co2_timestamp = new Date().format('yyyy-MM-dd_HH-mm-ss')

co2footprint {
  traceFile = "${params.outdir}/pipeline_info/co2footprint_trace_${co2_timestamp}.txt"
  summaryFile = "${params.outdir}/pipeline_info/co2footprint_summary_${co2_timestamp}.txt"
  reportFile = "${params.outdir}/pipeline_info/co2footprint_report_${co2_timestamp}.html"
  location = 'DE'                             // replace with your zone code
  emApiKey = secrets.EM_API_KEY               // set your API key as Nextflow secret with the name 'EM_API_KEY'
  pue = 1.3                                   // replace with PUE of your data center
  machineType = 'compute cluster'             // set to 'compute cluster', 'local', or 'cloud'
}
```

Include the config file for your pipeline run using the `-c` Nextflow parameter, for example as follows:

```bash
nextflow run nextflow-io/hello -c nextflow.config
```

For a complete list and detailed descriptions of all available configuration parameters, please refer to the [Parameters](./parameters.md) section.

## Carbon intensity (CI)

### How are CI values determined by default?  

The plugin can retrieve **real-time carbon intensity (CI) values** in grams of CO₂-equivalent per kilowatt-hour (gCO₂eq/kWh) from [Electricity Maps](https://www.electricitymaps.com/) if a valid API key and location are provided. This enables task-specific CI estimates based on the actual energy mix at execution time.  
If **no API key is supplied**, the plugin will fall back to using **2024 yearly average values** for the specified zone (if available). When no location is provided either, a global default CI value is used.

The logic applied in detail: 

1. **If `ci` is explicitly set**, this value is used directly as the carbon intensity, and no API call is made.
2. **If `ci` is not set**, but both `location` and `emApiKey` are provided, the plugin will query the [Electricity Maps API](https://www.electricitymaps.com/) for a real-time carbon intensity value for the specified zone. The API call is made once per Nextflow task to retrieve the most up-to-date carbon intensity.
3. **If only `location` is set**, the plugin will fallback to a default value for the specified zone. 
4. **If neither `ci` nor valid `location` and `apiKey` are provided**, the plugin will  fallback to a global default value.

> Carbon intensity data is retrieved from [Electricity Maps](https://www.electricitymaps.com/) and used under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/). See the full attribution and license terms [here](https://nextflow-io.github.io/nf-co2footprint/).

### Accounting for a personal energy mix

The `ciMarket` parameter can be used to provide a custom value to account for differences to your regional average. This can occur due to:  

-  A different market share through a contract with your energy provider, guaranteeing to provide a certain percentage of electricity from renewable sources  
-  Direct contributions to the used electricity (e.g. via owned solar panels)

You can calculate an approximation of your personal/marked-based CI via the average of the [emission factors](https://github.com/electricitymaps/electricitymaps-contrib/wiki/Default-emission-factors) weighted by their respective share in your mix.

Example: If your institution would produce half of their energy themselves through 20% hydroelectric and 80% solar power generation:

$$
\mathrm{ci}_{\mathrm{total}} = \left( \mathrm{ci}_{\mathrm{hydro}} \cdot 0.2 + \mathrm{ci}_{\mathrm{solar}} \cdot 0.8 \right) \cdot 0.5 + \mathrm{ci}_{\mathrm{region}} \cdot 0.5
$$

!!! info

    We encourage the reporting of emission values by location instead of marked-based carbon intensities. The energy is still drawn from a finite pool of renewables, currently available to your grid. It would likely be utilized for another buyer, regardless of the contract. This is not to say that these contracts are without merit, but the short-term differences are limited.

## Cloud computations

While the CO2 footprint calculation works on cloud instances, **nf-co2footprint** can currently not natively support all cloud environments, as cloud-specific values (such as certain CPU models or PUE values) may be missing in our datasets. As a result, calculations may often rely on fallback values.

!!! info

    For common cloud platforms, the plugin automatically applies provider-specific default PUE values where supported. However, not all cloud providers or platforms are currently covered, so you may need to supply some information manually. You can view the full list of supported providers and their corresponding PUEs in the [executor PUE mapping file](https://github.com/nextflow-io/nf-co2footprint/blob/master/plugins/nf-co2footprint/src/resources/executor_machine_pue_mapping.csv). For example, for AWS a default PUE of **1.15** is used.

To improve the estimate of your CO₂ footprint on the cloud, you are encouraged to manually provide:  

- [The location of your instance](https://portal.electricitymaps.com/docs/getting-started#geographical-coverage) (e.g., zone code `'DE'` for AWS region `eu-central-1`)
- The PUE of the data center (cloud providers often give global averages)
- If the plugin’s TDP table does not include the CPU of your cloud compute instance and you know the per-core TDP for your instance, set `ignoreCpuModel = true` and specify `powerdrawCpuDefault`. Alternatively, you may provide a `customCpuTdpFile` if there are multiple models, but in this case, `ignoreCpuModel` should **not** be set to `true`, as this would prevent the custom TDP file from being used. For more information, see [parameters.md](parameters.md).

If you still want to estimate your CO₂ footprint on the cloud, you can manually provide:

- The location of your instance (e.g., `'DE'` for AWS region `eu-central-1`)
- The PUE of the data center
- If the plugins TDP table does not include your cloud compute instance, and you know the per-core TDP for your instance, set `ignoreCpuModel = true` and specify `powerdrawCpuDefault`.

**Example configuration:**

```groovy title="nextflow_cloud.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}

def co2_timestamp = new java.util.Date().format('yyyy-MM-dd_HH-mm-ss')

co2footprint {
    traceFile           = "${params.outdir}/co2footprint/co2footprint_trace_${co2_timestamp}.txt"
    summaryFile         = "${params.outdir}/co2footprint/co2footprint_summary_${co2_timestamp}.txt"
    reportFile          = "${params.outdir}/co2footprint/co2footprint_report_${co2_timestamp}.html"
    location            = 'DE'
    emApiKey            = secrets.EM_API_KEY
    pue                 = 1.3
    ignoreCpuModel      = true
    powerdrawCpuDefault = 8
}
```

## GPU computations

!!! warning

    GPU support is not yet implemented. Tracking of GPU-driven computations may not work or may be incomplete.