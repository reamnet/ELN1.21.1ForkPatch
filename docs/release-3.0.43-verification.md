# 3.0.43: laundry, cold food storage and fusion matter production

Adds a water-fed 120 V / 800 W repair washer, a 240 V / 6000 W dryer, and a
30 A dryer cord/outlet. Wet equipment retains the complete repaired original
item but is unusable until dried. Both appliances also accept fixed cables.

Both fridge sizes preserve food while cold, with optional integrations for
Spoiled, Freshness and FFFoodSpoilageAndPreservation. The new 6 × 2 × 2 Fusion
Matter Reactor derives block values using an isolated, MIT-attributed copy of
ProjectE's graph calculator. It uses 100–500 kW over 10–30 nominal minutes per
block. ProjectE is not a runtime dependency. New stationary model bodies and
previews use the existing baked mesh paths.

See [operation, balance and datapack configuration](laundry-and-fusion-reactor.md).

## Verification

The full unit suite passes **846 tests across 207 suites**, with no failures,
errors or skipped tests. See the [unit result summary](verification/release-3.0.43/units.json).
The [native report](verification/release-3.0.43/native.json) records the live checks.
Screenshots: [laundry](verification/release-3.0.43/laundry.png),
[reactor](verification/release-3.0.43/reactor.png), and
[reactor GUI](verification/release-3.0.43/reactor-gui.png).

The unit suite covers wet-item repair and component retention, water capability
simulation/execution, liquid rejection, connector ratings, 24-cell multiblock
occupancy, energy accounting, saved work/water, boot before value mapping,
ProjectE recipe propagation and the power/duration curve. Existing connector
regressions now allow multiple appropriate amperage ratings at one voltage,
while continuing to reject wrong-voltage connectors.

The opt-in native check runs with:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.laundryFusionChecks=true" ./gradlew --no-daemon --max-workers=2 runClient
```

Install the three spoilage mods in the development client's `mods` directory for
this opt-in integration fixture. Production does not require them. The fixture
uses real placed appliances, NeoForge fluid capabilities and live simulated
power. It verifies rated wattage, preserved equipment data, water consumption,
save/read progress, outage behavior, output production and 30 A cord operation.
It also checks cold storage with all three spoilage mods installed and checks
that all three new bodies enter the terrain mesh path. All twelve static item
preview cases (three machines in four display contexts) use baked models. The
fixture verifies the reactor GUI and a saved cold-storage interval for Freshness.

Long production cycles in the native test start near completion using explicit
saved energy credit. This tests completion and persistence without claiming a
full 30-minute soak test. Separate real-time intervals verify electrical draw,
progress and pauses. Unit tests verify the full energy totals.

Native integration versions: Spoiled 6.3.0, Freshness 1.0,
FFFoodSpoilageAndPreservation 0.13.1 for NeoForge 1.21.1. The rendering check also
runs with Sodium 0.8.13, Iris 1.8.14-beta.1 and BSL 10.1.8. Ender IO itself is not
part of this new fixture: compatibility is through the same standard NeoForge
fluid capability previously verified with Ender IO for the industrial pump.

The release is source-only; compile `ElectricalAge-3.0.43.jar` using the
[Ubuntu instructions](building-on-ubuntu-24.04.md) and attach it separately.
