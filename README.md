# nf-co2footprint plugin [WIP]

A Nextflow plugin to estimate the CO<sub>2</sub> footprint of pipeline runs.

## 📚 Docs 👉🏻 <https://nextflow-io.github.io/nf-co2footprint>

## Introduction

The nf-co2footprint plugin estimates the energy consumption for each pipeline task based on the Nextflow resource usage metrics and information about the power consumption of the underlying compute system.
The carbon intensity of the energy production is then used to estimate the respective CO<sub>2</sub> emission.

The calculation is based on the carbon footprint computation method
developed in the Green Algorithms project: www.green-algorithms.org

> **Green Algorithms: Quantifying the Carbon Footprint of Computation.**
> 
> Lannelongue, L., Grealey, J., Inouye, M.,
> 
> Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707

The nf-co2footprint plugin generates a detailed TXT carbon footprint report containing the energy consumption, the estimated CO<sub>2</sub> emission and other relevant metrics for each task.
Additionally, an HTML report is generated with information about the carbon footprint of the whole pipeline run and containing plots showing, for instance, an overview of the CO<sub>2</sub> emissions for the different processes.

## Quick Start

Declare the plugin in your Nextflow pipeline configuration file:

```groovy title="nextflow.config"
plugins {
  id 'nf-co2footprint@1.0.0-beta'
}
```

This is all that is needed. Run your pipeline with the usual command.

You can find more information on plugins in the [Nextflow documentation](https://www.nextflow.io/docs/latest/plugins.html#plugins).

> [!NOTE]
> To test the plugin prior to its first release, refer to the [contributing documentation](contributing/setup.md).

## Credits

The nf-co2footprint plugin is mainly developed and maintained by [Sabrina Krakau](https://github.com/skrakau) and [Júlia Mir-Pedrol](https://github.com/mirpedrol) at [QBiC](https://www.qbic.uni-tuebingen.de/).

We thank the following people for their extensive assistance in the development of this pipeline:

- [Phil Ewels](https://github.com/ewels)
- [Paolo Di Tommaso](https://github.com/pditommaso)