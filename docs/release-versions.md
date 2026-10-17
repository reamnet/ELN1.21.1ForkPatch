# Release versions

ForkPatch updates now use matching source tags, mod versions and jar filenames.

| Version / source tag | Previous update name | Changes |
| --- | --- | --- |
| `v3.0.1` | ForkPatch 1 | Device text editing, wireless lamp configuration and chunk loading |
| `v3.0.2` | ForkPatch 2 | Large Generator startup recovery, including bare aluminum cable |
| `v3.0.3` | ForkPatch 3 | Transformer recovery, visible wire covers, creative grouping and utility street lights |
| `v3.0.4` | ForkPatch 4 | Shared generator/converter solving to prevent low-voltage stalls and blinking |
| `v3.0.5` | ForkPatch 5 | Faster pole converters, long-line stability and 10 kW mains step-down units |
| `v3.0.6` | — | Stop multiplying converter probes by the legacy 50-pass setting; reduce gauge allocations |

For example, compiling `v3.0.4` produces `ElectricalAge-3.0.4.jar`; compiling
`v3.0.5` produces `ElectricalAge-3.0.5.jar`. The earlier `v3.0.0-port-forkpatch.N`
tags remain available for the original source history. The numbered tags add
build version metadata to those updates without changing their gameplay code.
This description of metadata-only changes applies to `v3.0.1` through `v3.0.5`;
later versions contain the gameplay/simulator changes listed above. Original
release assets remain available with their original filenames.

`modVersion` in `gradle.properties` is the version source for the jar filename,
NeoForge metadata and the in-game version. Increment it when preparing the next
update and create the matching `vX.Y.Z` source tag. For unpublished experiments,
use an explicit override such as `-PmodVersion=3.0.7-local.1`.

Build with `./gradlew build` to retain previous versioned jars in `build/libs/`.
`./gradlew clean` removes that directory, and recompiling the same version
replaces the jar for that version. Install only one Electrical Age jar in each
game's `mods/` folder. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
