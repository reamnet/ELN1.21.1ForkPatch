# Shaft motor and flywheel bank: startup and solver performance

The [reported Spark capture](https://spark.lucko.me/Q4JyWpp7fc) runs Electrical Age
3.0.77. It samples about 60 seconds of a server with two Large Shaft Motors driving
a bank of Large Flywheels. The motors are supplied by Large Generators
through 3.2 kV -> 12.5 kV -> 69 kV -> 3.2 kV transformer stages.

## Findings

The electrical root solver accounts for 82.7% of sampled server-thread time.
Matrix generation accounts for 39.0%, and converter boundary restarts account for
37.5%. These are overlapping call-tree percentages, not additive costs. Flywheel
entity checks account for 0.13%.

The reproduction uses four energized Large Generator models, both motors and all
28 flywheel masses on a shared shaft, with the reported voltage cascade. It checks
both fixed-ratio and target-voltage transformer controls. With the original motor
initialization, the target-voltage case fails on its first physical step: all three
transformers enter numerical recovery after 400 motor-controller evaluations.

The motor initially enabled its ideal source at the shaft's back-EMF voltage. At
rest this is a zero-volt clamp behind the motor's series resistance, before its
drive controller has established a current-limited command. The upstream cascade
can enter its rejected operating branch while evaluating this initial guess.

## Changes

The first motor guess now leaves the drive open and seeds its EMF command. Its
first controller evaluation establishes the motoring or regeneration command,
which must pass the normal current and energy acceptance checks before a physical
step is committed. A regression also checks regeneration on the first step of an
already spinning motor.

Repeated electrical trials can revisit the same physical matrix after source
switches change state. The subsystem can now reuse its matrix solver when every
double-double coefficient and the ordered state identities match exactly. A
changed resistance or even a changed low part of a coefficient prevents reuse.
RHS snapshots and floating-gauge RHS validation are reset on every restore.

The cache holds at most four matrices and 262,144 matrix cells per subsystem.
After a physical flush it retains only the current stamp, releasing other trial
solvers. Large matrices exceeding the budget use the ordinary rebuild path.

## Verification and measurements

`MotorTransformerCascadeTest` runs 150 physical steps in all four combinations of
transformer control mode and matrix reuse. It checks motor faults, transformer
recovery, current ratings, finite shaft energy, powered voltage stages, and matching
final voltage/current/energy/speed with reuse enabled and disabled. This is an
electrical reproduction, without a Minecraft world, entity collisions or chunk
loading.

The fixed-ratio startup settles after 52 motor-controller evaluations; the
target-voltage startup settles after 54, and each case completes 150 fault-free
physical steps. [Cascade results](verification/shaft-motor-bank/cascade-results.json)
include both reuse settings. The full `./gradlew build` passes **1,091 tests in
261 suites**, with zero failures, errors or skips;
[unit totals](verification/shaft-motor-bank/unit-results.json) record this run.

The matrix regressions cover exact coefficient and state matching, bounded cache
storage, changed resistances, fresh source commands, probe-only state preservation,
and rejection of unbalanced current on a floating island. Existing motor and
120-pole feeder tests cover starting, coasting, supply changes and regeneration.

The opt-in switching workload alternates cached and uncached paths in the same
JVM. It warms 40 probes, records seven rounds of 40 trials, and reports the median
of the final five rounds. Each trial switches the source mask and varies the
supply command. Port readings are compared outside the timed batches.

| Feeder nodes | Original ms/trial | Cached ms/trial | CPU reduction | Allocation reduction |
| ---: | ---: | ---: | ---: | ---: |
| 24 | 0.259611 | 0.019951 | 92.6% | 94.8% |
| 96 | 1.553786 | 0.117925 | 86.0% | 66.6% |
| 240 | 2.154011 | 0.482061 | 72.4% | 54.0% |

The 96- and 240-node cases perform two full factorizations rather than 320, with
318 solver reuses. [Raw rounds](verification/shaft-motor-bank/stamped-matrix-workloads.json)
include CPU time and allocation measurements. Wall-time variability is visible in
those rounds; these component measurements do not predict whole-server TPS.

Run the regressions and the optional workload with a full JDK 21:

```sh
./gradlew build
ELN_STAMPED_MATRIX_PROFILE=1 ./gradlew benchmarkTest \
  --tests mods.eln.sim.mna.StampedMatrixProfilingTest
```

`-Deln.disableStampedMatrixReuse=true` is sampled when a subsystem is constructed
and provides the uncached comparison path. It does not restore the original motor
startup guess. A new Spark recording in the affected world is needed to establish
the resulting server TPS and any remaining hotspots.
