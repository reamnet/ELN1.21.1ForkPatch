# American underground residential service — 3.0.61

Use a single-phase **Pad Mounted Neighbourhood Transformer** for a residential
underground feed. Its green cabinet has a solid skirt, rounded top edges, a locked
cable compartment, primary elbows, secondary lugs, grounding lead and a concrete pad.
Switch it off to isolate the winding and open the door. Install the existing physical
transformer upgrade and fuse items; all secondary connections share that rating and fuse.

A typical installation is:

1. Run the insulated primary feeder to the pad transformer's primary connection.
2. Run its 240 V secondary through placed insulated cable and, when needed, a
   **120/240V Underground Service Handhole**. Place the handhole one block below
   ground level for its green lid to sit flush with grade. It joins four side entries
   and a bottom entry; protection remains upstream.
3. Use an **Underground Service Entrance Riser** for the short conduit entry on the
   building wall. It accepts ordinary placed cables rather than overhead spans.
4. Feed either the existing **North American Meter Base** or the new
   **Wide Underground Meter Socket**, then a service disconnect and breaker panel.

The pad's existing side connections remain compatible. Its new bottom cable entries
use Down for the primary, Up for the full secondary, and Left/Right for its two half
voltage branches (the usual ELN LRDU terminal labels). A nominal 240 V winding gives
120/120/240 V equivalents; 208 V gives 104/104/208 V. Every branch follows the actual
primary voltage proportionally. These are passive taps, not fixed-voltage regulators.

## Two-block-wide bottom-entry socket

The wide socket needs **two solid wall blocks behind it**. From its front, the
**utility input is underneath the left half**, and the **customer output is underneath
the right half**. Put ordinary wall-mounted cables directly below those entries,
against the same wall. Both cables may use the same color: the cabinet's insulating
barrier prevents those two adjacent downleads from joining directly. An external
cable route that joins the utility and customer feeds would still bypass the meter.

Both entries face down; the wide socket has no top/side or overhead-span connection.
Select 240 V for the usual full-service feeder. The existing compact socket retains
its top-in/bottom-out layout and outgoing side branches.

## Physical meter inserts

Insert an **Analog Glass Service Meter** or **Smart Digital Service Meter** in the
socket's inventory. Removing the insert opens the service. Both models now have
a stainless ring, glass shell, printed utility face, seal and mounting hardware;
the empty socket shows its jaws and insulators.

Each physical meter receives a serial number. Its import/export energy registers
travel with the item when removed, picked up and installed in another socket.
Fresh meters start at zero. Existing installed meters migrate their saved socket
registers when loaded. Unloaded time is not billed.

The analog meter uses five alternating-direction register pointers and a marked
disk calibrated to **Kh = 7.2 Wh per revolution**. Export reverses the disk without
subtracting from the separate imported-energy register. The powered smart LCD
cycles every five seconds through imported kWh, exported kWh, demand kW and utility
line volts. Its OPEN/SERVICE CONNECTED indication reflects the actual disconnect;
the line-powered display remains live when the customer side is disconnected.
The GUI provides the existing local disconnect; a remote-control API remains available
without claiming a utility communications network.

## Compatibility and rendering

The two older three-phase fused underground feeder cabinets are hidden from new
creative selection and crafting. Their saved IDs, placed blocks and old items remain
supported. The three-phase overhead feeder/riser poles remain available. Existing
single-phase underground equipment and compact sockets retain their IDs.

Cabinets, conduit, handhole, meter rings and printed faces use baked terrain meshes.
Only the glass, moving disk/pointers and live smart display require dynamic rendering.
No browser or media backend is added; EA Media remains a separate addon.

ELN represents residential service with scalar voltage equivalents, not an explicit
AC neutral/phase solver. The handhole splices one 240 V equivalent; downstream
service taps derive proportional 120 V branches while conserving power.

## Pole risers and removable streetlights (3.0.63)

**Single Phase Underground Feeder Pole** brings a buried primary cable up to a single overhead
conductor. The primary cable enters the left side of its base. Its separate secondary rack can
carry an upstream 120/240 V service without connecting that service to the primary.

**Single Phase Transformer Pole with Underground Service Riser** accepts the existing physical
transformer upgrades and fuse links. At nominal 12.47 kV its transformer supplies the selected
secondary voltage; primary sag or rise changes that output proportionally. Its added conduit
marks the underground secondary route. The bottom ports expose 240 V (Down) and 120 V branches
(Left/Right), while the ordinary primary and secondary overhead connections remain available.

Use a **Pole Streetlight Attachment** on an ordinary distribution pole, legacy utility pole or
69/230 kV pole/tower. Right-click the pole with the attachment to install it. Shift-right-click
with an empty hand reopens its controls. The controls provide on/off, dusk/night operation,
automatic or fixed 120/240 V supply selection, full horizontal rotation, height and tilt, and
removal. Removing it returns the physical attachment; picking up a pole carries its attachment
and settings with it.

The lamp draws 70 W at nominal voltage from the pole's **secondary only**. A pole with no local
transformer needs an externally fed secondary. Connecting only its primary never powers the lamp.
Steel transmission poles now also expose an independent secondary rack and bottom cable ports;
they do not silently convert their high voltage into lighting power. The lamp is a passive load,
so its brightness and consumption follow the supplied voltage. Static attachment geometry uses
the terrain mesh and Distant Horizons integration.

Dedicated poles with permanently built-in streetlights are vaulted from new crafting and creative
selection. Their saved IDs and existing placed blocks remain supported. For a new installation,
choose the pole you need and add the removable attachment instead.
