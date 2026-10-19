# Automatic backup power (3.0.16)

The **Automatic Transfer Switch** (Power Electronics tab) monitors a mains feed
and supplies a load from either mains or a **Standby Gasoline Generator**
(Machines tab). Both have crafting recipes and a compact status/configuration GUI.
Existing block and item identities are unchanged.

## Wall cabinet (3.0.20)

The **Wall-mounted Automatic Transfer Switch** is in Power Electronics. Craft it
from an Automatic Transfer Switch and one Iron Plate. Right-click a vertical wall
to mount it; all four wall directions work, with no floor required. Its shallow
case projects about one third of a block from the wall and has matching collision.

Run cables on that same wall face: **blue left = mains, yellow bottom = generator,
orange right = load**, as viewed from the front. The GUI, wireless starting, rated
voltage, contactor sounds and transfer timing work just like the floor version.
Existing floor ATS units keep their original wiring and appearance. Breaking the
supporting wall drops the cabinet. Crafting a new housing starts with default
settings; configure its voltage and wireless channel after placing it.

## Wiring and wireless starting

Looking at the front of the ATS, connect floor cables to:

| Terminal | Connection |
| --- | --- |
| Blue, left | Normal mains supply |
| Yellow, rear | Generator power output |
| Orange, right | Protected load/grid |

The generator's two orange side terminals are the same electrical output.
Connect one to the yellow ATS terminal. Keep that cable separate from mains and
the ATS load cable. The front panels and coloured terminals rotate with the blocks.

Set the **same rated voltage** on the ATS and generator (default 240 V; range
12–51,200 V). Mains must also supply that voltage. The ATS is a pass-through
switch; it does not transform voltage. A 14 kV distribution line can be backed
up directly by setting both units to 14,000 V.

Enter the same **wireless start channel** in both GUIs and click **Link** on each.
Channels are case-sensitive, trimmed, and limited to 64 characters; a blank
channel disables remote starting. Use a separate channel for each independent
backup system. Wireless carries the start request; electricity still travels
through the generator cable. Linked blocks must be loaded in the same dimension.
The link has no distance limit and does not load chunks.

With the generator set to **Auto**, the normal sequence is:

1. Mains supplies the load. The generator is stopped and consumes no fuel.
2. A failed/out-of-range mains feed opens its contact. After one second of
   failure, the ATS requests a generator start.
3. A fueled generator takes three seconds to start. The ATS waits for one second
   of suitable generator voltage, then connects the load.
4. After five seconds of stable mains voltage, the ATS switches back to mains.
   The generator runs for a further five-second cooldown and stops.

Every source change includes at least a quarter second with **both contacts
open**. Mains and generator contacts can never close together. Short supply dips
do not start the generator, and a wrong-voltage generator is never connected to
the load. The selected mains feed tolerates 80–120% of rated voltage; recovery
requires 90–110%. Generator transfer requires 85–115%.

This is a transfer switch, so the load loses power during detection and engine
startup. A [UPS](programmable-ups.md) after the ATS can bridge that interval.
Multiple loaded ATS blocks on the same channel combine their requests: the
generator stays on until all requests clear. Requests expire within five seconds
if their sender disappears; normal block unload/removal withdraws immediately.

## Generator and fuel

The generator is rated for **10,000 W**, with a **200 A** overload threshold (so lower
voltages have a lower usable power rating). Its GUI shows fuel, state, voltage
and output power. Modes are **Auto**, **Manual run**, and **Off**. Manual run starts
without an ATS request; Off stops the engine. Changing output voltage stops the
engine before restarting at the new setting.

The tank holds **20,000 mB / 20 buckets**. Right-click with a fluid container to
refuel, or connect another mod's fluid pipe to any side. Standard NeoForge fluid
capabilities handle buckets, modded tanks and container remainders. Emptying with
a container or pipe is supported. A tank cannot mix different fluid identities
or differing fluid components; empty it before changing gasoline types.

Gasoline is accepted through:

- Registry paths `gasoline`, `flowing_gasoline`, `petrol`, or `flowing_petrol`, in
  **any namespace** (including these names beneath a path such as `fuels/petrol`).
- Fluid tags `c:gasoline`, `c:fuels/gasoline`, `forge:gasoline`,
  `forge:fuels/gasoline`, or `eln:standby_gasoline`.
- The existing configured gasoline/engine-fuel aliases in `config/eln/fluids.cfg`.

Electrical Age does not add its own gasoline fluid or require a particular fuel
mod. For an unusual fluid name, add it to this data-pack file (Minecraft 1.21.1
uses singular `fluid` in this path):

`data/eln/tags/fluid/standby_gasoline.json`

```json
{
  "replace": false,
  "values": ["yourmod:refined_motor_fuel"]
}
```

Fuel use follows measured generated electrical energy, with an idle minimum of
300 W and 25% electrical efficiency. Existing fuel heat values are used when
configured; otherwise gasoline uses 2173.5 J/mB before efficiency. Fractional fuel
is paid for before generation and its remaining energy stays in the engine,
preventing pipes from extracting already-consumed fuel. Fuel components and that
small reserve survive saves and picking up the block.

Voltage droop while at the power limit stops the engine after half a second;
severe output collapse or reverse power trips immediately. Protection latches.
Correct the wiring/load, then
use **Reset protection**. It does not repeatedly restart into an overload.
Running out of usable fuel stops the engine; refueling allows Auto to start again
if an ATS still requests it. A restart rechecks requests and voltage before
closing contacts; it never restores a saved closed contact blindly.

## Verification

`./gradlew test` exercises real MNA failover circuits, open-contact isolation,
debounce, all supported voltage ranges, load losses, fuel energy accounting,
latched overload/reset, empty tanks, manual/off modes, wireless channel/world
isolation, fluid capabilities, mod namespaces/tags and saved fluid components.

For the opt-in native client check:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.standbyChecks=true" ./gradlew runClient
```

It creates an isolated development world and test-only fluids/buckets in three
namespaces. The check uses real container transactions, pipe capability calls,
three-port cables, GUI packets, mains failure/restoration, overload/reset and a
saved-world restart, including direct 14 kV operation. These test fluids are never registered in ordinary gameplay.
The report is `run/client/standby-checks.json`; screenshots are named
`standby-*.png` in `run/client/screenshots`. Run Gradle commands sequentially.

Electrical source power is capped to the fuel available for that physical step
and the 10 kW / 200 A rating, including a short circuit. Severe voltage collapse
trips immediately; stopped/protected engines remove their controller from the
solver. If a long or undersized generator cable drops below usable voltage when
the load connects, the ATS holds that feed open rather than repeatedly reconnecting.
Fix the cable/load and toggle the ATS Off/On to reset it. A generator that stops
for a full second, or stable mains recovery, also clears this feed check.

The 3.0.16 verification passed all 637 unit tests with no failures or skips,
including 120 mixed-voltage pole converters on a long 14 kV standby feeder. All
nine native checks passed, including actual fluid capability/container use, GUI
linking, measured transfer intervals, direct 14 kV failover and world reload.
