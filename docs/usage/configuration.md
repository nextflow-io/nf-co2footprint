---
title: Configuration
description: Configuration of the nf-co2footprint plugin
---

# Configuration of the nf-co2footprint plugin

## General usage
To test if the plugin works on your system please follow the quick start guide ([Quick Start](https://nextflow-io.github.io/nf-co2footprint/#quick-start)) on a small pipeline like [nextflow-io/hello](https://github.com/nextflow-io/hello).

If the plugin works and a html report is being produced you can start making the output meaningful for your specific run, by using a customized configuration file. The configuration file could look something like this:

```groovy title="nextflow.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}

def co2_timestamp = new java.util.Date().format( 'yyyy-MM-dd_HH-mm-ss')

co2footprint {
    traceFile   = "${params.outdir}/co2footprint/co2footprint_trace_${co2_timestamp}.txt"
    reportFile  = "${params.outdir}/co2footprint/co2footprint_report_${co2_timestamp}.html"
    summaryFile = "${params.outdir}/co2footprint/co2footprint_summary_${co2_timestamp}.txt"
    ci          = 300
    pue         = 1.4
}
```

Include the config file for your pipeline run using the `-c` Nextflow parameter, for example as follows:

```bash
nextflow run nextflow-io/hello -c nextflow.config
```

In this case the plugin will create 3 outputfiles within the outdir, which is set for the pipeline run. Also a custom carbon intensity (CI) is set and a Power Usage Efficiency (PUE) factor. To check all available parameters you can visit: [Parameters](https://nextflow-io.github.io/nf-co2footprint/usage/parameters/). The CI and PUE values will influence the CO<sub>2</sub> footprint the most and are dependent on where your pipeline is running. The CI reflects the used energy sources, while the PUE describes how efficiently the Power is used in regard to computing power.

If you are using a local cluster you can usually find out your specific PUE at the system administrators or system managers. Otherwise a [yearly worldwide average](https://www.statista.com/statistics/1229367/data-center-average-annual-pue-worldwide/) of 1.56 could be used, for initial runs.

For the CI you can check available regions at the provided CI table [here](https://github.com/nextflow-io/nf-co2footprint/blob/master/plugins/nf-co2footprint/src/resources/CI_aggregated.v2.2.csv), otherwise you can use data from [ElecricityMaps](https://app.electricitymaps.com/map/24h), to find out your CI.

## Cloud computations

At the moment the nf-co2footprint can not natively support cloud computations.

!!! warning

    This is not untested and might not work as, but cloud native support will be implemented soon.

If you are still keen to get insights into your CO<sub>2</sub> you could try to find out the following and append it to your config:

- The location and hereby CI of your instance.
- The PUE of the data center, where the instance is located
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