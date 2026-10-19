# Programmable UPS

Version 3.0.14 adds a self-contained UPS in the Power Electronics creative tab.
It charges removable batteries from its input and supplies a regulated output
from the battery bus. Losing the input automatically leaves the output running
on the batteries; restoring the input resumes charging.

## Batteries and upgrades

Install **24 batteries in one complete rack**, or fill both racks for **48**.
The racks operate independently. A partially filled rack remains inactive and
keeps its batteries' charge until all 24 slots are filled. Use the rack button
to switch between the two pages of 24 slots. Shift-click fills the visible rack.

The UPS accepts the existing **Portable Battery** and **Portable Battery Pack**
items, including mixed types and different charge levels. It does not consume
them. Each item retains its own stored energy and charge/discharge rating.

| Battery type | Capacity with 24 | Capacity with 48 | Battery power available with 24 / 48 |
| --- | --- | --- | --- |
| Portable Battery | About 267 Wh | About 533 Wh | 6 kW / 12 kW before inverter losses |
| Portable Battery Pack | About 1,067 Wh | About 2,133 Wh | 24 kW / 48 kW before inverter losses |

The UPS output is limited to **10 kW and 200 A**, as well as the actual batteries'
ratings and charge. The inverter is 95% efficient, so a rack of 24 small Portable
Batteries supplies at most roughly 5.7 kW. A second rack of those batteries
increases both capacity and usable output. A rack of Portable Battery Packs can
already reach the UPS's 10 kW rating.

Batteries keep their exact charge when removed. Removing a battery makes its
rack inactive; another complete rack can keep running. Breaking the UPS drops
the installed batteries with their individual charge. Picking up and placing
the empty UPS preserves its voltage and on/off settings, without duplicating
stored battery energy. World saves preserve all installed cells and their charge.

## Wiring and controls

Connect input cable to the **blue terminal on the left**, and output cable to
the **orange terminal on the right**, viewed from the front. Both terminals meet
floor-mounted cables. Input and output are isolated from each other; the battery
cannot feed power backward into a failed input grid.

The input accepts **12–51,200 V**, subject to available input current and cable
losses. A weak input can supply part of the load while the batteries supply the
rest. The charger is limited to 100 A input and 16 kW total DC output, including
power passed through to the inverter. Its electronics are 95% efficient. Match
the installed external cables to the voltage and current.

Open the GUI, enter an output voltage from **1 to 3,200 V**, then click **Set V**.
The voltage field supports select-all and pasting. Invalid, nonfinite or
out-of-range settings are rejected. The output button disables the output while
allowing charging to continue. Configuration copying transfers settings only.

The GUI shows status, charge percentage, usable capacity, installed/active cell
counts, measured input/output voltage and power, and estimated backup time at
the current load. The estimate accounts for inverter efficiency; current limits,
wire losses and changing loads affect actual runtime. With no load it shows
**Backup: no load**.

States distinguish online operation, charging, battery backup, battery assist,
empty or incomplete racks, output limits and thermal protection. Overloads limit
or protect the output locally; cooling lets the hardware recover automatically.

The crafting recipe uses two Variable DC-DC Converters, two Machine Blocks,
four Copper Cables and an Optimal Ferromagnetic Core. Batteries are added
separately.

## Development checks

Regenerate the enclosure with `python3 tools/power/build_ups_model.py`. After
registration or recipe changes run `./gradlew runData generateLangFiles` and
review the generated resources. The native check uses `-Deln.upsChecks=true`
with `runClient` and writes `ups-checks.json` and screenshots in the client run
directory. It exercises the actual rack GUI, changing output voltage, outages,
recharging, live cell removal, world restart and breaking the block.

In 3.0.15 the UPS also sounds a nearby warning when grid power is lost or the
output is overloaded. Battery backup beeps immediately and then every five
seconds; overload/protection warnings repeat every second. The alarm stops
when normal operation resumes or the output is switched off. An active battery
rack must be installed. The warning uses the existing game sound system and is
audible within eight blocks.

## Generator charging stability (3.0.20)

The charger now starts gradually and reduces charging demand when its input
voltage sags. It behaves as a consuming input instead of insisting on constant
power from a limited generator; the batteries continue supplying the regulated
output while charging backs off. Charger current, battery ratings, efficiency and
the 10 kW inverter limit still apply.

The standby generator also distinguishes temporary solver trial voltages from
actual reverse power. Its real reverse-power and overload protection remain
active. If a generator already saved the old false reverse-power trip, use its
**Reset protection** button once after updating. Wire generator output to the
UPS's **blue input**, with the protected loads on its orange output.

## Wall mounting and manual bypass (3.0.21)

Craft a Programmable UPS with an Iron Plate to make the wall-mounted variant.
It keeps both 24-cell racks, all compatible cell types and programmable output.
Place it against a vertical solid wall and run cables on that same wall face.
The blue input is on the left and orange output on the right when facing the cabinet.

The **Bypass** GUI button opens the inverter and connects input directly to output.
**Bypass passes the actual input voltage unchanged and provides no battery backup.**
For example, a 240 V input produces approximately 240 V in bypass even if the
programmed output is 120 V. Match the connected load to the incoming voltage
before using this mode. Bypass works without installed cells. Switching it off
restores the programmed voltage and battery operation. Charging can continue
while bypassed; no inverter output is paralleled with the direct feed.
