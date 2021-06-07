# nf-hello plugin 
 
This project shows how to implement a simple Nextflow plugin named `nf-hello` that intercepts 
workflow execution events to print a message when the execution starts and on workflow completion.

## Plugin assets 
                    
- `settings.gradle`
    
    Gradle project settings. 

- `plugins/nf-hello`
    
    The plugin implementation base directory.

- `plugins/nf-hello/build.gradle` 
    
    Plugin Gradle build file. Project dependencies should be added here.

- `plugins/nf-hello/src/resources/META-INF/MANIFEST.MF` 
    
    Manifest file defining the plugin attributes e.g. name, version, etc.
    The attribute `Plugin-Class` declares the plugin main class. This class 
    should extend the base class `nextflow.plugin.BasePlugin` e.g. 
    `nextflow.hello.HelloPlugin`.

- `plugins/nf-hello/src/resources/META-INF/extensions.idx`
    
    This file declares one or more extension classes provided by the plugin. 
    Each line should contain a Java class fully qualified name implementing 
    the interface `org.pf4j.ExtensionPoint` (or a sub-interface).

- `plugins/nf-hello/src/main` 

    The plugin implementation sources.

- `plugins/nf-hello/src/test` 
                             
    The plugin unit tests. 

## Compile & run unit tests 

Run the following command in the project root directory (ie. where the file `settings.gradle` is located):

    ./gradlew check

## Run and debug plugin in the development environment

To run and test the plugin in the development environment, configure a local Nextflow build 
using the following steps:

1. Clone the Nextflow repository in your computer into a sibling directory:

    ```
    git clone --depth 1 https://github.com/nextflow-io/nextflow ../nextflow
    ```
  
2. Instruct the plugin build setting to use the local Nextflow code, adding the following 
  line in the file `settings.gradle`: 
   
    ```
    echo "includeBuild('../nextflow')" >> settings.gradle
    ```
  
  (make sure to not add it more than once..)

3. Compile the plugin along the Nextflow code, with this command:

    ```
    ./gradlew compileGroovy
    ```

4. Run Nextflow with plugins using the `./launch.sh` script as a drop-in replacement for the `nextflow` command and 
  adding the option `-plugins nf-hello` to load the built plugin:
   
    ```
    ./launch.sh run nextflow-io/hello -plugins nf-hello
    ```

## Package, upload and publish

The project should hosted in a GitHub repository whose name should match the name of the plugin,
that is the name of the directory in the `plugins` folder e.g. `nf-hello` in this project.

Following these step to package, upload and publish the plugin:

1. Create a file named `gradle.properties` in the project root containing the following attributes
   (this file should not be committed in the project repository):

  * `github_organization`: the GitHub organisation the plugin project is hosted
  * `github_username` The GitHub username granting access to the plugin project.
  * `github_access_token`:  The GitHub access token required to upload and commit changes in the plugin repository.
  * `github_commit_email`:  The email address associated with your GitHub account.

2. The following command, package and upload the plugin in the GitHub project releases page:

    ```
    ./gradlew :plugins:nf-hello:upload
    ```

3. Create a pull request against the [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) 
  project to make the plugin public accessible to Nextflow app. 

