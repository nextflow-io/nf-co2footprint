---
title: Publishing
description: How to publish the nf-co2footprint
---

# Publishing

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
    ./gradlew :plugins:nf-co2footprint:upload
    ```
3. Create a pull request against [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) to make the plugin accessible to Nextflow.