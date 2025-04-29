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