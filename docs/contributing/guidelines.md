---
title: Guidelines
description: How to contribute to nf-co2footprint
---

# Contribution guidelines

Hi! Thanks for contributing to the nf-co2footprint plugin for Nextflow 😊

Please read the following to help us maintain and ensure high-quality code.

## 💅 Follow commit style

Commits should be structured like this: `<Type>: <Message>`, with the following types:

- `Feature` representing an enhancement or new functionality
- `Fix` when addressing a bug
- `Documentation` when mainly improving comments, documentation files or docstrings
- `Refactor` when moving stuff or changing naming, but essentially making no functional changes
- `CI` when changing the continuous integration (guidelines, templates,...)
- `Chore` when just doing something that has to be done once in a while or relates to other changes

## 🖋️ Signing commits

When submitting a pull-request (PR), please sign-off the [DCO](https://developercertificate.org/) to certify that you are the author of the contribution and you adhere to [Nextflow's open source license](https://github.com/nextflow-io/nextflow/blob/master/COPYING) by adding a `Signed-off-by` line to the contribution commit message. See [here](https://github.com/apps/dco) for more details.

## 🚩 Github's issue tracking

- When working on an issue, please assign yourself
- When opening a pull-request that connects to an issue, please mention it.
- Assign labels / types to PRs when possible

## 🏗️ Create draft pull-requests

- This tells us that you are working on something, even during early stage of development. Don't worry about everything being perfect already, that's what it's for.
- If you are unable to finish the PR, someone may easily pick up the work where you left off.

## 💬 Comment your code

- Use docstrings for methods & classes
- Use one line comments for variables

## ☑️ Unit testing

Run unit test frequently to notice faulty code early on. For that purpose also try to keep them brief (i.e. mock as little as possible).

To run your unit tests, run the following command in the project root directory (ie. where the file `settings.gradle` is located):
```bash
make test
```