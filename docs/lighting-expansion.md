# Lighting and backup expansion (3.0.18)

This update adds 30 bulb-based fixtures, four wireless wall controls, three UPS
battery sizes and two utility-pole street lights. Existing item identities and
saved installations are preserved.

## Movement around fixtures (3.0.19)

Decorative lights reserve space for their models without creating solid invisible
cubes. You can walk through the empty space around chandeliers, long strips and
other decorative fittings. Normal machine and camouflage collision stays solid.
Already-placed lamps update their clearance cells automatically as their chunks
load, preserving bulbs, channels and wiring. Replacing the lights is unnecessary.

## Fixtures

* **Home:** Window Candle Light and a two-block Standing Drum Floor Lamp.
* **LED strips:** Under Cabinet, Recessed Channel, Indirect Cove, Surface Linear
  and Flexible Neon. The surface strip spans two blocks.
* **Grand chandeliers:** Halo Ring, Crystal Cascade, Sculptural Branch, Spiral
  Rain and Empire. Allow five blocks of width and up to six blocks below the
  ceiling for these large, multi-block fittings.
* **Heavy duty:** Stadium Wall Floodlight, Armored Oval Bulkhead, Industrial Wall
  Pack, Rugged Outdoor Canopy and Industrial Yard Area Light.
* **Offshore:** Caged Wellglass, Sealed Linear and Platform Floodlight.
* **Standalone street lights:** Modern LED and Boulevard Lantern.
* **Christmas:** Warm White Fairy, Multicolor String, Icicle and Star Wall lights.
* **Halloween:** Pumpkin String, Ghost String, Bat Wall and Jack O Lantern Table
  lights. Seasonal recipes are available throughout the year.

Right-click with bulbs to fill a fixture, or open its GUI. Every new fixture
accepts all existing bulb technologies and voltages. Multiple bulbs must be the
same item, and each consumes its own rated power. Fixtures are crafted without
bulbs or cable. The LED-strip names describe their housing; they still use the
mod's replaceable bulbs.

Use the existing Lamp Supply channel for wireless power. For wired power, insert
cable and select the wired mode in the fixture GUI; connect same-plane cable and
match the bulb voltage. Ceiling models retain the existing slab mounting rules,
and the candle and jack-o'-lantern use the existing table/partial-block support.
String lights mount horizontally on walls and reserve their neighboring spans.
Off-state lenses become dark instead of continuing to glow.

## Wireless wall controls

Paddle Switch, Toggle Switch, Paddle Button and Toggle Button are wall-mounted
controls. Right-click to operate; use a wrench to open their channel GUI. Switches
stay on or off. Buttons send a brief pulse, and another press extends that pulse.
They use the existing wireless transmitter range and channel system. Match a
Lamp Supply's control channel directly (select its highest-signal mode for
switches, or toggle mode for buttons), or use a Wireless Signal Receiver to
operate other signal-controlled equipment. Buttons do not reload in a held state,
and unloaded controls are removed from the transmitter registry.

## Utility pole variants

The Cobrahead and Twin LED poles use the same replaceable Mains Step-down Unit,
120/240 V output, dusk mode, height and rotation controls as the original pole.
Install the unit by right-clicking with it. Their lamps draw 70 W and 120 W
respectively; the original pole remains 60 W. The two standalone street-light
fixtures instead accept replaceable bulbs and wireless power.

## Larger UPS cells

| Cell | Energy per cell | Charge / discharge per cell |
| --- | --- | --- |
| AGM | 0.267 kWh | 500 / 1,000 W |
| LiFePO4 | 1 kWh | 1,000 / 2,000 W |
| Industrial module | 4 kWh | 2,000 / 4,000 W |

Cells are crafted empty. Install complete racks of 24 or 48 batteries; sizes may
be mixed. Charge belongs to each removable item, so changing cells neither creates
nor discards stored energy. Larger cells increase runtime, while the UPS output
remains limited to 10 kW and its programmed voltage.

## Standby generator effects

A running or cooling-down standby generator plays the existing engine sound and
emits smoke from its top exhaust. The sound grows slightly with load. Both effects
stop when the engine stops. Exhaust follows the cabinet's orientation, and the
particles are generated on the client.

The ATS also plays a contactor clack whenever a feed physically opens or closes,
including the transfer back to mains. Steady operation and chunk unloading are
silent; the electrical transfer delays are unchanged.

## Developer checks

Run `./gradlew test` for circuit, battery, placement and model checks. For the
interactive client check, add `-Deln.lightingExpansionChecks=true` to
`JAVA_TOOL_OPTIONS` and run `./gradlew runClient`. It creates an isolated gallery
world, checks wired/wireless lighting and ATS sound events, captures the models,
reopens the saved world, writes `run/client/lighting-expansion-checks.json` and exits.

Use `-Deln.lampClearanceChecks=true` with `runClient` for the focused collision
regression. It simulates old solid reservations, checks server/client collision
and real player movement, reloads an old-state world and checks fixture removal.

## Adjustable heads and ceiling fans (3.0.21)

Warehouse, stadium and offshore floodlights, the adjustable gimbal recessed lamp,
and the gasoline construction cart now have **Pan**, **Tilt** and **Reset aim**
controls. On a twin fixture, select **Head 1/2** to adjust each head independently.
The housing and emitted beam rotate together. Settings survive world saves and
can be copied with the configuration tool.

Five more compact dot styles are available: pinhole, bezel, eyeball, deep baffle
and twin dot. The Twin Adjustable Eave Floodlight has two independent swivel
heads and mounts below ceilings or on walls. All accept every bulb technology;
the miniature dots use wireless supply power, while the eave fixture also accepts
wired power. Slab mounting and passable fixture clearance apply to these lights.

The Classic Four Blade and Modern Three Blade Ceiling Fans accept wired or
wireless lamp-supply power. Their **Fan: On/Off** button controls the motor
independently of the lamp. Motors draw 55 W and 28 W respectively at the installed
bulb's rated voltage (120 V without a bulb). Supply loss stops the blades. Install
a cable in the fixture, including in wireless mode, just as for other lamp sockets.

Wireless paddle and toggle controls now snap in roughly a tenth of a second,
play a quiet click on operation, and show a small green marker when on. Momentary
buttons click again when released automatically; loading a saved switch is silent.

Both ceiling-fan styles have quiet motor and airflow loops as of 3.0.22. Their
sound follows actual motor power and stops when the motor is disabled, even if
the light remains on. Removing or unloading a fan releases its sound instance.

As of 3.0.23, ceiling fans spin up and coast down gradually; their motor sound
follows rotor speed through shutdown. See [fans and appliance cords](fans-and-appliance-cords.md)
for the new box and industrial fans and the flexible cord prototype.
