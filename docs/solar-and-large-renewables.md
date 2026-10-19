# Solar and large renewable generators

Added in 3.0.17. Existing panels, turbines, saved IDs and recipes remain available.
Find the MPPT and hybrid inverter under Power Electronics; the new generators are
under Machines. All four have survival crafting recipes.

## Solar wiring

Use either `panels -> Hybrid Solar Inverter -> load`, or
`panels -> MPPT Solar Controller -> DC distribution/battery/Hybrid Solar Inverter`.
Both solar devices include automatic MPPT. The separate controller is useful for
charging an existing battery system or supplying a DC bus at a selected voltage.
It does not contain rechargeable batteries or replace the UPS.

With the display facing you, the terminals at floor level are:

| Terminal | Connection |
| --- | --- |
| Blue, left | Solar positive |
| Black, rear | Solar negative/return |
| Orange, right | Regulated output |
| Dark blue, front (hybrid only) | Optional grid input |

Connect both poles of a floating small solar panel/string. Grounded arrays can
use the same common return. When feeding the solar input from a ground-referenced
one-terminal device (including the standalone MPPT output), connect the receiving
black solar-return terminal to a Ground Cable. Use correctly insulated cable and keep solar, grid
and output conductors separate. Neither device feeds power back to an input.
The simulation uses averaged DC; “inverter” describes its role, not an AC waveform.

The MPPT controller supplies up to **10 kW**, with **12–3200 V** programmable output.
The hybrid supplies up to **20 kW**, with **120 V or 240 V** output. Solar inputs
accept **6–51200 V** and grid input **12–51200 V**; each input/output is limited to
**200 A**. Consequently low-voltage panels cannot produce their full power through
a single controller if the current limit is reached. The output is limited by
actual source power, cable losses and conversion efficiency, not just the nameplate.

The GUI shows solar voltage/power, grid voltage/power, output voltage/power and
DC-link energy. Select 120/240 V with a button on the hybrid, or enter the MPPT's
output voltage. Hybrid priority modes are:

| Mode | Behavior |
| --- | --- |
| Solar first + grid assist | Uses available solar energy first; grid supplies the shortfall |
| Grid first | Uses grid capacity first; solar assists when it is insufficient or absent |
| Solar only | Never draws grid power |
| Grid only | Solar harvesting and discharge are disabled |

MPPT perturbs the solar input impedance and observes real panel power every
quarter-second. Allow roughly 30 seconds to settle after initial installation;
it continues tracking when sunlight or the array changes. If the load needs less
than the panels can supply, harvesting is curtailed. It cannot force surplus power
into an unloaded output. There is no need to set a panel voltage manually.

A **5000 J internal DC link** decouples solar tracking from output regulation. It
starts empty and its real energy is saved with the placed block. This is a short
buffer (at most a quarter-second at the hybrid's full rating), not overnight
storage. Harvesting is 98% efficient and output conversion 97% efficient, before
wiring losses. A UPS/battery bank is still required for sustained backup. Stored
DC-link energy is not copied by the configuration tool or dropped item.

## Utility Wind Turbine 3.2kV

A new tower with a **9-block rotor** and **15-block total clearance**, substantially
larger than the normal turbine. Its regulated output is **3200 V**, up to **20 kW**
at 12 m/s. Output falls with wind speed and obstruction; wind below 3 m/s or at
least 25 m/s produces no power. Place it in open sky. The orange terminal at the
front of the base supplies power. Use cable rated for at least 3.2 kV.

## Large Watermill 3.2kV

A new **8-block-diameter wheel**, with a nine-block reserved height. Output is
**3200 V**, up to **10 kW**. Run a flowing-water channel at base level behind the
wheel. Three samples across its width, at the center and three blocks to either
side, each provide one third of the rating. These are two blocks behind the base,
outside the reserved wheel plane. Source/still water and an empty channel provide
no power. The orange front terminal supplies power.

Moving parts reserve ghost clearance. Breaking the base or a clearance block
removes the structure normally. Environmental samples only inspect loaded chunks;
these machines do not force-load chunks or generate power while unloaded. On
reload the environment is resampled automatically, without replacing a cable.
Both generators limit their power/current locally under overload and block reverse
power. A larger model and 3.2 kV rating do not imply unlimited power.

## Wall-mounted solar cabinets (3.0.20)

Power Electronics also contains the **Wall-mounted MPPT Solar Controller** and
**Wall-mounted Hybrid Solar Inverter**. Craft either from its floor-standing
version and one Iron Plate, then right-click a vertical wall. They work on all four
wall directions without a floor. The back plate touches the wall; the enclosure
projects about one third of a block, with a matching shallow collision box.

Cables run on the same wall face, viewed from the front:

| Position | Connection |
| --- | --- |
| Blue left | Solar positive |
| Black bottom | Solar return |
| Orange right | Regulated output |
| Dark blue top (hybrid only) | Optional grid supply |

Output voltage, MPPT, grid priorities, power ratings and GUIs are the same as the
floor units. Mounting direction and settings survive a world reload. Floor units
keep their existing terminals. Removing the supporting wall drops the cabinet.
Crafting a new housing starts with default settings; configure it after placement.

For native placement, powered wall wiring, GUI, collision and reload checks, run
the client with `-Deln.wallCabinetChecks=true`. The isolated test world exercises
all three cabinets on four walls and writes `wall-cabinet-checks.json` under the
client run directory.
