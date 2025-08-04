## [Unreleased]

# Version 1.0.0-rc.4
## Bug Fixes:
- Correct ANSI line reset

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
- Added requests to report warnings as Github issues
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