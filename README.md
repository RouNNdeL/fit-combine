# Introduction
This is a simple utility used to combine 2 FIT files. 
It will automatically attempt to detect the time difference between the 2 files, 
based on the GPS data.
It allows combining one or more fields from one file with the other file (can also average fields between the files).
It'll also optionally interpolate missing data points for selected fields.

# Installation
Since the FIT SKD's license forbids distribution of the SDK, I cannot provide a runnable JAR file.
The SDK can be downloaded from the [Garmin Website](https://www.thisisant.com/developer/resources/downloads/).

# Usage
`fit-combine <dir>` - attempts to load the latest 2 files from a given directory.

`fit-combine <file1> <file2>` - uses the provided files.

# Disclaimer
*FIT protocol or any associated documentation are the exclusive property of Garmin.*
