# 3.0.61: American underground service and detailed meters

Adds a two-block-wide wall socket with **both cable entries facing down**, utility
input beneath the left half and customer output beneath the right. Its real wall
cables connect in either placement order. The adjacent downleads are insulated from
each other so matching cable colors cannot bypass the smart meter's disconnect.
The compact socket and all existing save IDs remain available.

Adds an underground wall service entrance and a flush-with-grade secondary handhole.
The single-phase pad transformer now has a green, rounded-top, solid-bottom American
cabinet, concrete pad, opening cable compartment, elbows, secondary lugs, grounding
lead and door hardware. Bottom entries expose primary, full secondary and both half
secondary equivalents. All secondaries share the installed transformer/fuse rating
and follow actual primary sag proportionally, including non-240 V nominal settings.
Older three-phase underground feeder cabinets remain loadable but are hidden from
new crafting and creative selection; overhead three-phase risers remain available.

Analog and smart meters have printed faces, glass, stainless rings, seals and real
socket jaws. The analog disk uses Kh = 7.2 Wh/revolution and reverses for export.
The smart LCD cycles import/export kWh, kW and utility line volts, and remains
line-powered with its service disconnect open. Serial numbers, energy registers and
the smart disconnect state travel with the removable meter. Old installed registers
migrate to the item. All static bodies and faceplates use baked terrain meshes;
glass, needles/disk and live LCD content remain dynamic.

Also prevents client-side overlay capability queries (observed with Jade) from
resolving server-only simulation nodes. This removes repeated diagnostic stack traces
when looking at ELN equipment; server pipe/inventory protection remains in place.

## Validation

All **91 focused utility and industrial tests pass** with no errors, failures or skips.
The suites cover physical meter removal/reinstallation,
old-save migration, all four socket orientations, passive load/sag response, open
disconnects, shared pad service taps, retired IDs and model geometry. See
[unit results](verification/release-3.0.61/units.json).

The opt-in `-Deln.americanServiceChecks=true` native client campaign runs with
Sodium 0.8.13, Iris 1.8.14 beta, BSL and Distant Horizons 3.3.3. It places real
registered same-color cables and meters in both placement orders, supplies actual
24-ohm loads, operates the actual smart GUI button over client/server packets,
verifies the customer side is dead while the input remains energized, and moves the
physical meter to a different socket. It also checks nonempty baked bodies and
actual printed faceplate textures. See [native report](verification/release-3.0.61/report.json)
and screenshots in that directory. Rendering is checked using software OpenGL;
this is functional visual evidence, not a hardware FPS benchmark.
The campaign explicitly queries client energy/item capabilities, and its final log
is checked for the earlier server-node access traces.

Dedicated-server startup and final jar/resource audits are recorded in
[server](verification/release-3.0.61/server.json) and
[packaging](verification/release-3.0.61/packaging.json).
EA Media is unchanged at 1.0.2 and remains optional.

For wiring, limitations and meter behavior, read the
[American service guide](american-underground-service.md).
Source-only prerelease; compile using a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.61.jar`. Update clients and server together.
[Ubuntu instructions](building-on-ubuntu-24.04.md).
