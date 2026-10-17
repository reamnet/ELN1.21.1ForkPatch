# External loads on lit overhead grids

Version 3.0.8 addresses reproduced failures when taking power from a lit
overhead grid through a Variable DC-DC Converter in target-voltage mode.
The regression fixtures cover both direct taps and transformer poles, with
850 V and 3,200 V converter outputs.

## Drive trials and physical time

A Large Shaft Motor attached behind an 850 V converter dropped a working
feeder to zero. Every repeated electrical trial advanced its legacy soft-start,
integral controller, filters and stall timer. Its voltage command also sat
outside the converters' coupled numerical correction.

The motor now participates in the same coupled trial/acceptance loop as the
generator and converters. Each trial starts from the previous physical drive
state, so repeated evaluations do not advance its clock. An affine supply
probe preserves its trial command instead of replacing it with a previously
committed voltage. A spinning motor starts with its existing back EMF.
An idle drive requesting zero current opens its switches rather than holding
an unnecessary voltage clamp. Current limits, regeneration and shaft energy
accounting remain active.

## Correction on a shared feeder

Changing a streetlight from 120 V to 240 V reproduced another rejection while
the external taps were connected. The numerical correction tried to perturb an
unloaded output already fixed by its target. Either direction could activate
an idle attached drive, preventing a derivative on the current operating branch.
Those fixed, unloaded outputs are now excluded from the free correction
variables. The existing coupled solver and normal current/power checks remain
in use. Candidate voltages must also agree with the actual controller commands;
power balance alone cannot accept an off-target output.

## Idle transfer and tiny loads

An independently energized converter output already at the target voltage
needs no power transfer. In that narrow operating region, a target-voltage
converter opens its input and matches the existing output voltage. This avoids
opposing voltage adjustments between a coasting motor and its converter near
zero power. Forward or reverse transfer resumes when the output moves away
from the target. Candidate power balance is still checked.

An idle machine resistance of 1 GΩ could also falsely reject a converter or
pole transformer on a 120-light feeder. Across a stiff HV line, rounding a
voltage command by one representable floating-point increment can exceed the
original power tolerance for that tiny load. Transformer acceptance includes
a calculated rounding bound from the port resistance and voltage resolution.
It does not discard idle loads or introduce a fixed extra wattage allowance.
A regression deliberately exceeds the bound and verifies rejection.

An unloaded transformer can balance zero watts while retaining an obsolete
secondary voltage after a supply change. Acceptance now also checks its actual
solved turns ratio, preventing a stale induced voltage from reaching the
voltage watchdogs. A regression verifies that zero power cannot hide this error.

If a transformer still fails validation, its ports are isolated and the
remaining network is re-solved before considering a connected-group shutdown.

## Checks

Release validation: **578 unit tests**, **3 benchmarks** and **9 native in-game
checks** passed, with no failures or skipped tests.

`PoleLoadConnectionTest` starts a feeder with 10 or 120 production step-down
loads, then connects external branches through a direct tap or a fixed 4:1
pole transformer. Target-voltage converters run at both **850 V and 3,200 V**.
It switches **2 kW and 10 kW resistive loads** on/off, attaches the production
**Large Shaft Motor**, and exercises an idle **1 GΩ** machine input. Each step
checks that the feeder and street lights stay powered. Additional cases change
a loaded supply from **14 kV to 3.2 kV** and attach an already-coasting motor while switching a streetlight to 240 V.

`MotorDriveRuntimeTest` compares normal operation against repeated algebraic
evaluations and sampling settings of 1 versus 50. It checks the same physical
drive evolution, actual shaft startup, current limits and regenerative energy
balance. Converter regressions cover the voltage-resolution bound, local
failure isolation, zero-transfer standby and forward/reverse transfer.

The native `WorldInteractionChecks` fixture takes four overhead taps from its
120-pole line: direct and transformer poles feeding 850 V teleporter charging
resistors and 3,200 V Large Shaft Motors through real inventory-configured
Variable DC-DC Converters. An additional unused legacy transformer pole is a
spur, so it does not carry the entire feeder through its internal legacy cable.
Thermal and voltage watchdogs remain active. It connects the machines after the street lights
are powered and checks the taps following chunk travel and a saved-world
restart. The teleporter's normal 2 kW charging demand is selected directly on
its production resistor; teleport destination/player transfer behavior is
outside this electrical check.

With a Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=4 test
```

Native check instructions are in
[utility street lights and recovery](utility-street-light-and-recovery.md#checks).
