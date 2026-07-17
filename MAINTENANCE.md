# Maintenance manual

## New PRs
- Check for passed tests (CI testing on GitHub should be automated)
- Understand what is happening, if you can't, dont hesitate to ask for comments or better code style
- Wait for at least one approval by a maintainer to merge
- Merge into `dev`

## Release
- Follow Nextflow's release guidelines
- `make release` works, if
  - you set an environment variable or an entry in `gradle.properties`: `npr.apiKey=npr_pat_XYZ` (no quotes!)
  - `gradlew` is formatted with the correct line endings (apply `dos2unix gradlew`, if necessary)
- Make a release on GitHub with `<version>` as a tag and `v<version` as release name
  - This will generate an automated message in the nf-co2footprint Slack channel
- Post a message to Slack with the highlights of the release

## Versioning
Versioning happens mostly in the `build.gradle` file, but also documentation and tests may have to be adjusted.

### Nextflow
Sometimes Nextflow changes core code or deprecates older methods. Aim to support the newest version with new releases. Older versions can always use an older plugin version as well.
Setting `NXF_SYNTAX_PARSER=v1` can help to run some older pipelines with a newer Nextflow versions.

### Plugin
The plugin follows syntactic versioning guidelines in the style `MAJOR.MINOR.PATCH`
- A patch should only include fixes and small stylistic adjustments
- A minor version changes behavior of the plugin, but leaves core functions intact
- A major version introduces breaking changes on a large scope or completely reworks large parts of the code

### Dependencies
Dependencies should be updated from time to time to avoid vulnerabilities and profit from better code. However, please avoid experimental versions.

## Tests

### Run
- With `make test` or `./gradlew test`

### Full integration test
- Sometimes the file endings are not correctly transferred by Git, you may need to run `dos2unix src/testResources/integration/prepare-environment.sh`

### Adjusting file checks
Adjusting tests can be tricky, because small changes can change a lot of output files, which leads to multiple failed snapshot comparisons. Don't worry, it's getting quicker the more you do it.

1. run tests (from IDE or `make test`)
2. Check which tests fail
3. Look for files that were generated during tests under `build/resources/test/<TEST_DIR>/failed/<FILE>`
4. Compare new file with old file at `src/testResources/<TEST_DIR>/<FILE>` (A file comparison tool, like IntelliJ's "Compare with" or VSCode's "compare selected", helps a lot)
   - Check for each change whether it was intended and adjust test file accordingly
   - Some things, like changing dates, or file paths are often replaced through a RegEx in the test or excluded from the comparison, you can update those, but don't have to
5. If a checksum is set, rerun after file changes and set the new MD5 checksum.
   - The test will tell you whether unexpected difference between old and new file were still found after you adjusted it.