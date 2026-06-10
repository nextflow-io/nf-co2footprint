### Development of the nf-co2footprint plugin
Thank you for your interest in contributing to the development of the nf-co2footprint plugin! We welcome contributions of all kinds, including bug fixes, new features, documentation improvements, and more. Below you will find some guidelines to help you get started.

#### PR Template
When you create a PR you will be prompted to fill out this short template. Please do so, but you may also drop some point if they are not applicable.

```Markdown
🎯 Motivation
Why is this change needed?

📋 Summary of changes
- Short bullet points describing the changes you have made.

## ✅ Checklist
- [ ] New functionalities are covered by tests
- [ ] Class structure in `test` reflects class structure in `main`
- [ ] Documentation reflects changed behaviour
- [ ] `README.md` contains information on or reference to new features
- [ ] `CHANGELOG.md` is updated with a note on your changes
- [ ] Ensure all tests pass (`.\gradlew check`)
```

#### Testing
When you run the tests, either through your IDE or via `make test` / `.\gradlew check`, several tests will be done to ensure the core functionality of the plugin. They are by no means comprehensive, but they will help to catch some common issues.

If you have added new functionality, please consider adding tests for it as well.

##### Snapshot testing
When a test fails due to a mismatch between the generated output and the expected output, the plugin will automatically generate snapshots of the new output files in the `build/resources/test` directory.
If your changes affect the output, please review the generated snapshots and update their counterparts in the `src/testResources` folder. This way, we can ensure that future tests will compare against the correct expected output.
The line-by-line comparison of several IDEs can be very helpful for this task.

After you have updated the output files, you may have to update the expected MD5 checksums, number of lines, and special lines (such as lines with local date-times) in the corresponding `file_checks.json`.
You can do this by extracting the information from the test output or, if necessary, running the tests again.
