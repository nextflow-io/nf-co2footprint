# Contribution guidelines

Hi! Thanks for contributing to the nf-co2footprint plugin for Nextflow 😊

Please read the following to help us maintain and ensure high-quality code.

## 🖋️ Signing commits
When submitting a pull-request (PR), please sign-off the [DCO](https://developercertificate.org/) to certify that you are the author of the contribution and you adhere to [Nextflow's open source license](https://github.com/nextflow-io/nextflow/blob/master/COPYING) by adding a `Signed-off-by` line to the contribution commit message. See [here](https://github.com/apps/dco) for more details.

## 🚩 Github's issue tracking
- When opening a pull-request that connects to an Issue, please mention it in the opening comment.
- Assign labels / types to PRs / issues when possible

## 🏗️ Create draft pull-requests
- This tells us that you are working on something, even during early stage of development. Don't worry about everything being perfect already, that's what it's for.
- If you are unable to finish the PR, someone may easily pick up the work where you left off.

## 💬 Comment your code
- Use docstrings
  - For methods:
    - ```groovy
      /**
      * The recreates an iconic greeting.
      *
      * @param The name of the greeted person
      * @param The voice of the second character represented by number
      * @return The greeting
      */
      static def writeGreeting(String name, Integer voice=null) {
          String voiceType = switch(voice) {
              case 0 -> 'happy'
              case 1 -> 'evil'
              default -> 'robotic'
          }
          String greeting = """\
          - ${name}: Hello there...
          - Grievous: [${voiceType} voice] General ${name}!
          """.stripIndent()
    
          return greeting
      }
      ```
  - For classes:
    - ```groovy
      /**
      * This class creates dialogues.
      */
      class Dialogues extends Scene {
          ...
      }
      ```
- Use one line comments for variables
  - ```groovy
    final answer = 42 // This variable contains the answer to the sense of life itself
    ```


## ☑️ Unit testing

To run your unit tests, run the following command in the project root directory (ie. where the file `settings.gradle` is located):
```bash
./gradlew check
```


## 🧪 Testing and debugging

To build and test the plugin during development, configure a local Nextflow build with the following steps:

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
    make assemble
    ```

4. Run Nextflow with the plugin, using `./launch.sh` as a drop-in replacement for the `nextflow` command, and adding the option `-plugins nf-hello` to load the plugin:
    ```bash
    ./launch.sh run nextflow-io/hello -plugins nf-hello
    ```

## 🧪 Testing without Nextflow build

The plugin can be tested without using a local Nextflow build using the following steps:

1. Build the plugin: `make buildPlugins`
2. Copy `build/plugins/<your-plugin>` to `$HOME/.nextflow/plugins`
3. Create a pipeline that uses your plugin and run it: `nextflow run ./my-pipeline-script.nf`

> To test the plugin prior to its first release, refer to the [contributing documentation](contributing/setup.md).


## 📡 Package, upload, and publish

The project should be hosted in a GitHub repository whose name matches the name of the plugin, that is the name of the directory in the `plugins` folder (e.g. `nf-hello`).

Follow these steps to package, upload and publish the plugin:

1. Create a file named `gradle.properties` in the project root containing the following attributes (this file should not be committed to Git):
   - `github_organization`: the GitHub organisation where the plugin repository is hosted.
   - `github_username`: The GitHub username granting access to the plugin repository.
   - `github_access_token`: The GitHub access token required to upload and commit changes to the plugin repository.
   - `github_commit_email`: The email address associated with your GitHub account.
2. Use the following command to package and create a release for your plugin on GitHub:
    ```bash
    ./gradlew :plugins:nf-hello:upload
    ```
3. Create a pull request against [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) to make the plugin accessible to Nextflow.