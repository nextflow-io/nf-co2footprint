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
- `ci`: carbon intensity of the respective energy production.
- `pue`: power usage effectiveness, efficiency coefficient of the data centre.
- `powerdrawMem`: power draw from memory.