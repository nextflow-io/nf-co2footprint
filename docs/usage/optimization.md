---
title: Optimization
description: How to improve the emission profile of pipeline runs.
---

### Resource allocation

#### On similar and repeated runs
We all know it, something went wrong and now the workflow needs to be runs again.
Regarding the carbon footprint it is almost always better to allocate enough resources to avoid duplicate runs,
but if you have to repeat a run or apply a similar dataset, optimizing resource allocation can save some energy.

#### Memory
If possible, the [HTML report file](./output.md#output-report) provides a recommendation on how to adjust the allocated memory of each process, based on its peak resident set size (RSS).
A 20% buffer is included to avoid running out of memory, and the result is rounded up to the nearest GB:

$$
\mathrm{Mem}_{proc}^{\mathrm{rec}} = \left\lceil 1.2 \cdot \max_{task}\, \mathrm{RSS}_{proc}(task) \right\rceil_\mathrm{GB}
$$

where $proc$ denotes the process and the maximum is taken over its runtime.