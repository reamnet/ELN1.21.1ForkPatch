# Sunlit solar-array probe performance, 3.0.72

The reported setup is 12 series by eight parallel large 2×3 tracking panel blocks
(96 blocks), open circuit, with approximately 17 TPS while sunlit. The supplied
expanded server-thread profile puts 33.94% of sampled wall time in
`SolarPanelPowerProcess.rootSystemPreStepProcess`, almost all of it in
`SubSystem.solveChecked`. Slow solar light/tracker sampling is only 0.15%.
This identifies the electrical voltage probes as the relevant cost.

Every panel previously called `solveChecked(positive)` and `solveChecked(negative)`
separately before choosing its piecewise Norton equivalent. Each call assembled
the full subsystem RHS and projected one absolute terminal voltage. With 96
panels, 50 legacy sweeps, and multiple electrical steps per server tick, this
repeated source sampling and double-double dot products heavily. Sunlight makes
the current-source terms nonzero, explaining the increased day-time cost.

## Changes

`solveVoltageDifferenceChecked` samples the sources once and projects the
terminal-to-terminal voltage directly. Difference responses belong to one stamped
matrix and are discarded on rebuild. Double-double subtraction happens before
conversion to double, preserving small differences on large common-mode voltages.
Floating-island KCL validation still runs. A conservative bound on both absolute
terminal sums retains the previous checked path when intermediate overflow is
possible, even if a finite difference might otherwise hide it.

For subsystems containing only exact `CurrentSource`, `VoltageSource`, and
`SwitchableVoltageSource` callback classes, assembled source values can be reused
until a command changes. Their setters invalidate the snapshot on a changed
double bit pattern; current-source NBT restoration uses that setter too. Matrix
and callback changes invalidate it. Capacitors, inductors, delays, source
subclasses, and custom callbacks use fresh sampling, since they can depend on
physical state or other data. The snapshot is separate from the subsystem's
working RHS, so other probes and physical solves cannot overwrite it.

The existing fixed-anchor projection helper periodically rebases in DD. Probes
never commit terminal state or advance capacitor/inductor history. Dark panels
apply their existing zero-current/high-impedance model without probing a voltage:
there is no I–V segment to select in that case.

The I–V curve, ratings, tracking geometry, sunlight sampling, electrical period,
legacy sweep count, convergence criteria, IDs, and save formats are unchanged.
`-Deln.disableDifferentialVoltageProbes=true` selects the original pair of
`solveChecked` calls for diagnostic comparisons.

## Reproduction and measurements

`SolarArrayPerformanceTest` creates 96 separate production
`SolarPanelPowerProcess` instances with explicit positive/negative terminal
loads, wire junctions, and `ElectricalConnection` resistance. It includes the
per-panel grounding pull-downs and normal subsystem partitioning. It does not
replace the array with one scaled panel. Large-panel parameters are Voc 111 V,
Vmp 90 V, Imp 16 A, and Isc 17.2 A. Tracking and fixed large panels share these
electrical parameters; the full-sun benchmark uses light factor 1.

The same JVM alternates original and optimized probe modes on identical arrays.
Each mode warms for 20 electrical steps, then runs seven rounds of 12 steps;
the first two rounds are discarded. The retained five-round medians were:

| Open-circuit, full-sun 12×8 large array | Milliseconds per electrical step |
| --- | ---: |
| Previous two-terminal probe path | 10.3938541667 |
| Direct difference and tracked-command reuse | 6.8694771667 |

This is approximately 33.9% less simulation time for this isolated array workload,
not a prediction of total server TPS. Both modes retain 50 legacy sweeps and the
0.01-second electrical period. The optimized open-circuit result was
1,331.999963 V, consistent with the expected 12 × 111 V. The run recorded 499,200
differential probes and 446,969 source-RHS snapshot reuses. See the
[benchmark report](verification/release-3.0.72/solar-array-benchmark.json).

Enable the optional benchmark with `-Deln.benchSolarArray=true` and run
`SolarArrayPerformanceTest.benchmarkOpenSunlitLargeArrayInAlternatingOrder`.
The comparison's original mode uses the old checked-probe path; shared source
setter tracking remains installed in both modes.

## Correctness checks

The 34-test focused suite passed both small and large 12×8 arrays through open
circuit, matched-load connection, reduced light, darkness, sunrise, and physical
load disconnection. It compares every panel terminal and bus voltage against the
previous probe path. It checks that dark panels stop issuing electrical probes.
Existing solar and MPPT tests also pass.

Additional probe tests cover changing voltage/current commands, resistance and
source-mask rebuilds, unbalanced floating reference currents, custom callbacks
whose output changes without a command setter, large common-mode voltages,
overflow fallback, and probes leaving physical state untouched.

See the [focused-suite report](verification/release-3.0.72/targeted-tests.json).

Dedicated-server passive solar lookup, sunlight sampling, and old-save restoration
checks retained zero added chunks/load events. The native client, with Sodium,
Iris, and Distant Horizons, passed all four panel variants on slabs, pitched arrays
in four stair directions, both terminals, 20 baked bodies, and resource reload.
See the [passive-solar report](verification/release-3.0.72/native-passive-solar.json)
and [mounting report](verification/release-3.0.72/native-mounts.json).

The full Gradle build passed with 1031 tests across 250 suites and
zero failures, errors, or skips. See the [full-suite report](verification/release-3.0.72/full-suite.json).
