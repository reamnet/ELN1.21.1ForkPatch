# 3.0.65: Reduce converter and lamp work per tick

The supplied [server profile](verification/release-3.0.65/profile.json) places 22.60%
of sampled server-thread time in ELN, including 8.23% in convergence correction,
5.16% in fixed-ratio/target transformer updates, and 2.47% in decorative lamp
lighting. These are inclusive shares and overlap. The same capture spends 65.53%
waiting for the next tick; it has average headroom and does not establish how
large occasional tick spikes are.

## Changes

During a convergence correction, large-system Thevenin probes reuse a compact,
read-only inverse-row projection. When at most four contributing RHS entries
change, their double-double deltas
are applied to a fixed full-dot anchor. After sixteen shortcuts the projection rebases;
larger changes or nonfinite trials fall back to a full projection. Deltas do not
accumulate from one speculative probe into the next. Resistance, contact-mask
and topology changes discard the projection with the old matrix. Small systems
keep their original direct projection. Ordinary probes also retain that path;
the temporary projection rows are released on leaving a correction, including
early returns and exceptions. Physical RHS assembly and floating-island
validation still run for every probe.

Simultaneous convergence trials restore only the previous participant's owned
sources before evaluating the next participant. The previous implementation
rewrote every command in the connected grid for each participant, producing
quadratic restoration work. All participants still receive the same trial
commands and masks; all controller evaluations, branch checks, limits and
acceptance tests remain. Connected-system lists and branch comparisons also
avoid rebuilding collections inside repeated trial evaluations.

Decorative fixtures query their mounting inset once per update and reuse beam
directions shared by multiple bulbs. Overlapping bulb rays share chunk and
solid-block queries for that update only. Nothing is retained between ticks:
new walls, aiming changes and newly loaded chunks are observed on the next
ordinary update. Light refresh rate, bulb brightness and power draw are unchanged.
No block models, IDs, recipes, packet formats, solver iteration limits or
physical integration intervals change.

## Verification

`AffinePortProjectionTest` compares complete probes against the original direct
projection on small/large reciprocal and nonreciprocal networks, changed source
commands, resistance/mask changes, rejected floating currents, nonfinite inputs
and recovery. A stiff-cancellation test compares double-double projections over
1,000 changed inputs without committing state. Lighting tests check overlapping
rays, unloaded-chunk exclusion and changed obstructions on the next update.

The mixed-grid benchmark starts with two physically warmed, unloaded production
Large Generators before attaching ten renewable
sources, target-voltage 3.3/15.2 kV converters and loaded three-phase transformer
poles. Running-voltage initial guesses still require the full physical acceptance
checks; they avoid mixing the initial field ramp into steady-load measurements.
Paired implementations alternate in the same JVM. Each measured root step
is followed by an untimed re-solve of the committed commands to verify the same
physical acceptance checks; trial values are intentionally unavailable after a
normal physical flush. Timings are milliseconds per electrical step, not a whole
Minecraft tick or a saved-world replay.

Each case uses 200 unloaded-generator steps, 20 connected settling steps and
20 wind-change warmup steps. Forty timed steps include eight abrupt budget
changes between 1, 5, 15 and 20 kW per renewable source. This deliberately stresses
convergence transitions; it is not a claim that real wind changes that quickly.
Set `ELN_BENCH_MIXED_SAMPLES` for a longer timed run. Cold boot, chunk attachment
and large matrix rebuilds remain separate costs and can still stall a tick.

Diagnostic JVM flags `eln.disableAffinePortProjections=true` and
`eln.disableLocalConverterRestores=true` select the previous projection/restoration
paths. Both are sampled when their solver objects are constructed. Paired runs
retain all preceding MNA optimizations and the small collection cleanup; they do
not measure the separate lighting change. The standalone probe comparison
explicitly enters a correction-style projection scope.

## Measured results

The final ordinary build passes **991 tests in 239 suites**, with zero failures,
errors or skips and no added test exclusions. Both paired benchmark tests also
pass. [Unit totals](verification/release-3.0.65/units.json),
[benchmark output](verification/release-3.0.65/benchmarks.json) and
[all mixed-grid samples](verification/release-3.0.65/mixed-grid-benchmarks.json)
are retained. The [jar checks](verification/release-3.0.65/packaging.json) confirm
the version and scoped implementation, unchanged assets/configuration, and no
bundled media runtime.

| Poles | Electrical-step metric | Diagnostic baseline (ms) | Reuse enabled (ms) |
| --- | --- | ---: | ---: |
| 40 | Median | 0.164 | 0.185 |
| 40 | Mean including transitions | 100.452 | 83.880 |
| 40 | 95th percentile | 711.163 | 534.785 |
| 120 | Median | 0.816 | 0.898 |
| 120 | Mean including transitions | 1489.747 | 744.063 |
| 120 | 95th percentile | 9365.773 | 4514.708 |

The mean reductions are about **17% and 50%** for the 40- and 120-pole stress
cases. The 120-pole 95th percentile improves about **52%**. Small steady steps
remain below 1 ms here, but their medians are slightly slower in this capture.
The separate probe microbenchmark is mixed/noisy too; this is not a claim that
every probe or circuit becomes faster.

Abrupt transitions still have severe outliers: the 120-pole maximum measured
**18.057 seconds before and 9.385 seconds after**. This patch reduces their cost;
it does not eliminate expensive nonlinear correction or matrix rebuilds. The
stress fixture deliberately changes renewable budgets abruptly every five
electrical steps. These timings do not predict real-world tick time or establish
that all reported lag is resolved. Compare a new server profile under the same
world/load after updating.

Run the comparisons with a full JDK 21:

```bash
./gradlew --no-daemon --max-workers=2 benchmarkTest \
  --tests '*PortProjectionBenchmarkTest' --tests '*ServerTickHotspotBenchmarkTest'
```

Source-only prerelease. Compile `build/libs/ElectricalAge-3.0.65.jar` and attach it
separately. [Ubuntu build instructions](building-on-ubuntu-24.04.md).
