# 3.0.48: reduce factorization scans and matrix copies

The follow-up profile reports 12.88% sampled server-thread time for ELN and
12.43% in `Simulator.tick`, with 2.70% in matrix generation beneath the UPS
input probe. LU construction accounts for 1.89%. These are sampling shares from
separate captures, not controlled before/after elapsed-time measurements.

## Changes

The sparse LU factorizer already records nonzero columns per row. It now also
maintains nonzero rows per column, including elimination fill and cancellation.
Warm pivot searches and elimination visit those rows in ascending order rather
than checking every remaining row. Row/column exchanges update both patterns.

Voltage-reference selection now reads the physical matrix without changing it
and saves only the discarded KCL rows needed for validation. The precise solve
matrix shares unchanged rows read-only and replaces only reference rows. This
avoids two complete double-matrix copies and a full precise-matrix row clone.
Each rebuild still stamps fresh physical arrays, so a retained small-system
inverse-update snapshot cannot be overwritten by the next generation.

Pivot selection, tie ordering, arithmetic order, precision and singular-matrix
rejection remain the same. Source control, UPS measurements, cable resistance
updates, matrix invalidation, thermal simulation and convergence tolerances are
unchanged. This reduces work per rebuild; it does not skip rebuilds or electrical
time steps. Cold Markowitz ordering remains in use for new variable identities.

The original scanning implementation remains available with the diagnostic JVM
flag `eln.disableMnaColumnWorksets=true`. The original full-copy gauge setup
remains available with `eln.disableMnaGaugeCopyReduction=true`. These are
independent of the preceding implicit-zero initialization, affine-probe and
cached-divisor optimizations.
Addressable LU arrays remain quadratic; this is not a full sparse-storage rewrite.

## Verification

`MnaColumnWorksetTest` compares exact DD solutions and inverse rows against the
scanning implementation across sparse and dense nonsymmetric inputs, changed
coefficient patterns, row/column pivoting, reused permutations and exact
elimination cancellation. It checks singular matrices with both cold and reused
orderings, and real feeder probes across resistance/source-mask changes with
identical rebuild counts and unchanged committed state. Additional gauge tests
verify multiple floating islands, rejected unbalanced currents, grounded
networks, unchanged physical diagnostic stamps and read-only sharing of
non-reference precision rows. A nonsymmetric regression verifies that read-only
selection honors earlier reference choices exactly as the in-place algorithm
would, even if a discarded row originally constrained another group.

The benchmark alternates six warmed rounds for each implementation on floating
branching feeders with 80, 240 or 720 voltage nodes. Each round contains eight
complete probe-triggered matrix rebuilds with changed cable resistance and source
commands. The baseline disables both new changes; both paths retain the 3.0.47
initialization improvements and perform the same number of factorizations.
Timings include stamping, gauge construction, factorization and the resulting Thevenin probe.

The full suite passed **878 tests in 214 suites**, with no failures, errors or
skips. Median complete-rebuild timing ratios from that run:

| Voltage nodes | Baseline/new median time |
| --- | --- |
| 80 | 1.18× |
| 240 | 1.29× |
| 720 | 1.77× |

A ratio above 1 indicates faster rebuilds. Individual rounds are noisy; all
rounds are retained in [raw timings](verification/release-3.0.48/benchmarks.json).
See also [full-suite results](verification/release-3.0.48/units.json).

This is a synthetic electrical-core comparison, not a Minecraft saved-world
replay or an estimate of whole-server speedup. Physical matrix allocation,
gauge connectivity scans, cold pivot ordering and other thermal/lighting work
remain costs outside the optimized operations.

This source-only release builds `ElectricalAge-3.0.48.jar`; attach the compiled
jar separately. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
