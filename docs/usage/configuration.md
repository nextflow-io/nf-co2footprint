---
title: Configuration
description: Configuration of the nf-co2footprint plugin
---

# Configuration of the nf-co2footprint plugin

## General usage
To test if the plugin works on your system please follow the quick start guide ([Quick Start](https://nextflow-io.github.io/nf-co2footprint/#quick-start)) on a small pipeline like [nextflow-io/hello](https://github.com/nextflow-io/hello).

You can adjust the nf-co2footprint plugin parameters in your config file as follows:

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

## Cloud computations

At the moment the nf-co2footprint can not natively support cloud computations.

!!! warning

    This is not tested and might not work as intended, but cloud native support will be implemented soon.

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