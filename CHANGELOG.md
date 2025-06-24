## [Unreleased]

# Version 1.0.0
## Features:
- When a message is excluded from the log it is still sent to the `trace` level log
- Speedier Report generation through refactoring of Co2 aggregation
- Merge provided custom TDP matrices into the old `TDPDataMatrix`, instead of fully replacing it
- Added metric to indicate newly generated / non-cached CO2 emissions into report

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