---
title: CO2footprint-measures
description: Definition of CO2 footprint
hide:
  - toc
---

# CO2e Footprint Measures

A CO<sub>2</sub> equivalent (CO<sub>2</sub>e) is a metric used to compare the emissions from various greenhouse gases based on their impact on global warming.

For this, the amounts of other gases are converted to the amount of CO<sub>2</sub> that would have the same impact on global warming (over a 100-year period).

The formula used for the calculation of the carbon footprint ($CO_2e$) of one Nextflow task is:

$CO_2e = t \cdot (n_c \cdot P_c \cdot u_c + n_m \cdot P_m) \cdot PUE \cdot CI$

where

**$t$** = the running time of the computation (h)<br/>
**$n_c$** = the number of cores<br/>
**$n_m$** = the size of memory available (GB)<br/>
**$u_c$** = the core usage factor (between 0 and 1)<br/>
**$P_c$** = the power draw of a computing core (W)<br/>
**$P_m$** = the power draw of memory (W, per GB)<br/>
**$PUE$** = the efficiency coefficient of the data centre<br/>
**$CI$** = the carbon intensity of energy production, which represents the carbon footprint of producing 1 kWh of energy for a given country and energy mix

For the final CO<sub>2</sub>e estimation of a pipeline run, the values of all cached and completed tasks are summed up.
This includes failed tasks as well.

!!! warning

    Note that the usage of GPUs is not yet supported.

## Used data

If the `location` parameter is specified, the plugin makes use of [location-specific CI data](https://github.com/nextflow-io/nf-co2footprint/blob/master/plugins/nf-co2footprint/src/resources/CI_aggregated.v2.2.csv) that was copied from the Green Algorithms project [green-algorithms-tool/data](https://github.com/GreenAlgorithms/green-algorithms-tool/tree/master/data).
The CPU TDP data from the Green Algorithms project is used to retrieve model-specific CPU power draw values.


## References

> **Green Algorithms: Quantifying the Carbon Footprint of Computation.**
> 
> Lannelongue, L., Grealey, J., Inouye, M.,
> 
> Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707