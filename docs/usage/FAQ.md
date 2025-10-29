## Frequently asked questions (FAQ)

<a id="cpu-model"></a>
??? faq "Could not find CPU model"
    In our plugin, we are trying to match your CPU model name to entries in our TDP data table. Unfortunately things are not
    as smooth sailing as we would like it to be.
    
    **Main issues**

    1. Manufacturers have not given us access to their CPU data. While the manufacturers provide information to more than 4000 models
        on their websites, redistribution and automatic parsing of this info needs their permission. We currently use
        [WikiChip](https://en.wikichip.org/wiki/WikiChip) as our main source, which encompasses approximately 1400 models.
        Our current hope is that the new [MIT processor database](https://processordb.mit.edu) will solve this question in the next few months.
    2. Naming of CPU models is inconsistent. Sometimes the true model name ("Model X 1000") is appended with
        extra information ("Model X 1000 @10MHz"), slightly changed ("Manufacturer - Model X 1000"), or hidden ("M1"). While
        we are working on accounting for all these cases, it's not a trivial problem to solve.
    3. Cloud providers sometimes use custom processors that are not publicly listed.
    
    <br>
    **💡 Solution**

    So you encountered a warning like this:
    ```
    [WARN] Could not find CPU model "Model X 1000" in given TDP data table. Using default CPU power draw value (100.0 W).
    ```
    As previously mentioned, all information we need should be out there somewhere — the TDP of your model should be
    easy to find with a quick online search. Once you know the TDP and the number of cores you can either:
    
    - Create a [small table](./parameters.md#custom-tdp-table) with your CPU model names, as they are presented in the warnings, and
      supply the path to this table via the `customCpuTdpFile = <path>` parameter.
    - Set the TDP via `powerdrawCpuDefault = <TDP per core>` and then ignore the warning with `ignoreCpuModel = true`.
    
    For more information see our documentation on [power draw parameters](./parameters.md#hardware-power-draw). You can additionally
    report the model with the ["Missing chip" GitHub issue](https://github.com/nextflow-io/nf-co2footprint/issues/new?template=missing_chip.yaml).