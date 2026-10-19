# Fans and experimental appliance cords (3.0.23)

Both ceiling fans now accelerate over about five seconds and coast for about ten
seconds after switching off or losing power. Their blade animation and sound
follow the remaining rotor speed. The light still switches independently. Saved
worlds preserve the rotor's speed, so a coasting fan resumes coasting after reload.

## New fans

| Fan | Supply | Full-speed power | Startup / coast time |
| --- | --- | --- | --- |
| Classic Box Fan | 120 V | 45 W | 4 / 8 seconds |
| Metal Shop Box Fan | 120 V | 85 W | 5 / 10 seconds |
| Compact Desktop Box Fan | 120 V | 20 W | 3 / 6 seconds |
| Industrial Five Block Fan | 240 V | 1500 W | 12 / 20 seconds |

Each has a speed setting from 25 to 100%, an on/off control, power readings and a
moving rotor. They do not require bulbs. Box fans can use a floor cable or the
new flexible appliance cord. The industrial fan uses a floor cable; route it
behind the frame to keep the blade space clear.

The industrial fan is nearly five blocks wide and five blocks tall. Reserve a
clear vertical area for its frame and blades. Its extra occupied cells reserve
placement space without creating invisible solid movement barriers. Living
entities touching the rotating blade disk take damage: **five hearts per second
at full speed before armor and other normal damage reductions**. Damage is
applied twice per second and falls with the square of rotor speed. Switching off
cuts electrical demand immediately, but coasting blades remain dangerous until
nearly stopped. Standing beside or behind the blade plane does not inflict damage.

## Cord prototype

Craft an **Experimental Wall Outlet** and a **Flexible Appliance Cord**. The outlet
mounts on a wall and connects to cables on that wall face. Supply it with the
voltage your appliance needs: it passes through grid voltage and has a 20 A
breaker; it does not convert voltage. One outlet accepts one cord.

1. Use the cord on the outlet. The loose end follows your hand, with the cord
   draped down toward the ground.
2. Use it on a box fan or a small 120 V kitchen appliance within **eight blocks**.
   Survival mode consumes one cord. The cord is visible along the ground and
   supplies the appliance from that outlet.
3. **Sneak-right-click the plugged appliance with an empty main hand** to pick it
   up. Its plug reservation, inventory and paid processing progress travel with
   it. The loose cord follows the carried appliance. It draws no power while
   carried.
4. Place the appliance again within reach to reconnect automatically. Moving it
   beyond reach leaves it unpowered until it is moved back or unplugged.
5. To unplug, sneak-use a cord item on the appliance. A cord originally consumed
   in Survival is returned exactly once. Breaking a plugged appliance returns
   its cord separately through normal block-drop rules.

Supported kitchen appliances: microwave, toaster, coffee maker, kettle, blender,
rice cooker and mini oven. The refrigerator, 240 V cooktop, ceiling fans and
industrial fan are outside this first portable-cord prototype. A fan carried for
several seconds loses rotor speed while carried. Microwaved batteries do not gain
heat or fuse time while carried.

A plugged appliance uses its cord input instead of its ordinary floor cable
ports. This prevents a cord from joining two independently powered feeds through
the same appliance. The conductor has real resistance and the outlet's current
reading includes the attached load. There is no wireless energy source hidden
behind the cord.

The outlet GUI shows voltage/current and provides **Reset breaker** and
**Release lost cord**. An overload or supply over 264 V trips the outlet locally.
Release lost cord clears a reservation if the carried item was lost; an old link
will then need to be unplugged and connected again. Replacing an outlet does not
silently connect cords belonging to the old outlet.

This is an initial terrain-following cord prototype. It uses a loose rendered
curve with no cord blocks to place. Appliances move by pickup and placement;
continuous dragging and a fully simulated rope with obstacle routing are not
implemented. Use clear routes around walls and keep the endpoints within reach.

## Verification

`./gradlew test` exercises rotor timing, coasting, saved speeds, blade geometry,
cord anchors and geometry, models and stable registration IDs. The opt-in client
campaign `-Deln.fanCordChecks=true` uses production wired/wireless power, actual
plug/pickup interactions, real entity damage and a saved-world restart. Results
are written to `run/client/fan-cord-checks.json`.
