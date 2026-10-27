# Industrial equipment — 3.0.59

All eleven stationary machines have baked terrain bodies. The forklift and pallet are moving
entities; their reusable OBJ geometry remains in the entity renderer. Existing saved item IDs
are unchanged. New transparent machines occupy group 126, slots 0–10; accessories occupy
shared-item group 117, slots 0–3.

## Wiring and controls

Blue left or bottom is input. Orange right is output. Front top is an ELN trip/fault signal;
front bottom is an inhibit/trip signal. Use ordinary placed cables. The load bank, motor drive,
charger, welder, jukebox and TV also accept voltage-matching cords with sufficient nominal ampacity.
Changing nominal input voltage while plugged in is rejected: unplug first.

- **Programmable Load Bank:** select nominal voltage and 0–1,000,000 W. Its real resistive
  consumption follows voltage squared. Fan sound and case heating follow actual operation.
- **Grid Control Console:** power from 120 V (20 W). Enter X/Y/Z and a 1–12 circuit/phase
  number, then bind one of eight addresses. Targets must be in the same dimension within
  256 blocks. Live telemetry uses loaded chunks only. Open/close operates smart meters,
  service disconnects, individual panel breakers, manual recloser/gang switches, contactor
  enable/isolation and new industrial machines. It cannot reset protective lockouts or breaker
  trips. Reset those at the protected equipment after investigating the fault.
- **Protective Relay Cabinet:** line enters left bottom and leaves right bottom. Optional
  explicit return enters left-right and leaves right-right (the right LRDU terminal on each
  side). Set nominal voltage, current pickup, voltage ratios, delay and differential pickup.
  Differential pickup 0 disables it. A normal implicit-ground ELN circuit must leave this off;
  enable it only when both independent forward and return conductors pass through the relay.
  Undervoltage arms after healthy power, avoiding an unavoidable cold-start trip. Faults latch.
- **Pole Surge Arrester:** place and wire as a shunt, or right-click an overhead distribution
  pole with the item to install a visible primary-protection kit. Sneak empty-handed to remove
  the kit. It has a shared 200 kJ absorption budget and a finite-resistance surge shunt; a spent
  cartridge fails open and must be replaced. Nearby lightning costs 40 kJ and becomes visual
  only. This is a finite-energy transient protector, not an ideal voltage regulator or infinite
  lightning shield. Distribution kits automatically match the pole's nominal voltage.
- **Pad Mounted Neighbourhood Transformer:** green cabinet with opening doors when isolated.
  Insert an existing 10/25/50/75/100 kW pole transformer and a physical primary fuse. Set nominal
  primary/secondary voltage. The passive ratio follows primary sag and conserves power;
  sustained overload opens the fuse. Replace the spent fuse before restoring power. It accepts
  placed feeder cables, not overhead spans that belong at service masts/poles.
- **Variable Frequency Motor Drive:** choose nominal input/output, 0–60 Hz, ramp time and
  nominal short-circuit current limit. ELN uses scalar voltage, so frequency commands a ramped
  voltage ratio to the existing shaft motors; this is not a three-phase waveform simulator.
  A passive output impedance bounds current, with real loss and voltage droop under load.
  Drive frequency ramps down on stop; an overheat trip isolates immediately.
- **Automatic Load Shedding Controller:** four independent right-face outputs: left = priority
  1, right = 2, bottom = 3, top = 4. Low input voltage or excess input power sheds the lowest
  remaining priority once per second. Healthy voltage plus headroom restores one output every
  five seconds. Set the nominal input and total watt budget.
- **Programmable EV Pack Charger:** insert one removable battery in the outlined, labeled slot above the buttons; set input voltage, watts and
  game-clock charging hours (0–23, with equal start/end meaning always). Actual input energy
  charges the pack at 94% efficiency, limited by its capacity and charge rating. The new empty
  48 V / 20 kWh traction pack powers the forklift. No third-party vehicle integration is claimed.
- **Electric Welding Station:** left slot iron ingot or damaged tool, middle slot iron ingot,
  right slot output. Two ingots and 120 kJ produce a welded machine frame. A damaged tool,
  one iron ingot and 120 kJ restore 25% maximum durability while preserving other components.
  Changing the input discards unfinished work. Sparks and arc sound run only while welding.

The [Powered Web Jukebox](powered-web-jukebox.md) adds HTTPS/YouTube music playback using
real 120/240 V power, with bundled client media tools for YouTube and non-Vorbis formats.

The [Powered Browser Television](powered-browser-tv.md) adds interactive webpages, streaming video
and stereo audio. It uses 120/240 V at 150 W while on, with the MCEF browser bridge included.

## Electric forklift

Craft the welder first, weld machine frames, then craft the forklift. Craft a traction pack and
charge it in the EV charger. Sneak-right-click the parked forklift with the pack to install it;
sneak empty-handed while parked to remove it. Right-click normally to ride. Movement keys
steer and drive, Page Up/Down raise/lower the forks, G loads/unloads a pallet; sneak dismounts. Keys can
be rebound. Motor and lift work drain the real pack, and reversing sounds an alarm.

Place a Warehouse Equipment Pallet and right-click for its 27-slot inventory. Put equipment
items or materials inside, close the inventory, approach with lowered forks and press G.
Pallets with an open inventory cannot be picked up. Lift up to 2.5 blocks and press G again to
place the same contents in a clear destination. Sneak empty-handed to pack up an unloaded
pallet as one item retaining its inventory. Breaking a forklift drops its pack and loaded pallet
separately, once each. This transports equipment in item form; it does not dismantle arbitrary
placed machines or their multiblocks.

## Dryer appearance

The existing 240 V dryer now has its own opaque rectangular loading door, raised rear control
console, top lint-filter pull, bottom vent and rear exhaust. It retains its existing recipes,
power requirements and baked body, with no second coplanar roof surface.
