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
| `v3.0.19` | — | Remove invisible movement barriers around decorative fixtures and long LED strips; automatically upgrade existing lamp clearance cells |
| `v3.0.20` | — | Wall-mounted ATS, MPPT and hybrid solar inverter variants with shallow cabinets, wall cables and survival recipes; stable UPS charging on standby generators |
| `v3.0.21` | — | Quick, audible wireless switches; adjustable fixture heads; miniature dots, twin eave light and ceiling fans; wall UPS and manual bypass; kettle, blender and rice cooker; thermostat heaters, fireplace TV stands and window/wall AC |
| `v3.0.22` | — | Working countertop mini oven; energy-driven microwave battery hiss and explosion; operating sounds for all kitchen appliances, heaters, ceiling fans and both AC units |
| `v3.0.23` | — | Fan spin-up/coast-down; five-block industrial fan with blade damage; three box fans; flexible cord and wired wall-outlet prototype with portable appliance pickup |
| `v3.0.24` | — | Cords for all lamps and appliances; visible NEMA 5-20/6-20 plugs; 240 V outlets; wireless Lamp Supply outlet mode; five-block industrial ceiling fan with 5 damage/s blade contact |
| `v3.0.25` | — | Independent utility phases and 120/240 V service drops; 10–100 kW pole transformers and fuses; reclosers, feeder/secondary poles, regulators and capacitor banks; analog/smart meters, twelve-circuit wireless breaker panel; taller poles and an Aura-style seven-light fan |
| `v3.0.26` | — | Fixed 15.2 kV nominal pole transformer ratios; proportional secondary sag/rise with physical fuse protection; narrower inward-curved Aura blades with bulbous tips and downward/leftward pitch |
| `v3.0.27` | — | 12.47 kV pole nominal; aim-based HV/LV links and automatic independent three-phase spans; weatherhead-only overhead house entry with placed meter/panel feeders; slow-blow panel breakers; world-lit wire covers |
| `v3.0.28` | — | Per-fixture wireless load accounting; physical transformer tiers and fuse cartridges; duplex passive outlets, horizontal mounting and six-socket power strip; secondary-fed HV/LV streetlight poles; red-handle service disconnect |
| `v3.0.29` | — | Reuse identical transformer gauge validations and per-matrix open-port support; track sparse pivot/elimination fill and skip exact-zero arithmetic; preserve all voltage/current/fault acceptance checks |
| `v3.0.30` | — | Independent pole corner alignment and fused phase taps; visible long spans; correct machine shading and wired bulb brightness; leaner model rendering; pool/party/parking fixtures and miniature/globe sagging light strings; streetlight aiming; smaller lamp targets; reliable portable cords and recessed outlets; holiday lighting controls and per-circuit dusk switching; machine/industrial cords, external disconnect handle and breaker cable clipping; mini fridge, five electrical deck items and bounded span thermal updates |
| `v3.0.31` | — | Keep the Large Generator powered when switches, relays and diodes introduce tiny leakage loads; preserve droop, current limits and shaft-energy limits |

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
use an explicit override such as `-PmodVersion=3.0.31-local.1`.

Build with `./gradlew build` to retain previous versioned jars in `build/libs/`.
`./gradlew clean` removes that directory, and recompiling the same version
replaces the jar for that version. Install only one Electrical Age jar in each
game's `mods/` folder. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
