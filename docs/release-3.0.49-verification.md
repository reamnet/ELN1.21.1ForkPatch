# 3.0.49: transmission equipment, adjustable warehouse lighting and privacy

## Devices and connections

- 69 kV steel poles are 16 blocks tall. The 230 kV steel towers are 24 blocks
  tall with wider arms. Both carry three independent phase circuits and have
  separate riser variants with A/B/C placed-cable inputs at their bases.
- The substation transformers step 230 kV down to 69 kV, or 69 kV down to
  12.5 kV. Each leg uses a passive linear `ServiceTap`, with winding resistance
  representing 0.5% copper loss at its design load (30 MW or 5 MW per phase,
  respectively). These are loss-design references, not additional trip settings.
  Primary fluctuations and reverse power follow the circuit equations. No
  target-voltage control or extra Thevenin probes are introduced.
- Transformer primary and secondary disconnects operate separately, each opening
  all three legs together. Standalone gang-operated substation disconnects are
  also available at both transmission voltages. Menus select individual input
  and output terminals or three-phase bundles, and show every leg's voltage.
- Two new distribution poles combine a fused single-phase branch with a secondary
  powered street light. The transformer version has independent transformer and
  branch fuses. A branch fault does not open the three-phase through line or the
  transformer feed. The non-transformer version requires an upstream LV supply
  for its light. Both use the selected A/B/C leg for the branch.
- Single-phase distribution variants have a porcelain insulator on top of the
  pole, with the incoming overhead connection moved to its actual terminal.

New descriptor IDs are appended into unused slots. Existing poles, mini-fridge,
cord ratings and world-save identities are retained. Transformer upgrades and
physical fuse cartridges continue to use the existing installation system.

## Lamps, cords and covers

The dryer cord now uses a NEMA 10-30P mesh, with two slanted hot blades and an
L-shaped neutral. Its matching outlet is a 10-30R. Saved `V240_30` anchors keep
their identity and 240 V / 30 A electrical rating.

Bulb holders accept standard 120 V and 240 V appliance cords even while empty.
This includes decorative/recessed fixtures, floodlights and the construction
cart. Actual supply voltage still reaches the bulb; its voltage rating and
protection are not replaced with automatic voltage conversion. The special
dryer connector is not treated as a standard lamp plug.

The new **Adjustable Suspended Warehouse Light** holds four replaceable bulbs.
Its menu changes the drop from 0 to 7 blocks in quarter-block steps. Canopies
stay at the ceiling, suspension wires extend, and the body and actual light
sources move together. A ceiling ten blocks above the floor can therefore
support a fixture hanging six or seven blocks down. Settings persist through
saves/configuration copying; non-finite network values are rejected. The body
uses the terrain mesh path and has no large invisible clearance collision box.

Cable covers now maintain a persisted `covered` block-state bit. Placing,
removing or loading an old cover updates vanilla's weather heightmaps. The
motion-blocking predicate recognizes covered cable states even though their
dynamic shapes have no vanilla legacy-solid cache. Bare cables remain
non-blocking. Existing cover terrain shading and shader material handling stay
in place.

## Distant Horizons 3.3.3

An optional client bridge uses DH's custom-box API; DH is not required to load
the mod. It samples actual captured model surfaces and vertex/texture colors
instead of turning the entire model bounding box into one solid cube. Rotating
parts are frozen at an observed angle for the distant silhouette. Overhead
catenaries use thin segments. Invisible node/ghost support volumes are omitted
from DH terrain; covers receive position-specific colored cube snapshots.

Geometry preparation runs on one low-priority worker, with bounded queues and
caches. The bridge retains up to 2,048 entries / 150,000 boxes, and renders up
to 256 nearby distant groups, with 64 nearby groups prepared for handoff.
Range follows DH's configured distance. It does not load or simulate chunks.
Observed geometry is saved in `eln-objects-v1.dat` inside DH's per-level cache
folder when available. Integrated worlds fall back to their own
`data/eln-distant/<dimension>/` folder, avoiding a DH 3.3.3 client-wrapper cast
failure in its save-folder accessor. Unseen server changes and moving parts
refresh when their normal chunks are observed again. It cannot reconstruct device models in never-visited chunks
from DH's block-state-only data.

The client-level accessor is used for integrated worlds: DH's general loaded
level list contains server wrappers there, which are not the client renderer.

## External reporting

The two upstream analytics startup calls and HTTP implementations are removed.
Compatibility entry points are inert even with old settings enabled. Loading
configuration clears old analytics endpoints/identifiers and disables opt-in
flags; it no longer generates an analytics UUID. Automatic upstream update
requests are removed too.

The simulator's MQTT reporting sink is replaced by local logging. Local Spark/
debug profiling and the in-memory test sink remain. Explicitly configured MQTT
device and Modbus automation remain available; neither is enabled by default.
This audit concerns ELN, not Minecraft authentication or other installed mods.

## Verification

The full regression suite passes: **891 tests across 217 suites**, with zero
failures, errors or skipped tests. Run it with
`./gradlew --no-daemon --max-workers=2 test`.

The opt-in `TransmissionLightingChecks` native client campaign passes with
Distant Horizons 3.3.3, Sodium 0.8.13, Iris 1.8.14-beta.1 and BSL v10.1.8. It
places the actual new equipment, both registered wind-turbine types, a warehouse
fixture, a dryer and its 30 A outlet, and a covered cable. Five assertions verify
weather heightmaps, real cord attachment, DH placeholder filtering, the published
6.75-block suspension and active DH objects after the normal chunks unload.
The final DH state has no API failure or pending geometry work. The cloud client
uses Mesa llvmpipe; this is a correctness check, not an FPS benchmark.

- [Native assertions and renderer versions](verification/release-3.0.49/native.json)
- [Full regression totals and selected suites](verification/release-3.0.49/units.json)
- [Source reporting audit](verification/release-3.0.49/privacy.json)
- [Transmission equipment](verification/release-3.0.49/near.png)
- [Distant silhouettes after normal chunks unload](verification/release-3.0.49/distant.png)
- [Suspended warehouse fixture](verification/release-3.0.49/warehouse.png)
- [Installed dryer plug](verification/release-3.0.49/dryer-plug.png)
- [Wind turbines](verification/release-3.0.49/wind-turbines.png)

Language generation and NeoForge data generation complete successfully. New
device recipes, advancements and item models are checked in. Existing descriptor
IDs remain unchanged; tests cover cord compatibility, passive transformer power
balance and sag, disconnects, independent tap/transformer fuses, riser terminals,
top-insulator endpoints, suspension persistence and old analytics configuration.

This is a source-only release. Build instructions are in
[building on Ubuntu](building-on-ubuntu-24.04.md); the output is
`build/libs/ElectricalAge-3.0.49.jar`.
