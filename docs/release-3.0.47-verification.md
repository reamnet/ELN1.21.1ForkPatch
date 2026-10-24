# 3.0.47: reduce matrix rebuild work

The supplied 3.0.46 profile attributes 4.78% of sampled server-thread time to
matrix generation beneath the UPS clock's input probe, including 3.03% in LU
factorization construction. The UPS probe is often the first caller to encounter
a matrix invalidated by resistance or source-mask changes. Moving that probe
alone could shift the cost to the next electrical process.

This release reduces the shared matrix rebuild work itself. UPS control timing,
soft-start, probes, device ratings and simulation frequency are unchanged.

## Changes

Large-system LU initialization now gathers input sparsity and row scales in one
pass. Its working arrays use null for an unstored zero, avoiding the initial
full dense clone and repeated normalization/stores for zero cells. Actual
nonzero coefficients and every elimination fill entry remain explicitly tracked.
Pivot selection, row/column permutations and the order of arithmetic elimination
are unchanged. The backing addressable arrays still require quadratic memory;
this is less initialization work, not a complete sparse-storage rewrite.

The reciprocal-matrix check used by cached probes now visits every stamped
off-diagonal pair. A canonical pair is recorded even for one-sided or cancelling
stamps. Untouched pairs are both exactly zero and need no comparison. Checks
remain exact DD comparisons; dependent-source/nonreciprocal networks still use
the existing full-solve fallback.

Cable resistance and source-mask changes still rebuild the matrix. No numeric
change is rounded away or ignored, and no electrical/convergence tolerance is
relaxed. The previous affine-response and cached-divisor optimizations remain
included. `eln.disableSparseMnaRebuild=true` is a diagnostic JVM opt-out for
comparison with the previous dense initialization and full symmetry scan.

## Verification

`MnaRebuildTest` compares dense and new workspaces with exact DD solution and
inverse-response equality across pivoting, fill and coefficient-pattern changes.
It checks real component stamps, changing cable resistance and enabled masks,
identical port readings and rebuild counts, preserved committed state, and
nonreciprocal/cancelled source stamps. Existing generator/wind, UPS/standby,
transformer and floating-network regressions remain in the full suite.

The benchmark measures complete probe-triggered rebuilds on floating branching
feeders with 80, 240 or 720 voltage nodes. Both paths receive the same resistance
and source-command changes. After warmup it alternates six rounds per path,
each containing eight rebuilds. Timings include stamp construction, gauge,
factorization and the resulting port probe. The same number of rebuilds must
occur on both paths; savings come from reducing work, not skipping changes.

The full suite passed **870 tests in 213 suites**, with no failures, errors or
skips. Median timing ratios from that run:

| Voltage nodes | Rebuild speedup |
| --- | --- |
| 80 | 1.31× |
| 240 | 1.72× |
| 720 | 2.27× |

See [raw benchmark rounds](verification/release-3.0.47/benchmarks.json) and
[full-suite results](verification/release-3.0.47/units.json).
These are synthetic electrical-core measurements, not a Minecraft saved-world
replay or a predicted whole-server speedup. The next useful profile detail is
the expanded thermal-step and lamp-process cost in the updated server capture.

This source-only release builds `ElectricalAge-3.0.47.jar`; attach the compiled
jar separately. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
