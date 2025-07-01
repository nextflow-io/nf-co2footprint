# nf-co2footprint plugin [WIP]

A Nextflow plugin to estimate the CO₂ footprint of pipeline runs.

## 📚 Docs 👉🏻 <https://nextflow-io.github.io/nf-co2footprint>

## Introduction

The nf-co2footprint plugin estimates the energy consumption for each pipeline task based on the Nextflow resource usage metrics and information about the power consumption of the underlying compute system.
The carbon intensity of the energy production is then used to estimate the respective CO₂ emission.

The calculation is based on the carbon footprint computation method developed in the [Green Algorithms Project](https://www.green-algorithms.org).

> **Green Algorithms: Quantifying the Carbon Footprint of Computation**  
> Lannelongue, L., Grealey, J., Inouye, M.,  
> Adv. Sci. 2021, 2100707. [https://doi.org/10.1002/advs.202100707](https://doi.org/10.1002/advs.202100707)

The nf-co2footprint plugin generates a detailed TXT carbon footprint report containing the energy consumption, the estimated CO₂ emission and other relevant metrics for each task.
Additionally, an HTML report is generated with information about the carbon footprint of the whole pipeline run and containing plots showing, for instance, an overview of the CO₂ emissions for the different processes.

## Quick Start

Declare the plugin in your Nextflow pipeline configuration file:

```groovy title="nextflow.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}
```

This is all that is needed. Run your pipeline with the usual command:
```bash
nextflow run <pipeline_name>.nf 
```

More details are available in the Nextflow [plugin documentation](https://www.nextflow.io/docs/latest/plugins.html#plugins) and the [configuration guide](https://www.nextflow.io/docs/latest/config.html). 

## Contributing
Before contributing, please read the [contribution guidelines](contributing/guidelines.md) carefully. You may also find the recommended [testing setup](contributing/setup.md) helpful.

After your changes are accepted. maintainers may then [publish](contributing/publishing.md) a new version along with your contribution.

## Credits

The `nf-co2footprint` plugin has been mainly developed by:

- [Josua Carl](https://github.com/josuacarl)
- [Nadja Volkmann](https://github.com/nadnein)
- [Júlia Mir-Pedrol](https://github.com/mirpedrol)
- [Sabrina Krakau](https://github.com/skrakau)

at [QBiC](https://www.qbic.uni-tuebingen.de/). Special thanks to [Loïc Lannelongue](https://github.com/Llannelongue) from the [University of Cambridge, UK](https://www.lannelongue-group.org/) for collaboration and contributing to this project.

We additionally thank the following people for their extensive assistance in the development of this plugin:

- [Phil Ewels](https://github.com/ewels)
- [Paolo Di Tommaso](https://github.com/pditommaso)
- [Matthias Hörtenhuber](https://github.com/mashehu)
- [Till Englert](https://github.com/tillenglert)
- [Bastian Eisenmann](https://github.com/Bastian-Eisenmann)
- [Hemant Kumar Joon](https://github.com/hemantjoon)

### How to cite:
```text
J. Carl, N. Volkmann, J. Mir-Pedrol, P. Ewels, S. Nahnsen, S. Krakau nextflow-io/nf-co2footprint v1.0.0. (Jun., 2025). nextflow-io. Available: https://github.com/nextflow-io/nf-co2footprint
```
```Bibtex
@software{nf_co2footprint_plugin,
    author =    {Josua Carl and
                 Nadja Volkmann and
                 Júlia Mir-Pedrol and
                 Phil Ewels and
                 Sven Nahnsen and
                 Sabrina Krakau}
    title   =   {nextflow-io/nf-co2footprint - A Nextflow plugin to estimate the CO2e footprint of pipeline runs}
    month   =   {June}
    year    =   {2025}
    publisher = {Nextflow-io}
    version =   {v1.0.0}
    url     =   {https://doi.org/10.5281/zenodo.14622304}
    doi     =   {10.5281/zenodo.14622304}
}
```

--- 

## Data Attribution

### Carbon intensity

This project uses carbon intensity (CI) data from [Electricity Maps](https://www.electricitymaps.com/) under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/1-0/).  
Depending on the configuration, either historical yearly data from 2024 is used or real-time CI values are accessed via the Electricity Maps API.

> **Electricity Maps: Carbon Intensity Data**  
> Electricity Maps (2025). 2024 Yearly Carbon Intensity Data (Version January 27, 2025).  
> Electricity Maps. [https://www.electricitymaps.com](https://www.electricitymaps.com)

You are free to use, share, and adapt the data under the terms of the ODbL. For more details and attribution requirements, see the [NOTICE](https://github.com/nextflow-io/nf-co2footprint/blob/master/NOTICE) file.

### CPU TDP data

To estimate the CPU power draw this project uses CPU TDP data from [WikiChip](https://en.wikichip.org/wiki/WikiChip) under the [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) license.

> **WikiChip: CPU Data**  
> WikiChip LLC (2025). CPU Data including Thermal Design Power (TDP) (Version June 20, 2025).  
> WikiChip. [https://en.wikichip.org/wiki/WikiChip](https://en.wikichip.org/wiki/WikiChip)

You are free to use, share, and adapt the data under the terms of the CC BY-NC-SA 4.0. For more details and attribution requirements, see the [NOTICE](https://github.com/nextflow-io/nf-co2footprint/blob/master/NOTICE) file.
