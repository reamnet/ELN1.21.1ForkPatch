# Large grids and step-down unit convergence

Version 3.0.7 addresses a reproduced `NON_CONVERGENT` shutdown when joining
shaft-powered grids with 120 Mains Step-down Units. The production controller
could become stuck when the sources entered different parts of their current
limit curves. The root solver then opened the entire connected converter group,
including generators, and retried a second later. That produced a repeating
grid-wide blackout despite a valid electrical operating point being available.

The solver now tries a bounded set of starting voltages at the generators'
actual current-limit boundaries. Each trial runs the real controllers and must
pass the existing current, power, available shaft energy and network checks.
Generator current-source, current-sink and energy-limit regions also invalidate
the cached convergence derivative when its branch changes.

If the normal solve and bounded restart still fail, rejected regulated load ports
are opened locally first. The remaining network is solved and validated again
before committing physical state. A healthy generator and neighboring lamp can
therefore stay on when one load is rejected. The existing group shutdown remains
the final fallback when the remaining network cannot be validated. Retry timers
and physical simulation time advance once per electrical step.

## Regression coverage

`JoinedPoleGridsTest` connects two production shaft-generator processes to real
step-down hardware and 60 W lamp resistances. It covers 10 and 120 units,
near-14 kV supplies, unequal source settings that exercise current limiting,
changing cable resistance and shaft EMF, and a source limited to 8 kW of available
shaft output. It checks every sampled lamp after joining the grids, current and
shaft energy limits, and independent whole-network electrical power balance.
Another case joins the low-voltage outputs. An injected rejected load verifies
that a healthy generator and lamp remain powered with one physical commit.
An unrelated feeder stays powered without entering the restart search. A
generator with a positive field but no available shaft energy remains idle
instead of falsely rejecting its zero-current operating point.

The native `WorldInteractionChecks` campaign now places **120 actual street-light
poles over 3,840 metres**, starting at **14 kV**, with mixed 120/240 V outputs and
an additional 9 kW load on the last pole. It repeatedly checks outputs before and
after a supply change, chunk unloading/return and reopening the saved world.

Release validation passed all **565 unit tests** and **8 native Minecraft
checks**, including this 120-pole campaign. Generator-grid, shared-output
overload/recovery/reconnection and linear-control benchmarks also passed.

Run the regressions with a Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=4 test \
  --tests mods.eln.transparentnode.utilitylight.JoinedPoleGridsTest
```

See [native check instructions](utility-street-light-and-recovery.md#checks)
and [performance measurements](pole-converter-performance.md).

## Supply capacity

120 installed lamps consume about **7,200 W** at their outputs, or approximately
**7,580 W** before winding and line losses at 95% converter electronics efficiency.
External loads add to this demand. The 10 kW rating is per module; it does not
increase the power available from the generators. Protection and power limits
remain active in 3.0.7.
