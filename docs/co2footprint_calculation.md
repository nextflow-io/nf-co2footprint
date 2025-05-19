---
title: CO₂e calculation
description: Definition of CO2 footprint
hide:
  - toc
---

# CO₂e calculation

A **CO₂ equivalent (CO₂e)** is a metric used to compare the emissions from various greenhouse gases based on their impact on global warming. For this, the amounts of other gases are converted to the amount of CO₂ that would have the same impact on global warming (over a 100-year period).

The formula used for the calculation of the carbon footprint (CO₂e) of one Nextflow task is based on the methodology introduced by [Green Algorithms](https://doi.org/10.1002/advs.202100707), which provides a standardized way to estimate the carbon footprint of computational tasks:

$$ 
\begin{equation*}
\text{CO₂e (g)} = t \times \left( n_c \times u_c \times P_c + n_m \times P_m \right) \times PUE \times CI 
\end{equation*}
$$ 

where

$$
\begin{aligned}
t   & = \text{runtime of the computation (h)} \\
n_c & = \text{number of cores} \\
u_c & = \text{core usage factor (between 0 and 1)} \\
P_c & = \text{power draw per core (W)} \\
n_m & = \text{size of memory available (GB)} \\
P_m & = \text{power draw of memory (W, per GB)} \\
PUE & = \text{Power Usage Effectiveness of the data center} \\
CI & = \text{carbon intensity of energy production, which is the amount of CO₂e emitted per kWh of energy, depending on the energy mix of a region}
\end{aligned}
$$

For the final CO₂e estimation of a pipeline run, the values of all cached and completed tasks are summed up.
This includes failed tasks as well.

!!! note

    The usage of GPUs is not yet supported.

--- 

## References

> **Green Algorithms: Quantifying the Carbon Footprint of Computation**  
> Lannelongue, L., Grealey, J., Inouye, M.,  
> Adv. Sci. 2021, 2100707. [https://doi.org/10.1002/advs.202100707](https://doi.org/10.1002/advs.202100707)