# Server performance audit: 3.0.77

This audit starts from 3.0.76 and exercises the electrical work identified in the
earlier Spark captures: panel voltage probes, converter port measurements,
floating-network validation and mixed transformer grids. The main reproduction
has 96 large tracking-panel electrical models, wired as 12 series by 8 parallel,
with explicit panel terminals and cable connections. Other scenes exercise
external LiFePO4 banks and a 40-pole grid with changing renewable generation.

## Findings and implementation

* Sunlit panels repeatedly project precise terminal voltages while the legacy
  interconnection solver performs its configured sweeps. Legacy port measurements
  temporarily change a source command by 5 V and restore it, invalidating the
  source snapshot and repeating work on the feeder.
* For linear networks whose RHS callbacks are exactly the tracked current and
  voltage source classes, `getTh` can obtain the same external port response
  directly from the stamped matrix. Its final source command remains compatible
  with legacy callers. Partition links have a separate read operation that leaves
  the command intact until both sides have been measured and their new commands
  are installed.
* Repeated probes reuse source snapshots, floating-KCL validation and scalar port
  or terminal-difference results until a source command or matrix changes.
  Capacitors, inductors, Delay and custom/subclass callbacks are excluded because
  their RHS can change without a tracked command setter. Returned mutable
  Thevenin objects never expose cache storage.
* A checked single-terminal read on a floating network validates the complete
  network, then projects the requested row. It avoids another complete solution
  for each requested terminal. Sparse validation still requires the existing
  finite bound for every solution variable and intermediate.
* Identical reads of a precise projection's anchor no longer consume its refresh
  budget. The refresh limit still applies to actual delta calculations.
* Removed an unused per-step `Profiler`; matrix/topology debug profiling and the
  once-per-second simulator log now avoid constructing data when their existing
  debug logging setting is disabled. Runtime metrics remain available.

The implementation retains double-double arithmetic, floating-island KCL checks,
matrix invalidation, physical timestep and energy accounting. These are algebraic
probe optimizations; probes do not advance stored charge, temperature or time.

## Reproducing measurements

Use a full JDK 21. The opt-in workload test records elapsed time, server-thread CPU
time and allocated bytes per electrical step. The paired mode alternates original
and optimized probe paths within the same JVM. It warms both paths, records seven
batches and reports the median of the final five batches. The original path is
selected per subsystem using `-Deln.disableProbeDataReuse=true`; it retains the
small logging/projection cleanup shared by both paths.

```sh
ELN_AUDIT_PAIRED=1 ./gradlew test \
  --tests mods.eln.sim.power.ServerPerformanceAuditTest --console=plain
```

For a single-path recording, use `ELN_PERFORMANCE_AUDIT=1`, optionally with
`ELN_AUDIT_DISABLE_PROBE_REUSE=1` and `ELN_AUDIT_LABEL=baseline`. JSON and JFR
outputs go under `build/reports/server-audit-*`. The JFR explicitly enables only
execution samples, allocation samples and garbage collection events.

The paired fixtures validate finite solar voltages, inverter output and battery
energy/current bounds outside timed batches. Separate regressions compare every
panel terminal through load, shade, darkness, sunrise and load removal, and
compare cached probes with fresh solves across source changes, topology changes,
contact masks, nonreciprocal matrices, faults, NaNs and externally changed callbacks.

## Measured results

[Raw paired batches](verification/release-3.0.77/paired-workloads.json) retain wall
time, current-thread CPU time, allocation, batch sizes and cache counters.
[Summary](verification/release-3.0.77/workload-summary.json) includes CPU and
allocation reductions. Times below are milliseconds per electrical step;
allocation is KiB per step (1 KiB = 1,024 bytes).

| Workload | Original path ms | New path ms | Original KiB | New KiB |
| --- | ---: | ---: | ---: | ---: |
| 96 large solar panels, open | 7.84459 | 7.63135 | 11743.54 | 9743.54 |
| 96 large solar panels, loaded | 6.56115 | 5.09319 | 9577.02 | 7177.02 |
| 96 large solar panels, night | 1.35986 | 0.10932 | 299.93 | 104.33 |
| One 64 kWh bank, grid charging | 0.00369 | 0.00268 | 3.53 | 3.47 |
| Eight 64 kWh banks, 5 kW off-grid load | 0.01267 | 0.01127 | 15.88 | 15.57 |
| Thirty-two 64 kWh banks, 5 kW off-grid load | 0.05224 | 0.03963 | 59.83 | 58.77 |
| 40-pole mixed grid, changing wind | 30.71730 | 29.00008 | 31453.73 | 31420.68 |

Loaded solar takes about 22% less wall/CPU time and allocates about 25% less in
this run. Open solar allocates about 17% less, but its approximately 3% median
timing gain is small relative to batch variability. It remains expensive. The
night workload can reuse stable linear results and shows a much larger saving.
Battery scenes are small absolute costs; the 32-bank scene saves about 21% CPU
time but only about 0.011 ms per electrical step. The mixed-grid result is modest,
and preliminary runs varied in direction: there is no demonstrated cure for its
convergence spikes.

The solar fixture uses a 0.01-second electrical step with 50 legacy sweeps; the
battery fixture uses a 0.05-second step. These numbers must not be interpreted as
whole-server MSPT or multiplied into an expected TPS improvement.

## Packaged Minecraft checks

`ELN_AUDIT_PAIRED=1 ./gradlew build --console=plain` passes **1,085 tests in 259
suites**, with zero failures, errors or skips. [Unit totals](verification/release-3.0.77/units.json)
include the paired workload run and the production solar, converter, floating-gauge,
thermal, battery and utility regressions.

The 3.0.77 jar passes the isolated dedicated-server
[solar chunk campaign](verification/release-3.0.77/native-solar-chunks.json): roof
lookup, roof support, repeated solar sampling, legacy panel restoration and partial
support queries all load zero missing chunks and fire zero chunk-load events.

The packaged client/integrated server also passes all seven
[off-grid campaign checks](verification/release-3.0.77/native-offgrid.json), including
real wired grid charging, PV charging, transfer to battery backup, reverse-flow
prevention and a server-side BMS control packet. This checks production elements
and wiring in Minecraft in addition to the isolated electrical fixtures.
[Packaging metadata and hash](verification/release-3.0.77/packaging.json) identify
the locally tested jar. The public prerelease contains source only.

## Practical limits

These are warmed component workloads, measured per electrical step, rather than
a saved-world replay or a prediction of Minecraft TPS. Real solar light/tracking,
world loading, other mods, network traffic and JVM pauses add work. The mixed-grid
scene deliberately changes wind budgets every five steps and stresses convergence.
Cold topology/matrix rebuilds and difficult converter transitions remain expensive.
The audit therefore does not establish that every reported server stall is fixed.

The optional EA Media addon is unchanged. Media decoding/browser playback runs on
clients and was outside the measured electrical workloads.

Build the source-only prerelease with a full JDK 21 using `./gradlew build`.
The local result is `build/libs/ElectricalAge-3.0.77.jar`; retain earlier numbered
jars and install only one Electrical Age jar in a server's mods directory.
