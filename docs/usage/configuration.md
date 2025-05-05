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
  location = '<your_zone_code>'     // replace with your zone code
  ci = <your_ci>                    // replace with carbon intensity (gCO2eq/kWh)
  apiKey = secrets.<YOUR_API_KEY>   // replace with your electricity maps API key secret 
  pue = <your_pue>                  // replace with PUE of your data center
}
```
You can find your `zone code` on the [zones overview](https://portal.electricitymaps.com/docs/getting-started#zonesoverview) section on the Electricity Maps website. It has to match one of those defined there to be used within the plugin. To obtain the `apikey` you have to register in the [developer portal](https://portal.electricitymaps.com). 

Include the config file for your pipeline run using the `-c` Nextflow parameter, for example as follows:

```bash
nextflow run nextflow-io/hello -c nextflow.config
```

## How the plugin determines the carbon intensity (ci) value

The plugin uses the `ci`, `location`, and `apiKey` parameters to determine the carbon intensity (in gCO₂eq/kWh) that is used in the energy impact calculations. The logic is as follows:

1. **If `ci` is explicitly set**, this value is used directly as the carbon intensity, and no API call is made.
2. **If `ci` is not set**, but both `location` and `apiKey` are provided, the plugin will query the [Electricity Maps API](https://www.electricitymaps.com/) for a real-time carbon intensity value for the specified zone. The API call is made once per Nextflow task to retrieve the most up-to-date carbon intensity at the time the task starts.
3. **If only `location` is set**, the plugin will fallback to a default value for the specified zone. 
3. **If neither `ci` nor valid `location` and `apiKey` are provided**, the plugin will  fallback to a global default value.

> Carbon intensity data is retrieved from [Electricity Maps](https://www.electricitymaps.com/) and used under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/). See the full attribution and license terms [here](https://nextflow-io.github.io/nf-co2footprint/).

## Cloud computations

At the moment the nf-co2footprint can not natively support cloud computations.

!!! warning

    This is not tested and might not work as intended, but cloud native support will be implemented soon.

If you are still keen to get insights into your CO<sub>2</sub> you could try to find out the following and append it to your config:

- The location and hereby CI of your instance.
- The PUE of the data center, where the instance is located.
- The power draw per core of your selected instance.
- If available the power draw of the memory per GB.

Your configuration could look something like:

```groovy title="nextflow_cloud.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}

def co2_timestamp = new java.util.Date().format( 'yyyy-MM-dd_HH-mm-ss')

co2footprint {
    traceFile           = "${params.outdir}/co2footprint/co2footprint_trace_${co2_timestamp}.txt"
    reportFile          = "${params.outdir}/co2footprint/co2footprint_report_${co2_timestamp}.html"
    summaryFile         = "${params.outdir}/co2footprint/co2footprint_summary_${co2_timestamp}.txt"
    ci                  = 300
    pue                 = 1.4
    ignoreCpuModel      = true
    powerdrawCpuDefault = 8
    powerdrawMem        = 0.3725
}
```

## GPU computations

So far tracking of GPU driven computations are not implemented, and functionality might be impaired.