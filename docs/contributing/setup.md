---
title: Contribution instructions
description: How to contribute to nf-co2footprint
---

# Getting started with plugin development

## Compiling

To compile and run the tests use the following command:

```bash
./gradlew check
```

## Launch without a local Nextflow build

The plugin can be tested prior release without using a local Nextflow build using the following steps:

1. Build the plugin: `make buildPlugins`
2. Copy `build/plugins/nf-co2footprint-<version>` to `$HOME/.nextflow/plugins`
4. Run nextflow with this command:

   ```bash
   ./launch.sh run -plugins nf-co2footprint@0.1.0 <script/pipeline name> [pipeline params]
   ```

## Launch it with Nextflow

To test with Nextflow for development purpose:

1. Clone the Nextflow repo into a sibling directory

   ```bash
   cd .. && https://github.com/nextflow-io/nextflow
   cd nextflow && ./gradlew exportClasspath
   ```

2. Append to the `settings.gradle` in this project the following line:

   ```bash
   includeBuild('../nextflow')
   ```

3. Compile the plugin code

   ```bash
   ./gradlew compileGroovy
   ```

4. run nextflow with this command:

   ```bash
   ./launch.sh run -plugins nf-co2footprint <script/pipeline name> [pipeline params]
   ```

## Change and preview the docs

The docs are generated using [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/). To change the docs, edit the files in the [docs/](docs/) folder and run the following command to generate the docs (after installing mkdocs via `pip install mkdocs-material`):

```bash
mkdocs serve
```

To preview the docs, open the URL provided by mkdocs in your browser.