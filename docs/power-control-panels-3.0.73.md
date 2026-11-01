# Solar and protection controls — 3.0.73

The floor and wall MPPT/hybrid cabinets now have distinct front panels with live
PV watts, grid-input/grid-feed watts, output volts, operating lamps and a DC-link
charge bar. Their housings and printed legends remain in terrain buffers; digits,
lamps and bars update without rebaking. The protective relay shows input volts,
line amps, UV/OV/overcurrent/differential indicators and its latched trip lamp.
The load shedder shows input volts, total watts and four numbered priority rows.
Full operating messages and electrical readings are translated in the GUIs.

## MPPT grid feed and meter sell-back

Existing MPPT installations retain **Regulated supply** mode. In that mode the
selected voltage supplies an isolated load and backfeed from an energized output
is blocked. The hybrid retains its existing four priority modes and separate grid
input.

To feed surplus solar into a grid:

1. Set the MPPT output voltage to the grid's nominal voltage (12–3200 V).
2. Connect its orange output to the local bus, on the load side of the service meter.
3. Select **Output mode: Grid feed** in the MPPT GUI.

Grid feed requires an already energized bus between 80% and 110% of nominal. It
injects available solar energy instead of holding the bus at a fixed voltage.
The 10 kW output rating, 200 A limit, 110% voltage ceiling, real PV input energy,
DC-link reservoir and conversion/wire losses all remain enforced. It stops feeding
an unpowered or out-of-range bus and stops after the DC link drains at night.
A grid/source and upstream path that accept reverse flow are required; directional
converters continue to block reverse power.

The MPPT's feed counter measures energy delivered at its output terminal. The
service meter measures **net** export after local consumption and feeder losses.
These are separate readings: a 10 kW solar feed into a 6 kW local load sells back
about 4 kW before feeder losses. Existing analog and digital meter export registers
record this reverse flow; this does not introduce currency or billing mechanics.
Mode and feed counter persist with a placed device. Moving/copying settings does
not duplicate stored DC-link energy or feed counters.

This is an averaged DC simulation. Live-bus voltage checks are not a model of AC
frequency/phase synchronization or certified anti-islanding. Multiple generators
can maintain a shared island bus; the energy limits still apply.

## Automatic Load Shedding Controller

Connect one incoming supply and up to four separate outgoing branches. On the
right face, the existing terminals are **left P1, right P2, down P3, up P4**.
P1 is highest priority. Sustained low input voltage or excess input watts opens
P4 first, then P3, P2 and finally P1, one branch per shed delay.

The GUI exposes six settings:

| Setting | Default |
| --- | --- |
| Nominal input | 240 V |
| Power budget | 50 kW |
| Shed delay | 1 second per branch |
| Restore delay | 5 healthy seconds per branch |
| Shed below voltage ratio | 0.85 (204 V at 240 V nominal) |
| Restore above voltage ratio | 0.95 (228 V at 240 V nominal) |

Restoration starts with the highest disconnected priority. Voltage must exceed
the restore threshold, current connected demand must be below 80% of budget, and
the next branch's projected demand plus current demand must fit within 90% of
budget. The projected branch demand uses its last measurement, normalized for
voltage. The controller holds an oversized branch off instead of retrying it every
five seconds, and does not skip it to restore a lower priority branch.

The GUI shows each branch's live watts or last demand at nominal voltage, why the
controller is waiting, the next priority and remaining delay. If you reduced a
load while it was disconnected, **Retry shed loads** clears the disconnected
branches' remembered demand and restarts the normal restore delay. Voltage and
power protection remain active during the retry. A high control signal or Disable
opens every branch. An unpowered input waits without cycling contacts. Invalid
voltage/power readings open all branches. Saved contacts and demand estimates
survive reload; existing two-setting saves receive the new timing defaults.

## Validation

