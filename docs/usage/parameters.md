---
title: Parameters
description: Customising parameters for the CO2e calculation.
---

## Customising parameters

You can adjust the nf-co2footprint plugin parameters in your config file as follows:

```groovy title="nextflow.config"
co2footprint {
    file        = "${params.outdir}/co2footprint.txt"
    reportFile  = "${params.outdir}/co2footprint_report.html"
    ci          = 300
    pue         = 1.4
}
```

Include the config file for your pipeline run using the `-c` Nextflow parameter, for example as follows:

```bash
nextflow run nextflow-io/hello -c nextflow.config
```

The following parameters are currently available:

- `file`: Name of the TXT carbon footprint report containing the energy consumption, the estimated CO<sub>2</sub> emission and other relevant metrics for each task.
- `reportFile`: Name of the HTML report containing information about the entire carbon footprint, overview plots and more detailed task-specific metrics.
- `ci`: carbon intensity of the respective energy production. Mutually exclusive with the `location` parameter.
- `location`: location code to automatically retrieve a location-specific CI value.
For countries, these are [ISO alpha-2 codes](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2). 
For regions, it’s the ISO alpha-2 code for the country, followed by an identifier for the state, e.g. US-CA for “California, USA”.
You can find the available data [here](../../plugins/nf-co2footprint/src/resources/CI_aggregated.v2.2.csv).
Mutually exclusive with the `ci` parameter.
- `pue`: power usage effectiveness, efficiency coefficient of the data centre.
- `powerdrawMem`: power draw from memory.
- `customCpuTdpFile`: Input CSV file containing custom CPU TDP data.
This should contain the following columns: `model`,`TDP`,`n_cores`,`TDP_per_core`.
Note that this overwrites TDP values for already provided CPU models.
You can find the by default used TDP data [here](../../plugins/nf-co2footprint/src/resources/TDP_cpu.v2.2.csv).
