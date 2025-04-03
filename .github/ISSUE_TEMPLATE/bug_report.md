---
name: Bug report
about: Report a bug to help us improve
---

## Bug report 

(Please follow this template by replacing the text between parentheses with the requested information)

### Expected behavior and actual behavior

(Give a brief description of the expected behavior and actual behavior)

### Steps to reproduce the problem

(Provide a test case that reproduces the problem either with a self-contained script or GitHub repository)

### Program output 

(Copy and paste the output produced by the failing execution. Please highlight it as a code block. Whenever possible upload the `.nextflow.log` file.)

### Environment 

* nf-co2footprint plugin version: [?]
    * <details>
      <summary>How to get the plugin version</summary>
        * Option 1: Report the version behind the `@` in following command. 
          * ```bash
            nextflow config | grep nf-co2footprint
            ```
        * Option 2: Run a small pipeline to get the version that is downloaded with no further specification.
          * ```bash
            nextflow run nextflow-io/hello -plugins nf-co2footprint | grep nf-co2footprint
            ```
      </details>
* Nextflow version: [?]
  * <details>
    <summary>How to get the Nextflow version</summary>
      * Option 1: Run the following script.
        * ```bash
          nextflow -version
          ```
  </details>
* Java version: [?]
  * <details>
    <summary>How to get the Java version</summary>Option 1:
    * ```bash
      java --version
      ```
    </details>
* Groovy version: [?]
  * <details>
    <summary>How to get the Groovy version</summary>Option 1:
    * ```bash
      groovy --version
      ```
    </details>
* Operating system: [macOS, Linux, etc]
  * <details>
    <summary>How to get the Operating sytem</summary>
      * OS-specific. But chances are, that you at least know whether you sit in front of a Mac, Windows or Linux. But if you want you may give us your `screenfetch` (Make sure to omit personal info).
    </details>
* Shell version: [?]
  * <details>
    <summary>How to get the Shell version</summary>Option 1: 
    * `$SHELL --version`
    </details>

### Additional context

(Add any other context about the problem here)
