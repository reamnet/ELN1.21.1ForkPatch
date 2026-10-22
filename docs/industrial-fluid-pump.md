# Industrial fluid pump

The **480 V Industrial Fluid Pump** moves **40 buckets per second at 480 V**
(40,000 mB/s, or 2,000 mB per game tick at 20 TPS). It draws 4 kW at nominal
voltage. Its passive motor load avoids introducing another voltage-regulating
converter into the grid.

Actual supply voltage changes flow proportionally: 432 V gives 36 B/s and 528 V
gives 44 B/s. Electrical consumption follows the resistive motor model, so those
voltages draw 3.24 kW and 4.84 kW respectively. The pump stops outside its
48–720 V operating band. At less than 20 server TPS, real-time delivery falls
with the simulation's tick rate.

Use a fixed power cable at floor level on a horizontal side, or a **480 V
Industrial Cord**. Right-click to open the GUI, switch the motor on/off, choose
an intake face, and inspect voltage, consumption, actual flow and stored fluid.
The blue port is the selected intake; all orange ports are outputs. The motor
makes a looping pump sound while running, with pitch following voltage.

Place the intake directly against one of:

- A valid infinite water source, such as a supported 2×2 source pool. The pump
  supplies renewable water only when the world's source-conversion game rule
  and NeoForge source-creation event permit it.
- A finite bucket-pickup source block. The source is consumed; lava and isolated
  water are never silently treated as infinite. Flowing liquid is not a source.
- A neighboring fluid tank or pipe exposing a standard NeoForge fluid capability.
  Other mods' fluids are supported without a hard dependency on those mods.

There are separate 40-bucket raw-input and pumped-output buffers. Pipes can
insert at the intake and extract at every other face. Raw fluid cannot reach
an output without electrical work. The pump also pushes stored output into
adjacent accepting tanks/pipes. It does not mix different fluids; empty existing
buffers before switching liquids. Buffers and settings survive saves and item
pickup, but electrical work credit is never saved or accumulated offline.

Ender IO conduits use these standard fluid capabilities. Configure an extraction
connection against an orange output, with extraction enabled and the appropriate
redstone mode, and configure insertion at the destination tank. Conduit tiers
have their own transfer limits; choose a tier with at least 2,000 mB/t available
throughput for the pump's full nominal flow. Several consumers can share output. In the live Ender IO 8.2.12-beta check,
a single Ender Fluid Conduit path delivered 8 buckets every five ticks (32 B/s);
two insertion destinations on that network provided enough combined throughput
for the pump's nominal and slightly overvolted flow. A direct accepting tank or
another sufficiently fast standard pipe also works.
The pump cannot force additional capacity through a slower pipe or full reactor
input. Its intake never scans a lake or loads remote chunks.

Crafting uses four Iron Plates, two buckets, two Electrical Motors and a Copper
Cable. The recipe appears in the normal recipe system and ELN item registry.
