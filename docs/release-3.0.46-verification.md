# 3.0.46: cheaper full MNA solves

Builds on 3.0.45's cached port-probe responses. Full matrix solves still run for
accepted physical steps, probe rebasing, unsupported affine probes and larger
RHS changes. Large systems use double-double LU solves; repeating division by
fixed row scales and pivots costs time and temporary arithmetic objects.

The factorization now caches those divisors' double-double reciprocals and uses
them for repeated RHS solves and transpose/inverse-row responses. Matrix stamps,
normalization during factorization, pivot ordering, LU elimination and the
physical simulation frequency are unchanged. Small inverse-based systems are
unchanged. This neither removes physical steps nor reduces solver precision to
ordinary doubles.

Reciprocal multiplication can lose double-double precision at extreme exponents
because its low component underflows. Divisors outside 1e-200 through 1e200 in
absolute value retain the original direct division. An unusable reciprocal or
a nonfinite multiplication result also uses direct division. No electrical,
energy-balance or convergence acceptance tolerance is relaxed.

`eln.disableCachedMnaDivisors=true` is a diagnostic JVM opt-out, sampled when a
factorization is created. The disabled path uses the original DD-by-double
scaling and DD-by-DD pivot division, allowing a fair baseline comparison. This
is independent of the 3.0.45 affine-probe optimization, which remains enabled by
default.

## Verification

`MnaDivisorTest` checks optimized and original solutions on pivoted matrices,
independent precise equation residuals and transpose responses, reused symbolic
ordering after coefficient changes, and extreme divisors from 1e-300 to 1e300.
The existing mixed-generator/wind, UPS, transformer, floating-island and affine
probe regressions remain in the full suite.

The benchmark warms both paths and alternates four baseline/optimized rounds,
each containing 2,000 complete solves on branching feeders with 80 or 240 voltage
nodes. It excludes factorization setup and records every measured round. It
measures an additional full-solve optimization against the 3.0.45 arithmetic
path; its ratios must not be multiplied by the earlier port-probe benchmark or
treated as a predicted whole-server speedup.

Raw timings and full-suite results are recorded in
[the verification directory](verification/release-3.0.46/units.json).
These are electrical-core tests on the cloud machine, not a replay of the user's
Minecraft world. A fresh profile from 3.0.45 or later is needed to establish the
current dominant server costs and measure the real improvement.

This is a source-only release. Build `ElectricalAge-3.0.46.jar` using the
[Ubuntu instructions](building-on-ubuntu-24.04.md) and attach the compiled jar
separately.

## Measured results

All 866 tests passed across 212 suites, with no failures, errors or skipped tests.

| Voltage nodes | Original median, 2000 solves | Optimized median | Speedup |
| --- | --- | --- | --- |
| 80 | 32.79 ms | 16.95 ms | 1.93x |
| 240 | 52.08 ms | 23.25 ms | 2.24x |

[Raw benchmark rounds](verification/release-3.0.46/benchmarks.json) record all samples.
