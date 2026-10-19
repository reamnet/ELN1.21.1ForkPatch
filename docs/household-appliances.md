# Household appliances and climate control

Version 3.0.21 adds three working countertop appliances. Like the original
kitchen machines, these need a floor cable and rated electrical supply; their
GUIs provide an on/off button, work progress and output slot. Work is paid for
with electrical energy and pauses during an outage or when output is blocked.

| Appliance | Supply | Recipe |
| --- | --- | --- |
| Electric Kettle | 120 V, 1500 W | Water bottle → drinkable Hot Water |
| Countertop Blender | 120 V, 400 W | Apple, melon slice or sweet berries + water bottle → Fruit Smoothie |
| Electric Rice Cooker | 120 V, 700 W | Wheat or any item tagged `c:crops/rice` + water bottle → Steamed Grains; returns the empty bottle |

Hot Water and Fruit Smoothie return a glass bottle when drunk. All new machines
have survival crafting recipes and appear with the other machines in Creative.

## Heating and air conditioning

The Portable Electric Space Heater and Modern, Farmhouse and Corner Fireplace
TV Stands draw 1500 W at 120 V when heating is requested. The TV stands have flat
tops. The Window Air Conditioner draws 1000 W and the Wall-mounted Mini-split
Air Conditioner draws 900 W at 120 V when cooling is requested.

Their small GUIs show air temperature, target, electrical power, heat exchanger
temperature and operating state. Set the thermostat between 16 and 32 °C; a
0.5 °C hysteresis band prevents rapid cycling. The on/off button disconnects the
active load. Overvoltage and extreme coil temperatures open the input until safe.
Settings survive saves, item pickup and configuration-tool copying.

Heaters use floor cables. Both AC variants mount against a solid exterior wall
and use cables on that wall face. The indoor coil exchanges heat with the air in
front of the unit; the exhaust rejects heat two blocks behind it. Keep the exhaust
outside the cooled room. Cooling moves three watts of heat per electrical watt
and exhausts four watts, conserving the electrical energy as added heat.

These integrate with Electrical Age's existing enclosed-room thermal simulation.
The GUI reports whether a room was detected; in open air the thermostat uses
local biome temperature. This does not alter Minecraft weather or biome values.

## Mini oven and appliance sounds (3.0.22)

The **Electric Mini Oven** is a compact countertop oven with a glass door, racks,
controls and glowing heating elements. It draws **1300 W at 120 V**. Put a food
with a furnace cooking recipe in its input slot; each item takes eight seconds at
rated voltage. It uses the same inventory GUI, on/off button, output protection
and partial-height supporting surfaces as the other kitchen machines.

All nine kitchen appliances now have local operating sounds: microwave hum,
toaster buzz and pop, cooktop sizzle, fridge compressor, coffee brewing, kettle
boiling, blender motor, rice-cooker steam and oven fan. Completed jobs play a
quiet chime (the toaster pops instead). Both ceiling fans have motor/airflow
sounds that follow their motor power independently of the light switch. The
heaters, window AC and wall mini-split also sound while actually heating or
cooling. Switching off, losing power or satisfying a thermostat silences the
corresponding loop. Removing or unloading the block disposes its sound.

The 16 new mono OGG samples are original procedural synthesis, with no external
recordings. `tools/audio/generate_appliance_sounds.py` can regenerate them with
Python 3 and ffmpeg; normal mod compilation uses the checked-in assets.

## Microwaving batteries

The microwave accepts ELN portable batteries, packs, larger UPS cells and block
batteries, plus other mods' items tagged `c:batteries`. Capacitors and arbitrary
energy tools are excluded. Batteries are a separate hazardous job, rather than
a food recipe, and a full output slot does not prevent heating.

At rated power a battery starts hissing after about six seconds (6000 J), then
explodes after at least two further seconds of powered warning and 8000 J total.
The GUI reports heating and the imminent explosion. An outage or the off button
pauses both heating and the fuse; partial heating and warning time survive saves.
Replacing or removing the input resets the heating cycle. The battery's existing
charge does not provide free microwave power.

The explosion consumes one battery and destroys the microwave. It does not start
fires. Nearby terrain damage follows the existing
`gameplay.hazards.explosionsEnabled` setting; with that setting off, the blast still
has its sound, particles and normal entity effects, but leaves nearby blocks
intact.

Run `./gradlew test` for the automated regressions. The opt-in native campaign
`-Deln.applianceSoundChecks=true` runs with `runClient` and writes
`run/client/appliance-audio-checks.json`, including loaded sound events, real electrical
cooking, on/off and power-loss behavior, the mini oven GUI and a battery blast.
On a headless machine, `ALSOFT_DRIVERS=null` allows the real OpenAL engine to load
and play the samples without an audio device.
