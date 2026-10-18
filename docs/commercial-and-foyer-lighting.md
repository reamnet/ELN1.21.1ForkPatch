# Commercial and foyer lights (3.0.15)

All 23 new lights are in the Lighting creative tab and have survival crafting recipes.
The electric fixtures use the ordinary lamp socket/supply circuit, without adding
converters to the grid. Existing block and item identities are unchanged.

| Fixture | Bulbs | Mounting |
| --- | ---: | --- |
| Thin Fluorescent Shop Light | 2 | Ceiling; 2.4-block-long slim strip |
| Suspended Factory Fluorescent Light | 4 | Ceiling; thick housing with one full block of suspension wires |
| Outdoor Motion Floodlight | 2 | Any vertical wall |
| Candle Arm Foyer Chandelier | 6 | Ceiling; curved brass arms and candle bulbs |
| Crystal Tier Foyer Chandelier | 6 | Ceiling; three descending crystal tiers |
| Wagon Wheel Foyer Chandelier | 8 | Ceiling; dark circular frame and suspension stays |
| Lantern Cage Foyer Chandelier | 4 | Ceiling; open rectangular lantern cage |
| Modern Sputnik Foyer Chandelier | 8 | Ceiling; brass radial arms and globe bulbs |
| Gasoline Construction Work Light | 2 | Floor; wheeled generator cart and raised twin floodlights |

## Bulbs and electricity

Every fixture accepts all existing bulb technologies, including fluorescent,
incandescent, carbon, farming, LED and halogen, at 12, 120 and 240 V. The fluorescent
strip appearance does not restrict which bulb item you can install. Fit identical
bulbs with the same remaining lifetime in a multi-bulb fixture; each installed
bulb consumes its normal rated power. Empty heads remain dark.

For cable-capable electric fixtures, insert one metre of cable in the cable slot,
then choose cable or wireless lamp-supply power in the GUI. Wireless mode uses
the saved lamp-supply channel. Match the supplied voltage to the bulb rating.
Bulbs still age and can burn out from excessive voltage.

The larger fixtures reserve clearance below their ceiling mounting. Leave two
blocks of headroom below the mounting block for the factory fixture and foyer
chandeliers, and lateral room for their wider housings. Cables connect at the
mounting canopy; the supplied suspension wires are part of the model.

## Motion floodlight

The GUI cycles between **Motion: night**, **Motion: all day**, **Always on** and
**Always off**. Automatic modes detect moving living entities within eight
blocks in front of the fixture, with solid blocks blocking the sensor's view.
The light stays on for 30 seconds after the last movement. A newly entering
entity also triggers it. Spectators and dead entities do not trigger it.

Night mode responds to global dusk/weather darkness, so the lamp's own light
cannot make it repeatedly switch itself off. When off, its bulb load is opened
and bulb life does not decrease. Sensor queries run at most four times a second.
The selected mode survives a world restart.

## Gasoline construction work light

This is a wheeled **block**: pick it up to relocate it. Its two-bucket tank uses
the same configured gasoline-compatible fluids as existing fuel generators.
Gasoline fluid/container items come from a companion mod, such as PneumaticCraft;
Electrical Age does not register its own gasoline fluid. Refuel by right-clicking
with a container holding at least one bucket, then use **Start engine** in the GUI.
The engine stops when fuel runs out or its output remains overloaded.

The generator has a **2000 W** nominal rating. Its output voltage automatically
matches the installed bulbs (12, 120 or 240 V), or 120 V with no bulbs. Ordinary
floor cables connect at the front/back. External equipment shares the generator's
power budget with the two floodlights; use matching voltage equipment. The engine
consumes fuel even at idle. The tank, bulbs and settings survive world saves;
picking up the block keeps the tank's fuel and drops its bulbs normally.

With the engine stopped, the same cable connection can supply external power to
the floodlights. **Wireless** mode disconnects the bulbs from the generator and
uses the entered lamp-supply channel instead; the engine cannot run in that mode.
The cart does not require an internal cable item. The GUI shows remaining fuel,
bulb voltage and engine controls. Unpowered lenses render dark.

The shared legacy generator voltage-source registration is also corrected in this
release: its electrical RHS process is added once, preventing a doubled output
voltage. The construction cart's undervoltage protection follows the actual
installed bulb voltage rather than assuming 240 V.

## Additional styles in the same release

Five more wall sconces: **Up Down Cylinder**, **Fabric Shade**, **Glass Globe**,
**Industrial Cage** and **Double Candle**. The cylinder has two bulbs and upward/
downward lighting; Double Candle also has two bulbs. The others have one.

Three additional table lamps: **Ceramic Drum**, **Green Banker** and
**Articulated Task**. These and the original Household Desk Lamp can sit on
slabs, stairs, fences, chests and other blocks with a central collision surface.
Their models follow that surface height. Air, plants without collision and
unsupported edges do not provide a mounting surface.

Three additional recessed designs: **Deep Baffle**, **Trimless Downlight** and
**Adjustable Gimbal**. Like the earlier recessed fixtures, these are wireless
lamp-supply lights, with one replaceable bulb and no exposed cable terminals.

Three more outdoor fixtures: **Garden Bollard**, **Gooseneck Barn Light** and
**Traditional Lantern Post**. Each has one replaceable bulb and cable/wireless
power options. The lantern post reserves two blocks above its base.

All ceiling lamp sockets now accept both top and bottom half slabs, including
legacy sockets and suspended fixtures. Their mounting position follows the
underside of the slab. Thin fixtures that enter the slab's unused half remain
directly clickable, so their normal bulb/channel GUI is accessible. Removing
the supporting block still removes the fixture normally.

Together with the initial nine fixtures above, this release adds **23 new
lights**. All have original models and survival recipes.
