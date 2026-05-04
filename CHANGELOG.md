# New
## Bug Fixes:

## Misc:

## Features:

# 1.3.0
## Bug Fixes:
- CPU power model now applied correctly
- Unintended report value removal
- Fixed floating point error for numerical conversion division

## Misc:
- Improved workflow reporting form extension/CLI by deriving and injecting workflow metadata from the provided trace file
- Added full integration test
- Adapted to Nextflow 26
- Reworked `Bytes`, such that it can take binary and decimal-based values

## Features:
- Transformation of data file to provenance file with schema.org / bioschemas.org type annotation in JSON-LD data format

# 1.2.1
## Bug Fixes:
- Total values from workflow in report
- Missing newlines in non-ANSI logging

## Misc:
- Aligned Session tracking with Nextflow's trace tracking

## Features:
- New CI reporting of failed file comparisons

# 1.2.0
## Bug Fixes:
- Plot display of titles and axes names in report
- Accumulation of dates
- Never initialized observer called for session emissions on CLI command
- Included descendant processes into session tracking

## Misc:
- Improved the testing of log messages
- Removed some classes that became outdated after Nextflow added support for mixed-typed Config Options
- New minimum Nextflow version: 25.10.0
- Added recommendation for market-based CI usage
- Single values in process summary plot
- Numbers that are not in Double format are now accepted for config values

## Features:
- Updated the config syntax inline with standard Nextflow style
- Usage of tree structure for result value propagation and summary / accumulation
- Configuration now represented in Nextflow Syntax Extension and V2 Parser
- Provided an extension point so trace files can be processed post-run through a pipeline
- Provided an CLI option to process execution trace files post-run
- Added new raw energy consumption metrics into report and trace file
- `cpus` is now at least `ceil(%cpu / 100)`
- Usage of TDP per logical core, becaus this corresponds best to the `cpus` trace metric
- Tracking of whole session emissions
- Precursor of machine-actionable file

# Version 1.1.0
## Bug Fixes:
- Conversion of time leaving trailing full bigger units (e.g. 60s instead of 1min)
- Small stylistic overreaches in report fixed
- Hard-code locale for decimal separator

## Misc:
- Renamed FileCreator and its classes
- Added `Markers.silentUnique` to deduplicate messages without prepending a prefix
- Content of total CO2e metrics now in tabs instead of <details> block
- HTML & CSS clarity improvements through comments and block separation
- Removed some unnecessary conversions in the Computer, JavaScript and Tests
- Removed `utils` and `HelperFunctions` in favor of `Metrics` class
- Moved from `plotly.min.js` to `plotly-custom.min.js` for smaller report file size
- Migration to new plugin template (https://github.com/nextflow-io/nf-plugin-template)
- Added link to documentation to warnings about unknown CPUs
- Added separate energy measurements of Processor and Memory to CO2Record

## Features:
- Base config class with more control over parameters
- Added sample outputs to documentation
- Support for custom polynomial CPU power models via the `cpuPowerModel` configuration parameter
- Automatic detection of AWS region and mapping to geographic Zone ID
- Verbose output after run including major metrics

# Version 1.0.0
## Bug Fixes:
- Wrong memory fallback value (current machine maximum memory) now removed
- Creation of report and summary file only at end of run

## Misc:
- "🔁" now prepended for every `Markers.unique` message
- Shortened JavaScript to make page more static and avoid code duplicates

# Version 1.0.0-rc.3
## Bug Fixes:
- Core logging no longer hidden after plugin invocation

## Features:
- Deduplication of task-specific messages

# Version 1.0.0-rc.2
## Bug Fixes:
- Logging with Logback < 1.5 (Nextflow < 25) now possible
- Removed faulty error messages on `customTDPTable` loading
- Failed uploads to Zenodo

## Features:
- Better matching of CPU names by core-specific specification strings like '32-core' optional

# Version 1.0.0-rc.1
## Bug Fixes:
- Version is `null` in many cases (parsed the wrong MANIFEST)
- Logging is overwritten by Nextflow (now integrated in Groovy code)
- Logging of simultaneous duplicated output now also unique
- Trace report now contains all headers to produce a correct TSV file
- Fixed premature test exit on file checking

## Misc
- Added a different wording for EM API calls with hints to obtain API key
- Added a better help request for missing chips
- Cleanup of AWS TDP table & renaming of TDP table to standard name
- Aligned style of expandable elements

# Version 1.0.0-rc
## Features:
- When a message is excluded from the log it is still sent to the `trace` level log
- Speedier Report generation through refactoring of Co2 aggregation
- Merge provided custom TDP matrices into the old `TDPDataMatrix`, instead of fully replacing it
- Added metric to indicate newly generated / non-cached CO2 emissions into report
- Added `ciMarket` to account for differences to the local grid
- Added plot to report, displaying the carbon intensity during the workflow execution & the CPU usage of the tasks

## Bug Fixes:
- Adjusted rendering of flights to deliver percentage < 1.0 flights and number of flights afterwards
- Add null checks, fallbacks, and logging to CO₂ calculation in `CO2FootprintComputer` class
- Enabled access to `store` of `CO2Record`s within parent methods
- Check row replacement upon supplying a customTDPDataTable

## Misc
- Moved call to OS when memory is exceeded into this case to avoid unnecessary calls
- Testing with MD5 sums for file creation
- Deescalated access rights of variables for tighter scope
- Extended documentation for CO2e equivalents
- Testing of files via saved snapshots when checksum fails
- Removal of inherited methods in `CO2Record`
- Adjusted folder structure of tests to main
- Added a method to extend DataMatrix by rows
- Added requests to report warnings as GitHub issues
- Added template to report missing chips
- Changed Javascript method to use Converter methods
- Modified Aggregator to include TraceRecords for tracking of CACHED processes
- Moved non nf-hello template files into categorizing folder structure (except `CO2FootprintComputer`)
- Changed missing executor logging to indicate a still functioning run
- Added "How to cite" & CITATION.cff
- Added warning when using 'cloud' `machineType`
- Added CPUs from WikiChip to work around licensing issues
- Added CPU TDP data from WikiChip
- Adapting to changes of checked files is now easier through reporting and snapshot copying

# Version 1.0.0-beta1
## Features:
- Plot co2e and energy in one plot with two axis.
- Report nf-co2footprint version in `co2footprint_report_*.html` and `co2footprint_summary_*.txt` reports.
- Show Plugin parameters in html and text reports.
- Show CO2 equivalences using scientific annotations in the html reports.
- Show CO2 equivalences in text reports.
- Updated documentation.

## Bug Fixes:
- Improved numbering in contribution instructions
- Improved sorting in html report summary

# Version 1.0.0-beta

Initial pre-release.
