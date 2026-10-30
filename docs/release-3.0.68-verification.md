# 3.0.68: equipment artwork, catalog icons and refrigerator doors

See [operation and asset authoring](equipment-visual-refresh.md) for scope and behavior.
The inventory covers 224 OBJ meshes added since 3.0.1: 222 gain original finishes;
two already use detailed legacy textures and retain them. The separate EAMedia
1.0.10 release refreshes its three media cabinets.

## Validation

The complete core build passes **1,011 tests across 244 suites**, with no failures,
errors or skipped tests. Six new refrigerator thermal cases cover open/closed
storage, limited compressor cooling, ambient temperature, substep independence
and long-tick stability. The existing cabinet-clearance regression caught a
protruding detail on the wall MPPT/inverter; their art was corrected to retain
the existing 0.33-block depth. The original test remains unchanged and passes.

The combined packaged client check loads **225 refreshed meshes** (222 core and
three addon meshes), with finite positions, UVs and normals and atlas-backed
finishes. It verifies **755 item-display cases** before and after a resource reload:
148 lamp/pole items plus three media items, each in five contexts. All 148 catalog
icons use one quad; held/dropped/frame views retain their nonempty baked 3D models.
The exported original 3D previews total 198,894 quads across these 148 items;
their GUI sprites total 148. This is a geometry count, not an FPS measurement.

All ten placed representative kitchen/laundry/power-control cabinets have baked
terrain bodies. The native fridge fixture starts at 4 °C with its compressor off,
opens the actual menu, advances eight simulated seconds and reaches about 14.46 °C
in that test biome. Cold-storage protection ends. Menu removal closes the door,
and a second-viewer check keeps it open until the last viewer closes it.

The native run uses Minecraft 1.21.1 / NeoForge 21.1.249, Sodium 0.8.13, Iris
1.8.14-beta.1 and Distant Horizons 3.3.3, with shaders disabled. A software GPU
provides rendering/correctness verification; this is not a physical client FPS
benchmark. Initial scene photos wait for loaded client tiles and their baked
terrain bodies. No new Distant Horizons or public video-stream performance claim
is made by this art update.

Evidence:

- [Unit totals](verification/release-3.0.68/units.json)
- [Native checks](verification/release-3.0.68/native.json)
- [Model inventory and hashes](verification/release-3.0.68/model-refresh.json)
- [Catalog inventory and hashes](verification/release-3.0.68/catalog-icons.json)
- [Catalog contact sheet](verification/release-3.0.68/catalog-contact-sheet.png)
- [Placed cabinets](verification/release-3.0.68/equipment-and-catalog.png)
- [Open refrigerator](verification/release-3.0.68/fridge-open.png)
- [After resource reload](verification/release-3.0.68/after-reload.png)
- [Packaging](verification/release-3.0.68/packaging.json)

The release is source-only. Build `ElectricalAge-3.0.68.jar` with a full JDK 21
and attach it separately; ordinary builds use the committed art without Python.
