# nf-hello plugin 
 
This project shows how to implement a simple Nextflow plugin named `nf-hello` which intercepts workflow execution events to print a message when the execution starts and on workflow completion.

The `nf-hello` plugin also enriches the `channel` object with a `producer` and `consumer` method (`reverse` and `goodbye`) which can be used in a pipeline script.

Also exposes some @FunctionS to be used in the pipeline as custom methods 

   NOTE: this repo uses the name `nf-hello` as root name. In case you want to use this repo as starting point for a custom plugin, you need at least to change `settings.gradle` and rename `plugins/nf-hello` folder.

## Plugin structure
                    
- `settings.gradle`
    
    Gradle project settings. 

- `plugins/nf-hello`
    
    The plugin implementation base directory.

- `plugins/nf-hello/build.gradle` 
    
    Plugin Gradle build file. Project dependencies should be added here.

- `plugins/nf-hello/src/resources/META-INF/MANIFEST.MF` 
    
    Manifest file defining the plugin attributes e.g. name, version, etc. The attribute `Plugin-Class` declares the plugin main class. This class should extend the base class `nextflow.plugin.BasePlugin` e.g. `nextflow.hello.HelloPlugin`.

- `plugins/nf-hello/src/resources/META-INF/extensions.idx`
    
    This file declares one or more extension classes provided by the plugin. Each line should contain the fully qualified name of a Java class that implements the `org.pf4j.ExtensionPoint` interface (or a sub-interface).

- `plugins/nf-hello/src/main` 

    The plugin implementation sources.

- `plugins/nf-hello/src/test` 

    The plugin unit tests. 

## Plugin classes

- `HelloConfig`: simple example how to handle configuration options provided via the Nextflow configuration file. 

- `HelloExtension`: show how create an extension class that can be used to create custom channel factories, operation and fuctions that can be imported in the pipeline script as DSL extensions.

- `HelloFactory` and `HelloObserver`: show how to intercept workflow runtime events and react correspondly with custom code.

- `HelloPlugin`: the plugin entry point.


## Unit testing 

Run the following command in the project root directory (ie. where the file `settings.gradle` is located):

```bash
./gradlew check
```

## Testing and debugging

To run and test the plugin in for development purpose, configure a local Nextflow build with the following steps:

1. Clone the Nextflow repository in your computer into a sibling directory:
    ```bash
    git clone --depth 1 https://github.com/nextflow-io/nextflow ../nextflow
    ```
  
2. Configure the plugin build to use the local Nextflow code:
    ```bash
    echo "includeBuild('../nextflow')" >> settings.gradle
    ```
  
   (Make sure to not add it more than once!)

3. Compile the plugin alongside the Nextflow code:
    ```bash
    ./gradlew compileGroovy
    ```

4. Run Nextflow with the plugin, using `./launch.sh` as a drop-in replacement for the `nextflow` command, and adding the option `-plugins nf-hello` to load the plugin:
    ```bash
    ./launch.sh run nextflow-io/hello -plugins nf-hello
    ```

## Testing without Nextflow build

The plugin can be tested without using a local Nextflow build using those steps:

1. generate required artifacts with `make buildPlugins`
2. copy build/plugins/your-plugin to `$HOME/.nextflow/plugins`
3. create a pipeline with your plugin and see in action via `nextflow run ./my-pipeline-script.nf`

## Package, upload and publish

The project should be hosted in a GitHub repository whose name should match the name of the plugin, that is the name of the directory in the `plugins` folder (e.g. `nf-hello`).

Follow these steps to package, upload and publish the plugin:

1. Create a file named `gradle.properties` in the project root containing the following attributes (this file should not be committed to Git):

   * `github_organization`: the GitHub organisation where the plugin repository is hosted.
   * `github_username`: The GitHub username granting access to the plugin repository.
   * `github_access_token`: The GitHub access token required to upload and commit changes to the plugin repository.
   * `github_commit_email`: The email address associated with your GitHub account.

2. Use the following command to package and create a release for your plugin on GitHub:
    ```bash
    ./gradlew :plugins:nf-hello:upload
    ```

3. Create a pull request against [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) to make the plugin accessible to Nextflow.
