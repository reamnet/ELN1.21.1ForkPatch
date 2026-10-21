# Lighting and cords in 3.0.30

This update adds these fixtures, all accepting the existing bulb technologies and
wired, wireless Lamp Supply or appliance-cord power:

- Large Area Floodlight; Quad Head, Round Canopy and Offset Twin Arm Parking Lot Lights.
- Round Niche, Linear Wall, Square Recessed, Corner Wash and Step Marker Pool Lights.
- Patio Festoon Party Lights, Electric Poolside Tiki Light, Multicolor Party Wash
  Light, Poolside Globe String Lights and Poolside Party Lantern.

Pool fittings can mount on walls, floors or steps. Source water remains in the
fixture cell and in temporary underwater light cells; removing a fitting or letting
its projected light expire restores water. The multicolor fixtures have colored
lenses; Minecraft's ordinary block lighting is not RGB lighting.

Two separate fairy-string items are available: **White Fairy Lights on Green Wire**
and **Multicolor Fairy Lights on Green Wire**. Click the first supporting surface,
then another surface 0.25–8 blocks away. The suspended string sags and has lamps
roughly every 10 cm. Sneak-click starts a new first anchor. Open the fixture GUI
for its Lamp Supply channel and nominal voltage, or connect a 120/240 V cord.
The hanging string and its power cord are separate: its power cord follows the
terrain, while the light string hangs between its selected anchors.

Christmas Trees, Holiday Candles and the original String Lights now have a channel
and voltage GUI. They accept both 120 and 240 V cords; inserting a cord chooses its
voltage automatically. Wireless lighting consumes power from the named supply.
Holiday Candles can sit on slabs and other central support shapes, including
window sills. The small candle footprint follows the actual support height.

Large fixtures have a small mounting-plate selection target. Their reservation
cells no longer catch the player's crosshair or block walking. Existing lamp
clearance cells upgrade when their chunks load.

## Outlets and portable appliances

Use a matching appliance cord on an outlet, then on the appliance. The Flat Electric
Cooktop accepts the **240 V Appliance Cord** and a **240 V Wall Outlet**.

Sneak-click an occupied outlet with an empty hand or a cord to unplug the nearest
socket. The outlet GUI also has individual **Unplug** buttons and **Unplug all cords**.
Unplugging preserves the appliance and its contents; a paid cord returns to the
player, or drops by the appliance when disconnected through the GUI. Breaking an
outlet removes its connected cords and plugs, including links restored later from
a carried item. Unloading chunks alone preserves valid connections.

Sneak-click a connected portable appliance with an empty hand to pick it up. Place
that held item again within the cord's eight-block reach. It keeps its inventory,
settings and outlet reservation. Moving beyond the reach disconnects it. The
cooktop, small kitchen appliances, box fans and table lamps support this lifecycle;
large fixed machines remain fixed.

New wall outlets placed on a full solid block recess into that wall cell, preserving
its original block state. Their thin visible face projects out of the wall, leaving
the block in front available for an appliance. Removing the outlet restores the
wall. Existing surface-mounted outlets remain usable; place a new outlet on a wall
to use the recessed mounting. Power strips retain their surface mounting.

## Utility and rendering changes

HV crossarms and LV racks angle independently with their connected spans. A 90°
corner produces a 45° crossarm; a branch on the other voltage level does not steer
it. A single-phase branch uses the A/B/C insulator you aim at, in either click
order. Connections between two three-phase poles still join all three legs. A
single-phase run cannot bridge two different HV legs. The multimeter reports
every HV leg and LV service circuit without changing the selected GUI terminal.
The new **Three Phase Pole with Fused Single Phase Tap** passes A/B/C through
and protects only its selectable single-phase branch with a physical fuse.

All pole streetlight variants have full-circle yaw, height and tilt controls. Hold
Shift for one-degree adjustments. Standalone street/parking fixtures have swivel
head controls in their bulb GUI. Their emitted light follows the same transforms.

The electrical panel has a per-circuit dusk/night option, alongside its existing
wireless switch control. Manual breaker state and protection still apply. Its
120 V banks use fixed 2:1 taps from the actual 240 V feeder: 228 V produces about
114 V; 252 V produces about 126 V, allowing ordinary winding/cable losses.

Transparent-node entity IDs no longer become light opacity. Invisible multiblock
reservations pass skylight, and placed models sample both their own and neighbouring
light. Directly wired bulbs round to the nearest light level, avoiding a whole-level
brightness loss for tiny wire drops while retaining real undervoltage dimming.

