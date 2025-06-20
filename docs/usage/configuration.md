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

// Optional config settings for CO₂ reporting:

def co2_timestamp = new java.util.Date().format('yyyy-MM-dd_HH-mm-ss')

co2footprint {
  traceFile = "${params.outdir}/pipeline_info/co2footprint_trace_${co2_timestamp}.txt"
  summaryFile = "${params.outdir}/pipeline_info/co2footprint_summary_${co2_timestamp}.txt"
  reportFile = "${params.outdir}/pipeline_info/co2footprint_report_${co2_timestamp}.html"
  location = '<your_zone_code>'               // replace with your zone code
  ci = <your_ci>                              // replace with carbon intensity (gCO2eq/kWh)
  apiKey = secrets.EM_API_KEY                 // set your API key as Nextflow secret with the name 'EM_API_KEY'
  pue = <your_pue>                            // replace with PUE of your data center
  machineType = '<compute cluster|local>'     // set to 'compute cluster' or 'local'
}
```
You can find your `zone code` on the [geographical coverage](https://portal.electricitymaps.com/docs/getting-started#geographical-coverage) section on the Electricity Maps website. It has to match one of those defined there to be used within the plugin. To obtain the `apikey` you have to register in the [developer portal](https://portal.electricitymaps.com). 

Include the config file for your pipeline run using the `-c` Nextflow parameter, for example as follows:

```bash
nextflow run nextflow-io/hello -c nextflow.config
```

For a complete list and detailed descriptions of all available configuration parameters, please refer to the [Parameters](./parameters.md) section.

## How the plugin determines the carbon intensity (ci) value

The plugin uses the `ci`, `location`, and `apiKey` parameters to determine the carbon intensity (in gCO₂eq/kWh) that is used in the energy impact calculations. The logic is as follows:

1. **If `ci` is explicitly set**, this value is used directly as the carbon intensity, and no API call is made.
2. **If `ci` is not set**, but both `location` and `apiKey` are provided, the plugin will query the [Electricity Maps API](https://www.electricitymaps.com/) for a real-time carbon intensity value for the specified zone. The API call is made once per Nextflow task to retrieve the most up-to-date carbon intensity.
3. **If only `location` is set**, the plugin will fallback to a default value for the specified zone. 
3. **If neither `ci` nor valid `location` and `apiKey` are provided**, the plugin will  fallback to a global default value.

> Carbon intensity data is retrieved from [Electricity Maps](https://www.electricitymaps.com/) and used under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/). See the full attribution and license terms [here](https://nextflow-io.github.io/nf-co2footprint/).

## Cloud computations

While the CO2 footprint calculation should function on cloud instances, **nf-co2footprint** can currently not natively support cloud environments because cloud-specific values, such as specific CPU models or PUE values may be missing in our datasets. Therefore most calculations would likely rely on inexact fallback values.

To improve the estimate of your CO₂ footprint on the cloud, you are encouraged to manually provide:
- [The location of your instance](https://portal.electricitymaps.com/docs/getting-started#geographical-coverage) (e.g., zone code `'DE'` for AWS region `eu-central-1`)
- The PUE of the data center (cloud providers often give global averages)
- If the plugins TDP table does not include the CPU of your cloud compute instance and you know the per-core TDP for your instance, set `ignoreCpuModel = true` and specify `powerdrawCpuDefault`. You may also provide a `customCpuTdpFile` if there are multiple models. For more information have a look at [parameters.md](parameters.md).

!!! info

    For AWS Batch, the plugin uses a default PUE of **1.15**.

**Example:**

```groovy title="nextflow_cloud.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}

def co2_timestamp = new java.util.Date().format('yyyy-MM-dd_HH-mm-ss')

co2footprint {
    traceFile = "${params.outdir}/pipeline_info/co2footprint_trace_${co2_timestamp}.txt"
    summaryFile = "${params.outdir}/pipeline_info/co2footprint_summary_${co2_timestamp}.txt"
    reportFile = "${params.outdir}/pipeline_info/co2footprint_report_${co2_timestamp}.html"
    apiKey              = secrets.EM_API_KEY
    
    location            = 'DE'
    pue                 = 1.3
    ignoreCpuModel      = true
    powerdrawCpuDefault = 8
}
```

## GPU computations

!!! warning

    GPU support is not yet implemented. Tracking of GPU-driven computations may not work or may be incomplete.