# Electric kitchen appliances (3.0.15)

Five working appliances are in the Machines creative tab, with survival recipes
and compact GUIs. Connect a floor cable to any horizontal side. These are ordinary
resistive electrical loads; they do not add voltage converters to the network.
Match the supply voltage to the appliance's rating.

| Appliance | Supply / processing power | Input → output | Nominal time |
| --- | --- | --- | --- |
| Electric Microwave | 120 V / 1000 W | Smeltable food → cooked food | 6 s per item |
| Electric Toaster | 120 V / 800 W | Bread → Toasted Bread | 5 s per item |
| Flat Electric Cooktop | 240 V / 2000 W | Smeltable food → cooked food | 10 s per item |
| Electric Refrigerator | 120 V / 80 W | Water bucket → ice + empty bucket | 60 s once cold |
| Electric Coffee Maker | 120 V / 900 W | Cocoa beans + water bottle → Fresh Coffee | 10 s per drink |

Microwave and cooktop use the world's smelting recipes, restricted to edible
outputs; they cannot process ores. Put food in the left slot and collect the
output on the right. The coffee maker also needs a water bottle in its middle
slot. Cocoa beans stand in for coffee beans, so it needs no extra crop mod.
Fresh Coffee is drinkable and returns its glass bottle when consumed. Toasted
Bread is edible. Both outputs have their own item sprites.

The refrigerator has **27 food-storage slots**, a temperature display, a water
bucket input, an empty-bucket return slot and an ice output. It cools toward
4 C while powered, cycles its compressor at idle, and warms toward room
temperature during an outage. Ice-making waits until it is cold. Vanilla food
does not spoil; storage and ice-making are its gameplay functions.

All GUIs show the supply rating, current state, processing progress and power,
with an On/Off button. Work advances using actual electrical energy, pauses
when the supply is lost or below its usable voltage, and resumes when power
returns. A full output or missing water stops a new operation without consuming
its ingredients. Excess supply voltage opens the appliance's load until the
voltage is corrected. Progress and inventory survive world saves. Breaking a
machine drops its stored items through the usual node inventory handling.

Countertop machines can follow partial supporting surfaces, including slabs.
The refrigerator needs two blocks of free height.
