# Outdoor lights and compact UPS

Electrical Age 3.0.67 adds Shoebox, Twin Wide Area and Three Arm parking lot lights, plus single and dual Gumdrop Sidewalk Lights. They use the existing lamp GUI, bulbs, wireless lamp supplies, hard wiring and matching 120/240 V cords. Parking heads retain individual aiming controls. Place these on a floor with room above them.

## Four Cell Plug-in UPS

Find **Four Cell Plug-in UPS** under power electronics. Open its GUI and install up to four portable batteries or larger UPS cells. Each installed cell contributes its actual stored energy, capacity and charge/discharge rating; a complete rack is not required. Empty batteries charge from the input. Removing a battery preserves its charge.

Select **120** or **240** as the output voltage. The four front sockets then accept the corresponding existing appliance cords (NEMA 5-20P or 6-20P). Select an outlet with a cord, then select the UPS to feed its input. Select an unused UPS socket with another cord, then select a lamp or appliance to feed that device. The four sockets fill in order; a fifth connection is refused. A power strip can extend a socket, sharing the same total capacity.

Charging draws at most **3,000 W**, with a **20 A** input limit: a 120 V input therefore charges more slowly than a 240 V input. The four outputs together supply at most **3,000 W** and remain limited by the installed cells' discharge capability and charge. During a heavy load, batteries can assist the input. These are total ratings, not 3,000 W per socket.

Unplug all output cords before changing voltage. Sneak-use a UPS socket to disconnect the nearest occupied output; the GUI's **Unplug input** button disconnects its supply. Sneak with an empty hand to carry it while retaining its input cord. Moving the UPS disconnects downstream cords. Breaking it disconnects all attached cords. Cord loops are rejected.

Manual bypass has the existing UPS behavior: input passes through without regulation or battery backup. The compact UPS refuses bypass when the nominal input and output voltages differ. Its 3,000 W inverter limit does not protect a bypass circuit; use an upstream branch breaker.

## Cabinet indicators and displays

Floor and wall variants of the programmable UPS and ATS have detailed doors, vents, hinges, latches and display bezels. The compact UPS has the same visual language with four visible receptacles and mating plugs. Cabinet geometry and stationary plugs use the terrain mesh; LEDs and seven-segment readouts remain live, so changing readings does not rebuild the enclosure.

UPS indicators, left to right: green mains present, amber battery backup/assist, blue charging, red protection/empty/overtemperature, purple bypass. The readouts show output volts and battery percent. The GUI gives the full status and power readings.

ATS indicators, left to right: green mains available, amber generator available, blue load connected, red backup feed fault, purple generator start requested. Readouts show output volts and load power in hundreds of watts. The normal transfer timing, contactor sound and electrical circuit are unchanged.

The optional **EAMedia 1.0.8** addon adds a separate wall-mounted browser TV. It requires this core version; existing television and jukebox save IDs remain unchanged.
