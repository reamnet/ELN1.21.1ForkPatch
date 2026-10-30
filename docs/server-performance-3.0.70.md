# Server performance hotfix 3.0.70

The 3.0.69 roof-mount lookup could request a solar panel's vanilla chunk during
ELN simulation, even though ELN keeps simulating saved nodes after vanilla unloads
their chunks. Reading an old solar save without the new roof tag also queried its
support during global node restoration. Both paths could synchronously load or
generate chunks.

## Changes

Solar simulation uses the roof orientation stored on its element. Server shape
lookups find that saved element through `NodeManager`; support and sunlight probes
read resident chunks through `getChunkNow`, without requesting a chunk. Unknown
support remains unknown rather than being treated as a broken mount. Saves from
before roof mounting retain a flat mount without probing the world at startup.
Newly placed panels still detect their loaded stair support, and saved pitched
mounts retain their orientation.

The transformer profile also identified repeated RHS validation as expensive.
Large, frequently probed systems now collect nonzero source entries during the
existing finite-bound check and use those entries to validate the discarded KCL
equations. This avoids full RHS snapshots, full-sized temporary solution arrays,
repeated dense scans, and synchronized lazy access inside probe loops. The compact
cache includes currents on discarded reference rows and belongs to one matrix.
Changes to sources, resistor values, or topology still invalidate the relevant
data. Full validation remains the fallback; electrical equations, tolerances,
simulation frequency, and convergence limits are unchanged.

`-Deln.disableCompactGaugeProbes=true` selects the previous sparse validator for
diagnostic comparisons.

## Dedicated-server reproduction

An opt-in `SolarChunkPerformanceChecks` fixture runs on a fresh flat dedicated
server with no players. Each operation starts at a separate absent far-away chunk.
The baseline uses 3.0.69 production code with only the same diagnostic class added.
Both runs explicitly select the Small Solar Panel descriptor, avoiding unrelated
multiblock ghost placement. The patched run enforces zero added chunk entries and
zero chunk-load events.

| Operation | 3.0.69 added chunk entries / load events | 3.0.70 |
| --- | ---: | ---: |
| Roof lookup | 529 / 1 | 0 / 0 |
| Roof support lookup | 529 / 1 | 0 / 0 |
| Solar light simulation, 200 calls | 529 / 1 | 0 / 0 |
| Old-save read and initialization | 529 / 1 | 0 / 0 |
| Partial support queries on six sides | 0 / 0 | 0 / 0 |

The first count is `ServerChunkCache.loadedChunksCount`, which includes chunk
entries and generation dependencies. It does **not** mean 529 fully ticking
chunks. Baseline event stacks identify `SolarRoofMount.at -> getBlockEntity`,
`fromSupport -> getBlockState`, and the solar simulation/restore callers.
See the [baseline](verification/release-3.0.70/solar-chunks-before.json) and
[patched](verification/release-3.0.70/solar-chunks-after.json) reports.

The fixture is disabled unless `-Deln.solarChunkPerformanceChecks=true` is set.
It writes a report and stops its test server; use it only in an isolated test run.
Validation used Java 21 and NeoForge 21.1.249. The reported deployment uses
NeoForge 21.1.265 with a substantially larger modpack and its own world.

## Probe benchmark and correctness

The same JVM compared the old and compact sparse validators on identical
384-state, eight-port circuits with changing source commands. Each round performs
12,800 port probes. Seven rounds alternate order; the first two are discarded.
The median of the remaining five rounds was 62.944296 ms before and 35.242669 ms
after, approximately **44% less time in this workload**. This is a probe
microbenchmark, not a whole-server speedup prediction. Run it with
`-Deln.compactGaugeBenchmark=true` and
`CompactGaugeProbeTest.benchmarkRepeatedTransformerProbeValidation`.

Tests compare complete Thevenin results across changing source commands, disabled
sources, and resistor changes, verify that probes do not commit physical states,
and check unbalanced floating-island currents and independent compact/full caches.
Existing nonfinite, overflow, singular, asymmetric, and topology regressions remain
part of the MNA test suite.

Native client acceptance with Sodium, Iris, and Distant Horizons passed all four
solar panel variants on bottom slabs, pitched arrays in four stair directions,
both array terminals, connected cables and grounds on slabs/glass, 20 baked
terrain bodies, and resource reload. See the
[native report](verification/release-3.0.70/native-mounts.json) and
[screenshot](verification/release-3.0.70/slabs-and-stairs.png).

1020 core tests across 247 suites passed with zero failures, errors, or
skips. See the [full-suite report](verification/release-3.0.70/full-suite.json).

## Incident capture limits

The expanded initial server-thread sample spent 74.09% waiting for the next tick;
ELN occupied 21.39% of its sampled wall time, with converter correction at 10.38%.
That supported investigating the transformer-probe cost, but did not show a
continuously blocked server thread.

The later [all-thread capture](https://spark.lucko.me/246EuUDWaH) recorded 1,200
ticks over 60.402 seconds, approximately 20 TPS and no players. Its process CPU
average was 3.4549% across 12 available processors, approximately 0.415 CPU cores.
The main Java server stack spent 48.576 of 59.900 sampled seconds waiting for the
next tick. Background workers were mostly waiting too. The grouped `Server
thread` node also contains native Sable worker samples; its combined time cannot
be interpreted as one Java thread's CPU use. Per-thread `100%` headings are
normalizations, not CPU utilization.

This capture does not reproduce the reported saturated-core stall. The fixes
address a separately reproduced regression and a measured hotspot; a capture
during any remaining stall is needed to establish its cause.
