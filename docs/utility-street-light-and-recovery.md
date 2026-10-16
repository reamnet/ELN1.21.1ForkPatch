# Utility street lighting and converter recovery

## Utility Pole Street Light

Craft the new pole from a Utility Pole, Streetlight fixture, 120 V LED bulb and
iron. It appears in the **Lighting** creative tab. Its overhead terminals accept
the same intact power cables as other poles, with a 40-block connection range.

Open its menu and insert a **Mains Step-down Unit** in the single upgrade slot.
Select **120 V** or **240 V** to match the devices you want to supply. The built-in
60 W lamp adapts to either selection. The lamp button switches just the light;
the converter's On/Off button controls the whole low-voltage supply.

Base-level wire connections can supply other devices from the same regulated
output. The lamp and those devices share the unit's **1 kW** rating and **10 A**
output-current limit. Removing the unit disconnects the grid supply and turns the
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
electronics efficiency, a **1 kW** output limit and **10 A** input/output limits.
The input must remain above the selected output under load. Winding resistance
causes a small voltage drop at the terminals. It will not boost an undervoltage
line or backfeed the grid; it opens its active branches when disabled or outside
its operating range. Severe overloads can also leave the branches open instead
of forcing an unsafe operating point. Electrical losses heat the unit.

Output selection and enabled state are saved and can be copied with the
configuration tool. A standalone unit also retains them when broken and replaced.
Poles save their own settings and installed unit across world restarts.

## Existing-device fixes

Variable and voltage-controlled converters no longer remain permanently off after
a failed numerical solve. They open their source branches, wait **one simulated
second**, then retry through the normal control, winding and electrical-limit
checks. Algebraic solver iterations do not advance this timer. This does not
repair damaged windings or insulation, override disabled controls, or add power.

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
