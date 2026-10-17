# Pole converter performance and long lines

## Version 3.0.6: use the default server workload

The earlier regression fixture used `interSystemOverSampling=1`. Normal servers
default to **50**, which was also repeating every exact-network converter's port
probes 50 times before the root solver checked a candidate. The earlier timings
below therefore understated this work at normal server settings.

Version 3.0.6 separates those loops. Legacy partition and transformer proxies
retain their configured sweeps; conservative converters run in the bounded
trial/acceptance loop. Legacy supplies and loads are reconciled after converter
commands change, including Newton corrections, before power balance and limits
are checked. Physical commits, retry timing, electrical frequency and the 10 kW
rating are unchanged. There is no need to lower the server's sampling setting.

Grounded port probes also avoid copying their RHS and applying an unused voltage
gauge. Floating networks retain reference and current-balance checks using
primitive arrays, without allocating a map or a list of residual terms per probe.

The generator benchmark and the 32-unit long-line regression now use **50**.
`ConverterOversamplingTest` compares actual electrical outputs and controller
counts at 1 and 50, checks a single physical commit per step, and tests a legacy
transformer feeding a regulated converter in both registration orders while
changing the source and load.

On the same local Java 21/JFR fixture, the 64-unit / 323-state case measured:

| Default-50 case | 3.0.5 median / p95 | 3.0.6 median / p95 |
| --- | ---: | ---: |
| Changing shaft speed | 15.13 / 22.33 ms | 2.09 / 8.45 ms |
| Speed and wire temperature changes | 14.46 / 32.15 ms | 1.84 / 17.14 ms |
| Unpowered grid | 0.121 / 0.225 ms | 0.057 / 0.097 ms |

Over 60 changing-speed samples, controller evaluations fell from 405,925 to
26,910. Matrix factorizations remained zero in that case and 12 with changing
wire resistance. These are fixture timings, not a predicted server TPS; matrix
changes, topology changes and very large networks still have a cost. The separate
64-unit shared-output test measured 6.39 ms steady p95, 211 ms recovery and 242 ms
reconnection. The unmodified overload and current-limit checks passed.

## Earlier ForkPatch 5 changes

ForkPatch 5 removes repeated matrix rebuilds from idle/undervoltage Mains Step-down
Units, including the identical controllers installed in Utility Pole Street Lights.
An open converter port is now measured using a unit-current response of the
existing matrix. Measuring it no longer temporarily closes the branch and changes
the source mask. This preserves the voltage, impedance and floating-reference
checks without repeatedly factoring the entire overhead grid.

Large networks use double-double LU factorization and only the inverse rows that
port measurements need. Sparse variable ordering is reused across temperature
updates, with fresh numerical pivoting. Nearby converter operating points can
reuse a convergence derivative; changed operating regions or failed improvement
rebuild it. Every accepted step still checks power balance, generator droop,
current limits and available mechanical energy. Physical time advances once.

The older **Utility Pole w/DC-DC Converter** also uses that shared, validated
transformer process. A regression at 202 rad/s previously held a Large Generator
near 301 V with that pole on the feeder. The updated test requires normal output,
checks power balance with an unused and loaded low-voltage tap, and stops/restarts
the source. The existing 4:1 ratio and saved component names are unchanged.
Idle transformer/converter inputs now remain open while their outputs retain the
correct open-circuit voltage. A load reconnects the power path normally. This
avoids trying to clamp a loaded high-voltage line at exactly zero current, which
can fall below the voltage command's numerical resolution and trip the entire
connected group into retries.

## Measured regression fixture

`GeneratorGridProfilingTest` uses the production Large Generator, 60 W loads on
production mains units, and a branching feeder. It maintains a 202 rad/s shaft,
varies its speed slightly each step and, in the thermal case, updates every wire's
resistance every fifth step. Measurements below are **milliseconds per electrical
step**, not Minecraft tick time or a prediction for a particular server.

The 64-unit case has a largest subsystem of 323 states. Both runs used the same
local Java 21 environment and fixture, including JFR recording. The baseline is
ForkPatch 4 (`4b7d93a9`); after warm-up there are 60 powered samples and 12 idle
samples. Each unit's actual load remains approximately 60 W despite the higher
nameplate capacity in ForkPatch 5.

| 64-unit case | ForkPatch 4 median / p95 | ForkPatch 5 median / p95 |
| --- | ---: | ---: |
| Changing shaft speed | 64.37 / 79.86 ms | 3.76 / 5.13 ms |
| Speed and wire temperature changes | 69.38 / 474.26 ms | 3.92 / 17.27 ms |
| Unpowered grid | 3341.61 / 3667.96 ms | 0.17 / 0.31 ms |

The 12 idle steps previously caused 1,584 full matrix inversions; the updated
fixture causes none. These timings include JVM/GC variation. Large subsystems
still retain dense stamp arrays; this improves repeated arithmetic and solves,
not unlimited network size.

The existing shared-output benchmark also passes at 64 converters (steady p95
14.17 ms). Its worst measured control-recovery and reconnection operations still
take 1.34 s and 1.03 s respectively. The changes remove repeated idle rebuilds and
reduce steady work; they do not eliminate every large topology-change pause.

Run the benchmark independently of the correctness suite:

```bash
./gradlew --no-daemon --max-workers=4 benchmarkTest \
  --tests mods.eln.mechanical.GeneratorGridProfilingTest
```

It prints `GENERATOR_GRID_BENCH` / `GENERATOR_GRID_OFF_BENCH` in the JUnit report
under `build/test-results/benchmarkTest` and writes `build/solver-profile.jfr`.
For a comparison, copy the same test file into a separate ForkPatch 4 checkout
and run it with the same command and JDK.

## Long-line and rating checks

`LongLineMainsTest` exercises 32 alternating 120/240 V converters connected by
31 real 40 m aluminum span resistances. It varies supply voltage and cable
resistance at 3.2, 12.8 and 51.2 kV, includes weak-feeder cases and a shaft-driven
generator, then removes and restores the supply. The test checks every output
repeatedly and verifies that unpowered probes do not cause new factorizations.

The opt-in native `WorldInteractionChecks` places twenty actual street-light
poles over a 640 m overhead line with the bare Aluminum 1000 kcmil cable and an
older transformer pole on the feeder. Their
modules alternate 120/240 V; the last pole also supplies a real 9 kW load. Repeated
samples cover voltage changes, actual chunk unloading/return and a saved-world
restart. See the [device guide](utility-street-light-and-recovery.md#checks).

The step-down unit now has a 10 kW shared output rating, 100 A input protection,
and output limits of 83.3 A at 120 V or 41.7 A at 240 V. Cooling is sized for rated
continuous operation. Unit tests check delivered load power above 9.5 kW at both
settings, conservation, overload protection and steady thermal headroom.