Automated tests cover real meter reverse flow versus local demand, accepted solar
energy, current limits, grid absence/loss, voltage range, darkness, disable, legacy
mode and save/item behavior. Shedder tests cover four real resistive branches,
priority order, hysteresis, configurable delays, oversized-load hold, retry and
old-save migration. The opt-in native client check is
`-Deln.controlPanelChecks=true`; it exercises cabled production devices, GUI
packets, live telemetry over baked housings and resource reload, and writes images
and `report.json` to the run directory's `control-panel-checks/` folder.

## Pad transformer primary fuse

The pad transformer's fuse now uses its stamped **primary amperes**, measured at
its primary tap. The previous code silently replaced that limit with the smaller
of the fuse rating and 125% of the installed transformer's nameplate current. A
transformer capacity overload could consequently consume a healthy 200 A fuse.

Primary fuse overcurrent and transformer capacity protection are now separate
trip reasons. Only actual primary overcurrent blows the cartridge. The existing
125% transformer capacity guard instead trips with **Transformer overload; primary
fuse intact**; reduce the load or install an adequately rated transformer, then
reset. Its timer cannot count toward a later primary fuse overcurrent event.
Pending protection time and fault type survive saving.

A nominal 145 A load at 240 V is about 34.8 kW, or 2.8 A at a 12.47 kV primary
before losses. It runs on the 50 kW core with a healthy 200 A primary fuse. A 25 kW
core trips its capacity guard without consuming that fuse. The cabinet GUI shows
primary amps, installed fuse amps and transformer capacity alongside secondary
readings. An already blown cartridge must still be replaced.

## ATS reset of linked generators

Both floor and wall ATS GUIs include **Reset linked generator**. The action uses
the existing case-sensitive sync channel in the same dimension and reaches only
currently loaded generators. A result line reports reset count, an already ready
generator, or no loaded match. Nothing is queued for a generator that loads later.

The command clears fault latches on faulted linked generators and lets normal Auto
operation restart them after the usual three-second engine delay and ATS voltage
checks. It preserves fuel, programmed voltage and Auto/Manual/Off mode. It does not
refuel or force an Off generator to run. A persistent overload/backfeed trips
again and stays latched; reset is a one-time user action, not an automatic retry
loop. Healthy generators and an active mains/backup feed remain uninterrupted.
The ATS can also retry its latched backup feed after a fault, with normal voltage
qualification and break-before-make contacts.

## Wide underground meter collision

The wide meter's second reserved cell now carries the cabinet's shallow shape in
its block state. Collision and selection work on remote clients without needing
the server's ghost-owner table. All four orientations retain the same 0.33-block
depth and the case's outer margin, leaving the space in front of the can clear.
Existing placed meters migrate their old cube placeholder when the cell is
loaded. The migration does not load chunks, replace the meter, disturb its
registers/wires, or change other multiblock ghost shapes.

## Acceptance results

The full Gradle build passes **1055 tests across 255 suites**, with no failures,
errors or skips. The native production-jar campaign passes nine checks, including
all four meter rotations, an old cube-ghost migration, ATS GUI reset and healthy
feed continuity, and both pad-transformer fuse cases. All six panel model assets
match the packaged jar and their generator is idempotent.

- [Full regression results](verification/release-3.0.73/full-suite.json)
- [Native acceptance results](verification/release-3.0.73/native-control-panels.json)
- [Packaging checks](verification/release-3.0.73/packaging.json)
- Front panels: [MPPT](verification/release-3.0.73/mppt-feeding.png), [hybrid](verification/release-3.0.73/hybrid.png), [load shedder](verification/release-3.0.73/shedder.png), [relay](verification/release-3.0.73/relay-trip.png), [wall MPPT](verification/release-3.0.73/wall-mppt.png).
- GUIs: [ATS reset](verification/release-3.0.73/ats-generator-reset.png), [145 A secondary load](verification/release-3.0.73/pad-primary-fuse.png), [core overload with intact fuse](verification/release-3.0.73/pad-overload-fuse-intact.png).

Regenerate the original vector panel meshes with `python3 tools/models/control_panels.py`.
