# 3.0.64: Fused single-phase primary riser

The existing **Single Phase Underground Feeder Pole** now has a physical primary cutout.
Its overhead top terminal stays on the line side. The left-base and bottom-Up buried
primary terminals are on the protected side. Opening, removing or blowing the fuse
isolates the buried primary in either direction; the separate 120/240 V secondary and
its removable streetlight remain independent.

The cutout uses the existing replaceable fuse cartridges, drop-open visual and operating
sounds. Its buried-riser jumper starts at the lower fuse hinge, so there is no direct
jumper around the protection. Static geometry remains in the baked terrain/distant-model
path. A geometry comparison found all 73 existing model parts unchanged and only the
new `RiserFuseJumper` part added.

Old unfused risers migrate to a closed included generic link without changing their IDs
or cable endpoints. A rated cartridge retains its own rating across saves and pickup.
New crafting uses the Single Phase Fused Cutout Pole, Copper Cable and Iron Plate.
The hidden legacy single-phase riser with a built-in lamp receives the same protection.
After closing the cutout, protection waits for the first solved current sample; it does
not interpret the previous open-contact voltage difference as an instantaneous fault.

See the [installation and fuse controls](american-underground-service.md#fused-primary-riser-3064).

## Verification

The complete ordinary build passes: **987 tests in 237 suites**, zero failures, errors
or skips. No test exclusions were added. [Full unit totals](verification/release-3.0.64/units.json).
Language and recipe generation also completed successfully before the final build.

Unit tests exercise both current and legacy risers: actual MNA load current, manual
opening/reclosing, overload with a physical rated cartridge, reverse-feed isolation,
independent secondary lighting, old-save migration, saved/picked-up fuse state,
single extraction and the generic-link rating control. Existing underground-terminal
tests now expect the protected output at both buried primary ports.

The opt-in `FusedRiserChecks` dedicated-server fixture places real cables, an overhead
span and a grounded resistive load. It checks the buried voltage and current through
closed, open, reclosed and overload states while a separately supplied secondary keeps
the streetlight running. It is enabled only with `-Deln.fusedRiserChecks=true`, with
`-Deln.fusedRiserReport=/absolute/path/report.json` selecting the report. Ordinary worlds
are unaffected by this developer fixture.

The [dedicated-server check](verification/release-3.0.64/native.json) passed with a
12.47 kV overhead feed and a real 2 A buried load. Opening or blowing the cutout
produced 0 V / 0 A underground while the overhead line stayed at 12.47 kV and
the separately supplied streetlight stayed at 240 V / 70 W. Reclosing restored
about 12,469.98 V / 2 A without a false trip. Installing a physical 1 A cartridge
then correctly blew under the 2 A load.

[Package and unchanged-model checks](verification/release-3.0.64/packaging.json) verify
the version, new model part, generated language key and crafting recipe in the jar.
No media runtime or Chromium archives are included.

Source-only prerelease. Compile with a full JDK 21 and attach
`build/libs/ElectricalAge-3.0.64.jar`. Update clients and server together.
[Ubuntu build instructions](building-on-ubuntu-24.04.md).
