# Maintenance manual

## New PRs
- Check for passed tests (CI testing on GitHub should be automated)
- Understand what is happening, if you can't, dont hesitate to ask for comments or better code style
- Wait for at least one approval by a maintainer to merge
- Merge into `dev`

## Release
- Follow [Nextflow's plugin guidelines](https://docs.seqera.io/nextflow/plugins/developing-plugins)
- Follow the [Nextflow Gradle plugin guide](https://nextflow.io/docs/latest/guides/gradle-plugin.html#publishing-a-plugin)
- Before releasing, bump the version everywhere it's hardcoded: `build.gradle`, `CITATION.cff` (`version` + `date-released`), `README.md` (citation block), and `CHANGELOG.md`
- Make a release on GitHub with `<version>` as a tag and `v<version>` as release name
  (auto-posts to the nf-co2footprint Slack channel)
- Check that the GitHub release triggered a new archive on [Zenodo](https://zenodo.org/) and that the DOI badge/citation in `README.md` still points to the correct DOI
- Post a message to Slack with the highlights of the release
- `make release` requires:
  - `gradle.properties` in the project root (gitignored, never commit it) with:
    ```properties
    npr.apiKey=npr_pat_XYZ
    ```
  - `gradlew` with LF line endings (apply `dos2unix gradlew` if needed)

## Versioning
Versioning happens mostly in the `build.gradle` file, but also documentation and tests may have to be adjusted.
- Follows `MAJOR.MINOR.PATCH`: patch = fixes/style only, minor = behavior change with core intact, major = breaking changes
- Aim to support the newest Nextflow version; `NXF_SYNTAX_PARSER=v1` can help older pipelines run on newer Nextflow
- Update dependencies periodically for security/features, but avoid experimental versions

### Nextflow
Sometimes Nextflow changes core code or deprecates older methods. Aim to support the newest version with new releases. Older versions can always use an older plugin version as well.
Setting `NXF_SYNTAX_PARSER=v1` can help to run some older pipelines with a newer Nextflow versions.

## Tests
Run with `make test` or `./gradlew test` (see [setup](docs/contributing/setup.md) for details). For integration tests, if file endings break, run `dos2unix src/testResources/integration/prepare-environment.sh`.

### Adjusting failed snapshot tests
Small changes can fail many snapshot comparisons — this is normal - adapting the tests gets quicker with practice.
1. Run tests and not which fail
2. Compare the generated file (`build/resources/test/<TEST_DIR>/failed/<FILE>`) with the snapshot (`src/testResources/<TEST_DIR>/<FILE>`) using a diff tool (found in your IDE)
3. Update the snapshot if the change was intended (dates/paths are often regex-replaced or excluded, so usually don't need updating)
4. If a checksum is set, rerun after updating and set the new MD5 checksum
   - The test will tell you whether unexpected difference between old and new file were still found after you adjusted it.