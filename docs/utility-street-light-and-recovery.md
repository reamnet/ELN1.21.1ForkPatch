# Utility street lighting and converter recovery

## Utility Pole Street Light

Craft the new pole from a Utility Pole, Streetlight fixture, 120 V LED bulb and
iron. It appears in the **Lighting** creative tab. Its overhead terminals accept
the same intact power cables as other poles, with a 40-block connection range.

Right-click the pole while holding a **Mains Step-down Unit**, or open its menu
and insert one in the single upgrade slot.
Select **120 V** or **240 V** as the nominal output at a **15.2 kV** overhead supply. Since 3.0.26, pole transformers use a fixed ratio: a 5% primary sag or rise produces a 5% secondary sag or rise, plus winding/cable losses. The separate standalone unit retains active voltage regulation. The built-in
60 W lamp adapts to either selection. The lamp button switches just the light;
the converter's On/Off button controls the whole low-voltage supply.
Enable **Dusk: Auto** to switch the lamp with daylight while keeping the base
output available. See [streetlight controls and visuals](streetlight-qol.md).

Base-level wire connections can supply other devices from the same transformer
output. The lamp and those devices share the selected **10, 25, 50, 75 or 100 kW**
rating (10 kW by default). Primary fuse current is the rating divided by 15,200 V;
physical winding resistance produces additional load sag. A primary fuse disconnects the transformer on sustained
overload; replace a blown link by right-clicking with a **Pole Fuse Link**.
Removing the unit disconnects the grid supply and turns the
lamp off. The installed unit is returned when the pole is broken.

The head emits light beneath the fixture. It does not load remote chunks to keep
light effects alive. Use `/eln chunkload on 1` when the surrounding installation
also needs Minecraft block ticks while players are away.

## Mains Step-down Unit

The same upgrade item can be placed as a standalone converter. It appears in
**Power Electronics** and is crafted from a Variable DC-DC Converter, Optimal
Ferromagnetic Core, Machine Blocks and Copper Cable.

Its base terminals follow the existing variable converter convention: **input on
the left, output on the right**, relative to the front. Connect grid power with
a suitably rated wire; a Direct Utility Pole can bring an overhead line down to
these terminals. Open the menu to choose 120 V or 240 V.

This is a step-down device, with a maximum input rating of **51.2 kV**, 95%
electronics efficiency, a selectable **10–100 kW** output limit and a **100 A** input-current limit.
Output current is limited to the selected watts divided by the output voltage.
The input must remain above the selected output under load. Winding resistance
causes a small voltage drop at the terminals. It will not boost an undervoltage
line or backfeed the grid; it opens its active branches when disabled or outside
its operating range. Severe overloads can also leave the branches open instead
of forcing an unsafe operating point. Electrical losses heat the unit.
Cooling is sized for continuous rated operation. Use output wiring rated for the
selected current; low grid voltages also require more input current. These ratings
apply automatically to existing units and pole upgrades after updating the mod.

Output selection and enabled state are saved and can be copied with the
configuration tool. A standalone unit also retains them when broken and replaced.
Poles save their own settings and installed unit across world restarts.

See [utility distribution and house service](utility-distribution-and-service.md)
for the secondary rack, phase-selectable transformers, service drops and protection poles.
All existing poles and towers are three blocks taller in 3.0.25.

## Existing-device fixes

Variable and voltage-controlled converters no longer remain permanently off after
a failed numerical solve. They open their source branches, wait **one simulated
second**, then retry through the normal control, winding and electrical-limit
checks. Algebraic solver iterations do not advance this timer. This does not
repair damaged windings or insulation, override disabled controls, or add power.

Shaft generators now join the same converged electrical solve as their converter
loads. Previously a converter input could hold a generator near its startup
voltage even with a shaft turning at 202 rad/s; repeated failed solves could also
make an entire connected light circuit blink. The generator's droop, forward and
reverse current limits, and available shaft-energy budget must all be satisfied
before a step is accepted. Its field ramp advances once per physical step.
The numerical-failure message now correctly says it retries automatically.

The older **Utility Pole w/DC-DC Converter** now joins that same validated solve.
Its previous transformer controller could hold a spinning generator around 300 V
and absorb or supply unaccounted power on a mixed overhead line, even with an
unused low-voltage tap. Its existing 4:1 ratio, saved identities and watchdogs are
preserved; both active branches now open safely when there is no input. An unused
tap leaves its input branch open while retaining the expected open-circuit output
voltage, avoiding zero-current clamps that could trigger repeated numerical trips
on a loaded high-voltage line. Mains units use the same idle-input behavior.

Wire camouflage applied by holding **C** and using a covering block now draws that
block's model and uses its opacity as well as its existing collision. Creative
items are ordered by their descriptor family and registered group/sub-ID, keeping
materials and device types together instead of alphabetizing their names.

## Checks

`./gradlew build` runs the unit suite. For native travel, menus and rendering checks,
run `./gradlew runClient` under an X/GL display with
`-Deln.worldInteractionChecks=true` in `JAVA_TOOL_OPTIONS`. The opt-in check creates
a uniquely named disposable world. It exercises six variable/voltage-control
setups, controlled numerical recovery, C-key camouflage, item grouping, both new
devices, menu packets, travel to unloaded chunks and back, and a saved-world restart.
It writes `run/client/world-interaction-checks.json` and screenshots under
`run/client/screenshots/world-interaction-*.png`.
The same check also builds 120 alternating 120/240 V street lights over a
3,840-metre bare-aluminum overhead line starting at 14 kV, fed through the older transformer pole,
with a real 9 kW load at the far end.
It repeatedly samples the pole outputs, travels away until chunks unload,
changes the feed voltage and checks the installation again after a saved-world restart.
It also connects direct/transformer pole taps through target-voltage converters
to actual motor and teleporter electrical loads. See
[external pole loads](external-pole-loads.md) for the 3.0.8 regression details.

For generator/grid cold starts, run `runClient` with
`-Deln.generatorChecks=place -Deln.generatorWorld=<new-test-world>` in
`JAVA_TOOL_OPTIONS`, then start a separate JVM with
`-Deln.generatorChecks=verify -Deln.generatorWorld=<same-test-world>`.
The 18 placed circuits include the bare Aluminum 1000 kcmil cable, both generator
output directions and placement orders, plus six generator-fed mains, variable
and one-way buck converter loads at 120/240 V. Repeated samples check for blinks
before and after restart; verification also checks restored connections without
replacing any cable. Results are `run/client/generator-checks-{place,verify}.json`.