Grid spans are culled using their complete geometry rather than only their owning
chunk section. Recorded model topology is reused, temporary vertex allocation is
reduced, and block renderers leave shared-buffer flushing to Minecraft. This
reduces repeated work in turbine/generator areas. Render display lists are released
when their owning grid or fairy-string renderer unloads.

Update the client and server together: this version includes network-format changes.

## Workshop and industrial machine cords

The existing 120 V NEMA 5-20 and 240 V NEMA 6-20 cords now also work with matching battery chargers and other workshop consumers. Separate, color coded machine cords and two-socket outlets cover 24 V, 48 V, 200 V (the existing wire machines/fabricator), 480 V, 800 V and 3.2 kV. These industrial couplers are fictional game connectors; they do not use the household NEMA models. Craft them from a normal cord plus rubber and the matching dye, or a normal outlet plus an iron plate and dye: purple, light gray, green, red, black and yellow respectively.

Supported consumers include macerators, compressors, plate machines, magnetizers, arc furnaces, electric furnaces, wire rollers/insulators/combiners, fabricators, egg incubators, active thermal dissipators, evaporative coolers, auto miners, teleporters, turrets, radio transmitters, lamp supplies, battery chargers and shaft motors. Furnaces match the installed heating element's nominal voltage; install that element before attaching a cord. Existing fixed wiring remains available when unplugged. A plugged cord isolates only the power inlet, preserving control signals, thermal connections and machine outputs. Industrial motors also connect their electrical return through the cord.

Select a matching outlet with the cord and then use it on the machine. Sneak-use a cord on the machine, or the occupied outlet socket, to disconnect. Heavy workshop machines remain fixed in place; the pickup/reposition action is for portable lamps and household appliances. Handheld battery tools still recharge through the cord-powered battery charger. Outlets pass through their supplied voltage and do not convert it; choose the proper upstream transformer/converter, cable and protection for the load.

The red-handle service disconnect retains an open enclosure. Its external handle swings up/down on an outboard spindle and its internal blades hinge outward from their bottom contacts. Standard breaker cable meshes now stop at the casing instead of drawing thick cable insulation through its top, including the Copper 1/0 AWG Cable 1000V.

## Mini fridge

The Electric Mini Fridge is a one-block, 120 V / 55 W refrigerator with nine food-storage slots and an ice compartment. It cools while powered, warms during an outage, and freezes a water bucket into ice once cold, returning the bucket. Its GUI shows the temperature and power state. It accepts floor wiring or a 120 V appliance cord, has the refrigerator compressor sound, and supports portable cord pickup with its contents retained.

## Server performance

Overhead spans share cached climate samples at each pole endpoint for one second, including multiple phases and adjacent spans. Conductor cooling uses cached, immutable geometry. Heating energy and melting checks still advance every thermal step. Electrical span resistance is refreshed when its drift exceeds 0.01% of the last stamp; large temperature changes update immediately on the next thermal step. This bounds the electrical/thermal coupling error while avoiding repeated matrix factorizations for minute temperature drift. It does not delay grid voltage/current changes or suppress faults.

## Electrical deck equipment

- **Deck Post Cap Light:** a small lantern cap for fence posts and rail tops, with a replaceable bulb.
- **Deck Step Riser Light:** a shallow, hooded light for stair risers, aimed downward.
- **Tropical Patio Ceiling Fan:** five broad leaf-shaped blades, replaceable light bulb, 38 W fan motor, separate fan controls and smooth spin-up/coast-down.
- **Electric Patio Heater:** a tall radiant heater with thermostat, 120 V / 1500 W input, power indicator and operating sound.
- **Patio Electric Grill:** a 120 V / 1800 W cooking appliance using the world's food-smelting recipes, with input/output slots, controls, hot-element visuals and operating sound.

The lights and fan accept all existing bulb technologies and can use wire, wireless Lamp Supply or a correctly rated cord. The heater and grill use fixed cable or 120 V appliance cords; the heater retains the simulated thermal/room model. The grill and patio heater support cord pickup/reposition. All have survival recipes.

The **Warm White Deck Globe String Lights** and **Multicolor Deck Globe String Lights** use the same two-anchor placement as the miniature fairy strings. They have larger round hanging bulbs, black wire, roughly 50 cm bulb spacing, sag, and a dark unpowered appearance. Craft either version from its corresponding miniature fairy string, a glass bottle and black dye. They accept 120/240 V cord power or a wireless Lamp Supply channel.
