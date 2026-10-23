# 3.0.41: static models in chunk meshes

New placed lamp fixtures, appliances, fans, outlets, UPS/ATS/solar cabinets,
pumps, service equipment and utility poles now put their stationary model bodies
in Minecraft's section buffers. NeoForge's `AddSectionGeometryEvent` is supported
by both the vanilla renderer and Sodium 0.8.13. This is actual terrain geometry,
not another display-list cache replayed by each block entity every frame.

The existing OBJ shapes and materials are retained. Moving fan blades, turbine
rotors, meter disks/needles/readouts, Christmas-tree blinking bulbs, flexible
cords, placed terminal cables and overhead spans retain their live render paths.
Legacy models outside the explicitly converted renderers retain their previous
renderers. Item/inventory previews retain their previous rendering.

## Geometry and lifecycle

The client records each visual configuration on its main thread. Identical
configurations share immutable geometry through a bounded cache. Chunk workers
receive immutable snapshots and never call the GL compatibility layer, access
mutable renderer instances, or query live block-entity state. Current, watts,
energy and other telemetry are excluded from static mesh keys; visible switch,
fuse, lamp, plug, mount and aiming changes invalidate the affected sections.

Faces crossing section boundaries are clipped, with interpolated UVs, colors
and normals. This keeps tall/wide geometry independent of its base section's
visibility and keeps Sodium's packed vertex positions in range. Most faces share
the original arrays; clipping allocates only for crossing faces. Negative world
coordinates and positions near the world border use the same path.

Textured models use block-atlas sprites without changing their resource-pack
paths. Repeating OBJ UVs are split at tile boundaries before atlas mapping.
Unsupported texture sizes, primitive types or extreme UV ranges keep their
original live renderer. Unsupported configurations are cached to avoid repeated
failed capture attempts. Model/texture reload clears both geometry and fallback
caches, while node removal and chunk/world unloading remove their source entries.

The previous neighboring sky/block light is retained and refreshed as lighting
changes. Emissive parts retain full light. Normal directional shading is applied
for vanilla rendering; shader packs which disable vanilla directional shading
through the level's shade API supply their own shading. This avoids applying
extra darkening with BSL. Transparency is submitted to the normal translucent
terrain layer. Invisible collapsed faces are removed before submitting vertices:
Sodium 0.8.13's translucent consumer can otherwise retain an invalid vertex index
after rejecting such a face.

## Verification

The opt-in native probe creates real placed devices in an integrated world,
using Sodium 0.8.13, Iris 1.8.14-beta.1 and BSL 10.1.8. It compares original
captured model attributes against the union of static and live geometry, checks
section compilation, lamp/pole changes, independent fan animation, source
removal/re-addition and resource/atlas reload. It also checks every registered
new decorative lamp style and wireless wall control.

Run in a development client with a working display:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.terrainChecks=true" ./gradlew --no-daemon --max-workers=3 runClient
```

The probe writes `run/client/terrain-model-checks/report.json` and comparison
screenshots, then exits. Read its `failure` field as well as the process exit
code. `-Deln.disableTerrainModels=true` temporarily restores the original live
bodies for comparison; it is a development diagnostic, not a gameplay setting.

All **832 unit tests passed**, with zero failures/errors/skips in **204 suites**.
New regressions cover clipping area/winding/attributes, negative and world-border
coordinates, boundary ownership, repeated UVs, collapsed faces, ARGB tint and
thread-local/nested part filtering. See [unit results](verification/release-3.0.41/units.json).

Both the **standard Minecraft renderer** and **Sodium + Iris + BSL** completed
all native checks, including all 87 decorative fixtures and four wireless wall
controls. The latter used the exact requested Sodium 0.8.13 / Iris
1.8.14-beta.1 combination. The final atlas contains 174 legacy model textures
and retains mip level 4 at 2048×1024. Existing blocks/items/voltage atlas sources
are preserved. Raw results: [BSL](verification/release-3.0.41/native-bsl.json),
[standard renderer](verification/release-3.0.41/native-vanilla.json).

The native benchmark warms up 40 trials, then measures 60 trials of 20 actual
models. Median CPU time for original body emission versus the live renderer
with its static body already in terrain:

| Renderer / 20 models | Original | Baked body |
| --- | ---: | ---: |
| Sodium/Iris/BSL: street poles | 9.164 ms | 0.039 ms |
| Sodium/Iris/BSL: crystal chandeliers | 21.393 ms | 0.047 ms |
| Standard renderer: street poles | 2.554 ms | 0.025 ms |
| Standard renderer: crystal chandeliers | 6.669 ms | 0.045 ms |

The old paths emitted 99,200 pole vertices and 252,400 chandelier vertices per
20-model trial; the baked bodies emit **zero** from the block-entity path.
The section buffers retain the actual geometry. One-time capture, section
compilation/upload, shared frame dispatch and GPU work are outside these timing
samples. The development host uses software Mesa; these are CPU-workload
comparisons, not gameplay FPS predictions.

Comparison images retain the same scene/camera: [BSL baked](verification/release-3.0.41/bsl-terrain.png),
[BSL original](verification/release-3.0.41/bsl-original.png),
[standard baked](verification/release-3.0.41/vanilla-terrain.png),
[standard original](verification/release-3.0.41/vanilla-original.png).
Geometry/material attributes are checked directly in addition to reviewing the
images. Shader packs can shade terrain glass differently from entity glass;
this is not a guarantee of pixel-identical shader output.


Baking removes repeated CPU model transformation/emission and lets the terrain
renderer batch/cull the resulting geometry. It does not remove GPU vertex,
shader, shadow or fill cost; actual FPS depends on the scene and hardware.
The timing comparison measures model CPU emission, not whole-client FPS.

Build `build/libs/ElectricalAge-3.0.41.jar` with JDK 21 using the
[Ubuntu instructions](building-on-ubuntu-24.04.md). The GitHub release is
source-only; the compiled jar is attached separately.
