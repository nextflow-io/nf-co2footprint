---
title: Setup
description: How to setup your environment to test the nf-co2footprint plugin locally
---

# Testing and debugging

## Option 1: 🛠️ Launch with local Nextflow build

To test with Nextflow for development purpose:

1. Clone the Nextflow repo into a sibling directory

   ```bash
   cd .. && git clone https://github.com/nextflow-io/nextflow
   cd nextflow && ./gradlew exportClasspath
   ```
2. Append the following line to the `settings.gradle` in this project:

   ```bash
   includeBuild('../nextflow')
   ```
3. Compile the plugin code

   ```bash
   ./gradlew compileGroovy
   ```
4. Run nextflow with this command:

   ```bash
   ./launch.sh run -plugins nf-co2footprint <script/pipeline name> [pipeline params]
   ```

## Option 2: 🚀 Launch via regularly installed Nextflow

The plugin can be tested without using a local Nextflow build using the following steps:

!!! warning

    This will install the compiled plugin code into your `$NXF_PLUGINS_DIR` directory (default: `${HOME}/.nextflow/plugins`). 
    If the plugin version from the manifest file (`plugins/nf-co2footprint/src/resources/META-INF/MANIFEST.MF`) of the dev code matches an existing plugin, any install will be overwritten.

1. Compile and install the plugin code

   ```bash
   ./gradlew compileGroovy
   make install
   ```
2. Run nextflow with this command, specifying the plugin version:

   ```bash
   nextflow run -plugins nf-co2footprint@1.0.0-rc.1 <script/pipeline name> [pipeline params]
   ```


## 🧪 Compiling and running tests

To compile and run the tests use the following command:

```bash
./gradlew check
```


## 📄 Change and preview the docs

The docs are generated using [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/). To change the docs, edit the files in the [docs/](https://github.com/nextflow-io/nf-co2footprint/tree/master/docs) folder and run the following command to generate the docs (after installing mkdocs via `pip install mkdocs-material`):

```bash
mkdocs serve
```

To preview the docs, open the URL provided by mkdocs in your browser.