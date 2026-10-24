# 3.0.45: server-side probe performance

The supplied 3.0.44 server profile attributes 48.14% of sampled server-thread time
to `Simulator.tick`, including 29.11% in `ConverterConvergence.correct`. Most of
that correction cost is repeated transformer/UPS port probes, particularly
floating-network validation calling a full double-double LU solve after each
voltage-command change. The profile identifies a cost distribution; it does
not provide a before/after tick-duration measurement.

## Change

For a reciprocal physical stamp matrix, a change to a few RHS entries can be
solved by adding the corresponding cached inverse responses to the preceding
precise solution. Port probes already cache these response rows. The new path
reuses them instead of repeating forward/back substitution and DD division.

The voltage gauge replaces reference equations, making the *gauged* matrix
asymmetric. Reusing its rows as columns indiscriminately would be wrong. The
optimization checks reciprocity on the original precise physical stamp and
sets the response at every fixed-zero reference variable to zero. Responses on
the remaining variables then have the required reciprocity.

Every physical RHS still goes through the original discarded-KCL validator,
including checks on every floating island and nonfinite solutions. No KCL,
power-balance, current, voltage, energy or converter convergence threshold is
relaxed. Probes never advance physical time or commit voltages/currents, heat,
batteries or shaft energy. The accepted physical step still uses the existing
full solver.

Any matrix regeneration clears the probe solution: cable resistance, topology,
source enable masks, contacts and changed state identities cannot reuse old
responses. More than four changed RHS entries, nonreciprocal matrices or a
nonfinite cached solution use the original full solve. A full solve also
rebases the cache after at most sixteen incremental updates to bound numerical
roundoff accumulation. No extra full inverse is constructed for large systems.

The existing transformer, generator and UPS processes benefit through their
shared port-probe implementation. Device ratings, control modes and simulation
frequency are unchanged. `eln.disableAffineProbeUpdates=true` is a diagnostic
JVM opt-out, sampled when each subsystem is constructed, for baseline comparison.

## Verification

`AffineProbeTest` compares optimized and original full-solve results on both
small inverse-based and larger factorized circuits. It varies source commands,
checks that probes leave committed states untouched, changes resistor values
and source masks, and verifies full-solve fallback for a nonreciprocal controlled
source. Deliberately unbalanced currents in discarded gauge equations must
still be rejected, even if opposite faults on different islands cancel globally.

The probe benchmark warms both paths, alternates their execution order for four
rounds, and measures 4,000 probes per round on floating feeders with 80 or 240
voltage nodes and eight active ports. It records raw elapsed times and full/
incremental solve counts; correctness assertions do not depend on timing ratios.
These are synthetic electrical-core measurements on the same cloud machine,
not measurements of the user's Minecraft world or a promised TPS/FPS gain.

Results and raw timings are saved under
[verification/release-3.0.45](verification/release-3.0.45/units.json).
The full suite includes the mixed wind bank / Large Generator / 3.3-to-15.2 kV
regressions, joined 120-pole grids, converter overload and reverse-power checks,
UPS/standby switching, breaker panels and floating-network tests.

This is a source-only release. Build `ElectricalAge-3.0.45.jar` using the
[Ubuntu instructions](building-on-ubuntu-24.04.md), then attach the jar separately.
A fresh server profile with the same loaded grid will establish the actual
improvement and identify any remaining dominant work.

## Measured results

All 863 tests passed across 211 suites, with no failures, errors or skipped tests.

| Voltage nodes / ports | Original median, 4000 probes | Optimized median | Speedup |
| --- | --- | --- | --- |
| 80 / 8 | 110.73 ms | 25.79 ms | 4.29x |
| 240 / 8 | 153.99 ms | 47.57 ms | 3.24x |

Each optimized run used 989 full validation solves and 15,811 affine updates
across warmup plus measured rounds, about 94.1% fewer full validation solves.
[Raw timings](verification/release-3.0.45/benchmarks.json) include every round.
