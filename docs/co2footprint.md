---
title: CO2footprint-measures
description: Definition of CO2 footprint
hide:
  - toc
---

# CO2e Footprint Measures

A CO<sub>2</sub> equivalent (CO<sub>2</sub>e) is a metric used to compare the emissions from various greenhouse gases based on their impact on global warming.

For this, the amounts of other gases are converted to the amount of CO<sub>2</sub> that would have the same impact on global warming (over a 100-year period).

The formula used for the calculation of the carbon footprint ($C$) is:

$C = t \cdot (n_c \cdot P_c \cdot u_c + n_m \cdot P_m) \cdot PUE \cdot CI$

Being:
$$
\begin{alignat*}{2}
t & =  \text{the running time of the computation (h)} \\
n_c & =  \text{the number of cores} \\
n_m & =  \text{the size of memory available (GB)} \\
u_c & =  \text{the core usage factor (between 0 and 1)} \\
P_c & =  \text{the power draw of a computing core (W)} \\
P_m & =  \text{the power draw of memory (W, per GB)} \\
PUE & =  \text{the efficiency coefficient of the data centre} \\
CI & = \text{the carbon intensity of energy production, which represents the carbon footprint of producing 1 kWh of energy for a given country and energy mix}
\end{alignat*}
$$

## References

> **Green Algorithms: Quantifying the Carbon Footprint of Computation.**
> 
> Lannelongue, L., Grealey, J., Inouye, M.,
> 
> Adv. Sci. 2021, 2100707. https://doi.org/10.1002/advs.202100707