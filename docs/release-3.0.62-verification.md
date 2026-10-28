# 3.0.62: Fix the full build after adding the wide meter socket

The five reported `GridCableCompatibilityTest` failures in 3.0.61 all stopped at
the shared registered-device assertion. Its explicit expected-device list omitted
the new **Wide Underground Meter Socket**. The registry itself contained the device
correctly. The focused 3.0.61 service tests did not include this separate grid suite.

The expected list now includes the wide socket. The test still compares the entire
registered grid-device set, including hidden legacy devices, rather than dropping
the assertion or excluding the new equipment. Cable ratings, insulation and gameplay
behavior are unchanged.

The direct-API test now uses known overhead-capable poles and verifies a valid
power-cable link first. It no longer depends on which device happens to be first or
last in registry iteration. This prevents a cable-only endpoint from rejecting all
negative cases before cable/span validation is reached. A separate check rejects
direct overhead links to the new wide socket and underground riser in both link
directions and all horizontal device orientations.

## Validation

The reported failures were reproduced on the released source: **9 tests, 5 failures**.
After the correction, the complete ordinary `build` task passes **978 tests across
234 suites**, with zero failures, errors or skips. All **10** grid cable compatibility
tests pass. No test filter or new exclusion is used:

```bash
./gradlew --no-daemon --max-workers=2 build
```

See [full suite results](verification/release-3.0.62/units.json) and
[build/package audit](verification/release-3.0.62/build.json).
The normal build's existing benchmark/profiling exclusions remain unchanged.
This release changes tests and version metadata only; the
[3.0.61 in-game service verification](release-3.0.61-verification.md) still documents
the unchanged wiring and rendered equipment. No new native FPS or simulation
performance claim is made.

Source-only prerelease. With a full Java 21 JDK, build normally and attach
`build/libs/ElectricalAge-3.0.62.jar`. Update clients and server together.
[Ubuntu instructions](building-on-ubuntu-24.04.md).
