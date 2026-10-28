# 3.0.66: Reduce mixed-grid convergence spikes

The user clarified that the supplied aggregate profile was from **3.0.60**.
It spends 68.55% of sampled server time waiting for the next tick, with 7.27%
in convergence correction. It does not isolate the reported 10,200 MSPT spike.
[Profile context](verification/release-3.0.66/profile-context.json) preserves those
limits. The measurements below compare against the **3.0.65 implementation**,
not against that old capture or the user's saved world.

## Changes

Large connected converter groups can include hundreds of fixed-ratio pole
transformers. Previously each numerical Jacobian column replayed every
controller in the group. The new correction composes local controller responses:
fixed-ratio transformers use exact linear voltage sensitivities from the existing
MNA factorization; shaft, renewable and regulated controllers use branch-checked
local differences. Serial correction uses the chain rule, while simultaneous
correction keeps each participant on the same initial command vector.

Only controllers that explicitly support local differentiation participate.
They must update only their owned commands and diagnostics, and read electrical
commands only through their declared connected systems. Unknown controllers,
changed enable masks, unsupported ports, nonfinite responses or a nondeterministic
base replay fall back to the existing whole-group derivative. A local Newton
matrix that is singular or fails to improve the residual also falls back.
Groups with fewer than sixteen active commands, fewer than eight fixed-ratio
transformers, or a majority of nonlinear controllers retain the previous path.
This avoids adding composition overhead to purely nonlinear regulator banks.

Every proposed command still runs the real controller evaluator and the existing
voltage, current, power and floating-island acceptance checks. Physical history,
energy budgets, retry timers, integration intervals, device ratings and source
laws are unchanged. The derivative is a command-space acceleration, not a
replacement electrical model.

Large linear systems also reuse the previous precise solution when the complete,
reference-adjusted RHS is exactly equal and the stamped matrix is unchanged.
RHS callbacks still run; every physical step still performs its ordinary flush.
Any matrix rebuild discards that solution. Nonfinite results are not cached.
Ordinary port probes outside a convergence correction directly take the existing
precise dot-product path.

## Verification

`LocalCommandJacobianTest` compares exact transformer sensitivities and serial /
simultaneous chains against independent whole-response differences, including
bounded trial commands, nonlinear responses, voltage-regulated and power-limited
converter branches. Unsupported controllers and changed source masks must fall
back. `IdenticalRhsReuseTest` checks bit-for-bit agreement with the original solve
through changed voltage, resistance, current injection, capacitor history,
nonfinite trials and recovery; stamping and flushing must still occur each step.
The ordinary suite also covers mixed wind/generator grids, current limits,
switches, diodes, reverse power and topology changes.

The paired benchmark uses two warmed production Large Generators at 202 rad/s,
ten renewable sources, target-voltage 3.3 kV and 15.2 kV converters, and forty or
120 loaded production three-phase transformer poles. Each timed electrical step
is followed by an untimed fresh solve and every controller's physical acceptance
check. Pole secondary voltages must follow the unchanged 12.47 kV nominal ratio,
and the two implementations must agree on the high-voltage bus.

Both paths retain all 3.0.65 optimizations. The baseline disables only the new
local Jacobian, identical-RHS reuse and direct probe dispatch through constructor-
sampled diagnostic properties: `eln.disableLocalCommandJacobian`,
`eln.disableIdenticalRhsReuse`, and `eln.disableDirectPortDispatch`.

Each case uses 200 unloaded-generator steps, twenty connected settling steps,
and twenty wind-change warmup steps. Forty timed steps include eight abrupt
renewable budget changes between 1, 5, 15 and 20 kW per source. A separate steady
phase has thirty warmup and one hundred measured steps with constant wind.
Measurements alternate paths within one JVM. Abrupt wind changes deliberately
stress convergence; they are not a model of natural wind variability. Optional
`ELN_BENCH_MIXED_SAMPLES` extends the transition measurements.

## Results

The full build passes **997 tests in 241 suites**, with zero failures, errors or skips.
The paired mixed-grid benchmark also passes. [Unit totals](verification/release-3.0.66/units.json),
[summary](verification/release-3.0.66/benchmarks.json), [raw samples](verification/release-3.0.66/mixed-grid-benchmarks.json)
and [packaging checks](verification/release-3.0.66/packaging.json) are retained.

| Poles | Metric per electrical step | 3.0.65 paths (ms) | New paths (ms) |
| --- | --- | ---: | ---: |
| 40 | Transition mean | 70.670 | 49.603 |
| 40 | Transition 95th percentile | 489.061 | 205.513 |
| 40 | Worst timed transition | 994.106 | 1052.498 |
| 40 | Steady median | 0.140 | 0.125 |
| 40 | Steady mean | 0.183 | 0.165 |
| 120 | Transition mean | 702.974 | 205.809 |
| 120 | Transition 95th percentile | 5484.964 | 947.648 |
| 120 | Worst timed transition | 6146.211 | 2830.040 |
| 120 | Steady median | 0.531 | 0.440 |
| 120 | Steady mean | 0.564 | 0.530 |

The 40-pole mean and 95th percentile improve, but its worst timed sample rises
from 994 ms to 1,052 ms. Isolated steady-phase maxima also rise despite improved
means and medians. This run therefore does not show that every individual step
is faster. The final implementation retains fallback behavior; earlier
experimental timings are not the release measurements.

The data measures milliseconds **per electrical step**, not whole Minecraft
ticks. It is a synthetic production-component grid rather than a saved-world
replay. Expensive transitions remain; cold topology/matrix rebuilds, larger or
unsupported networks, world loading, other mods and JVM pauses can still cause
spikes. These measurements do not establish that the user's reported 10.2-second
spike has been eliminated. A fresh spike-focused capture on this version is
needed to assess that world.

Source-only prerelease. Build `build/libs/ElectricalAge-3.0.66.jar` and attach it
to the GitHub release; no compiled jar or media runtime is uploaded.
