# Lamp and appliance cords (3.0.24)

Cords now work with all lamp families and kitchen appliances, heaters, fireplace
TV stands, window/mini-split air conditioners, floor and ceiling fans, and the
UPS input. This includes recessed dots and other fixtures that previously only
accepted wireless lamp power. Install bulbs matching the outlet voltage. A 12 V
bulb still needs a 12 V supply; these outlets do not convert voltage.

| Outlet | Cord / plug | Nominal voltage | Breaker |
| --- | --- | --- | --- |
| Experimental Wall Outlet (existing item) | Flexible Appliance Cord / NEMA 5-20P | 120 V | 20 A |
| 240 V Wall Outlet | 240 V Appliance Cord / NEMA 6-20P | 240 V | 20 A |

The receptacles have distinct 5-20R and 6-20R blade patterns. Plugs have visible
molded bodies, finger grips, grounding pins, strain reliefs and voltage badges.
The 5-20 neutral blade is horizontal; the 6-20 uses the opposite blade pattern.
The 120 V parts use a blue badge; 240 V uses orange.

Use a matching cord on an outlet, then on a lamp or appliance within eight blocks.
One outlet accepts one cord. The cord follows the ground and has real resistance.
A plugged device disconnects its ordinary power cable input. Utility poles accept
cord power on their lamp's low-voltage branch: the pole's converter and base
output connection are isolated while corded; its overhead line remains separate.
The gasoline work light cannot start its engine while plugged in.

The outlet GUI selects **Cable power** or **Wireless lamp supply**. In wireless
mode, enter the Lamp Supply's power channel. The supply must be loaded, in range,
switched on and fed with the matching voltage. The actual appliance current is
drawn from that supply through the outlet's breaker. The outlet does not create
power, copy a voltage into a new source, or repeatedly rebuild the circuit while
its supply stays the same. Wireless mode disconnects the outlet's wall cable
ports; there is no automatic connection between the wired and wireless feeds.

More than 20 A for a quarter second, or more than 120% of the outlet's nominal
voltage, trips its breaker. Remove the overload and use **Reset breaker**. Normal
full-load ratings are 2400 W at 120 V and 4800 W at 240 V, with cord losses reducing
power reaching the load. Larger appliances can therefore trip these outlets at
high demand; use their original cable connections for loads exceeding 20 A.

Sneak-use a cord on a plugged device to unplug it. Survival cords are returned
once, using their correct voltage variant. Small kitchen appliances, box fans,
table lamps and the portable space heater can be picked up with an empty hand
while sneaking, then placed within reach again. Their contents and cord link
travel with the item. Larger and fixed fixtures accept cords while retaining
normal placement and breaking behavior.

Existing outlet IDs, cords and saved reservations remain valid as 120 V parts.
Existing worlds with an outlet fed above 144 V must change that outlet and cord
to the new 240 V variants. Craft either 240 V part from its 120 V counterpart and
orange dye. Save and reload preserve outlet mode, channel, breaker and cord links.

The cord remains a prototype rendered curve. It does not physically route around
walls or provide continuous dragging; use pickup and placement on a clear route.

# Industrial ceiling fan

The **Industrial Five Block Ceiling Fan** has six metal blades spanning five
blocks. Mount it on a ceiling with clear space around its rotor. It accepts all
bulb technologies, wireless lamp power, a matching cord or an ordinary cable.
The motor uses 300 W, nominally at 120 V without a bulb. Installing a bulb sets
the motor's nominal voltage to that bulb's voltage. The GUI switches fan and
lighting independently. Startup takes about five seconds; shutdown coasts for
about ten seconds. Animation and fan noise follow rotor speed.

Living entities touching the thin horizontal blade disk take **5 damage per
second (2½ hearts)** at full speed before normal damage reductions. Damage falls
with rotor speed squared and continues during coasting. Walking below the blade
plane is safe. Its extra cells reserve placement clearance without creating
solid invisible blocks.

# Validation

`./gradlew test` covers cord ratings, saved anchors, registration, plug and fan
meshes, blade contact geometry, and the existing lighting and simulator suites.
The opt-in native client campaign `-Deln.universalCordChecks=true` exercises the
registered lamp/appliance families, actual wireless current draw, source unload,
local overload protection, table-lamp pickup, fan damage and a saved-world restart.
Results are written to `run/client/universal-cord-checks.json`.
