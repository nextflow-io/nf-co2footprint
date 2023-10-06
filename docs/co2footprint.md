---
title: CO2footprint-measures
description: Definition of CO2 footprint
hide:
  - toc
---

# CO2e Footprint Measures

A CO<sub>2</sub> equivalent (CO<sub>2</sub>e) is a metric used to compare the emissions from various greenhouse gases based on their impact on global warming.

For this, the amounts of other gases are converted to the amount of CO<sub>2</sub> that would have the same impact on global warming (over a 100-year period).

The equation used for the calculation of the carbon footprint ($C$) is:

$C = t * (n_c * P_c * u_c + n_m * P_m) * PUE * CI$

Being:

- **$t$** the running time of the computation (hours) 
- **$n_c$** the number of cores
- **$n_m$** the size of memory available (gigabytes)
- **$u_c$** the core usage factor (between 0 and 1)
- **$P_c$** the power draw of a computing core 
- **$P_m$** the power draw of memory (Watt)
- **$PUE$** the efficiency coefficient of the data centre
- **$CI$** the carbon intensity of energy production, which represents carbon footprint of producing 1 kWh of energy for a given country and energy mix.

## References

> **Green Algorithms: Quantifying the Carbon Footprint of Computation.**
> 
> Lannelongue, L., Grealey, J., Inouye, M.,
> 
> Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707