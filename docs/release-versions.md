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
| `v3.0.7` | — | Recover large joined grids across source current limits; isolate rejected step-down loads before tripping healthy supplies |
| `v3.0.8` | — | Keep external pole loads powered: couple motor drives to the solver, stabilize idle/zero-transfer operation and numerical probing, handle tiny idle loads at HV voltage resolution, and isolate failed transformers locally |
| `v3.0.9` | — | Continuous overhead cable meshes, an attached streetlight model with a dark off state, optional dusk control, hand-installed step-down units and working pole meters |
| `v3.0.10` | — | Saved Up/Down streetlight height controls, with the fixture and emitted light moving together |
| `v3.0.11` | — | Rotate streetlights around their poles to follow angled streets, with fine adjustment, saved angles and matching light positions |
| `v3.0.12` | — | Five new lighting fixtures with all bulb technologies, wireless power, optional wired power, multi-bulb inventories and custom models |
| `v3.0.13` | — | Compact dot spotlights, porch lantern sconces, basic lamp holders, and parking lights raised by two blocks with saved-pole clearance updates |
| `v3.0.14` | — | Programmable UPS with two 24-battery racks, automatic backup and charging, per-cell energy preservation, and a compact status GUI |
| `v3.0.15` | — | 23 new lighting fixtures, slab/table mounting, five working kitchen appliances, UPS backup/overload beeps and corrected legacy generator voltage stamping |
| `v3.0.16` | — | Automatic transfer switch, wireless generator start, 10 kW programmable gasoline standby generator and generic tagged/container/pipe fuel support |
| `v3.0.17` | — | MPPT solar controller, hybrid 120/240 V inverter with selectable grid priority, large 3.2 kV wind turbine and watermill |

| `v3.0.18` | — | 30 home, LED, grand chandelier, heavy-duty, offshore, street and seasonal lights; wireless wall controls; larger UPS cells; two pole variants; standby engine sound and exhaust; ATS contactor clicks |

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
use an explicit override such as `-PmodVersion=3.0.19-local.1`.

Build with `./gradlew build` to retain previous versioned jars in `build/libs/`.
`./gradlew clean` removes that directory, and recompiling the same version
replaces the jar for that version. Install only one Electrical Age jar in each
game's `mods/` folder. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
