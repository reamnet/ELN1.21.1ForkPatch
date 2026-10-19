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
