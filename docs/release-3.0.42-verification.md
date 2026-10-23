# 3.0.42: bake the remaining eligible model geometry

The terrain mesh path now covers the registered legacy device renderers as well
as the newer models converted in 3.0.41. This includes older machines, shaft
casings, instruments, switches, transformers, cable bodies and lamp fittings.
The original OBJ geometry, materials and resource-pack texture paths are used.
No item IDs, recipes or electrical simulation behavior change. The Create
adapters already use normal baked casings; their rotating shafts and the animated
replicator entity remain live.

Legacy models often retain direct references to OBJ parts. Selection therefore
happens when a part is drawn, rather than when its reference is looked up. The
shared renderer fingerprints cached batches, exact transforms, materials and
colors once per client tick without expanding their vertices. An unchanged
configuration reuses its signature objects and mesh. Visual changes rebuild only
the affected sections. This removes the previous per-renderer eligibility list
for legacy bodies and avoids maintaining incomplete manual visual-state keys.

Angle/scrolling OBJ draws and named externally moving parts remain live. So do
screens, displayed items, graphs, camera-dependent reflections/halos, particles,
flexible cords and overhead spans. Long wire spans can traverse empty air
sections which Minecraft does not compile; they retain the existing cached
span renderer. Non-quad line effects and exceptional resource-pack overrides
retain the original renderer. These are intentional live geometry, not omitted
stationary device models.

Textures that are small or have odd dimensions receive repeating pixel padding
instead of being rescaled or reducing the atlas mip level. Existing ordinary
block/item sprites use their canonical atlas identities. Reloads clear both
positive and fallback caches, including the inventory icon/model decision.

Inventory, held, dropped and item-frame previews use standard baked item quads
where the preview is static. Component-aware, bounded caches preserve stack
variants. JSON resource-pack replacement models retain control. Previews which
render font labels or nested item contents keep their original custom renderer;
the audit records these exceptions. Capturing a preview inside a running world
renderer preserves the outer matrix, lighting, attributes and mesh sink.

## Verification

The [coverage audit](verification/release-3.0.42/coverage.json) exercises **588
registered variants across 131 renderer classes**. All 580 variants with
stationary geometry register a nonempty terrain body. The remaining eight are
span-only, line-only or historically empty renderers. After lazy display-list
warmup, the original positions, UVs, normals, ARGB colors and materials equal the union of the static and live meshes. Cold production
captures are also compared against the warmed reference; fingerprinting preserves
the current UV and normal state before capture. Rotating shaft assemblies and
energy-meter readings leave their casing meshes unchanged. Hot bare-cable glow remains live without rebuilding the
conductor; terrain discovery does not emit extra overheat smoke.

The item audit exercises **2,352 previews**: GUI, ground, first-person and
third-person views for every registered device item. **1,652 preview cases**
newly use baked quads; **518** were already baked; **182 GUI cases** retain the
original renderer because they include font labels. Newly converted previews
match their original geometry, atlas-mapped UVs, colors, normal directions and
full-bright state. Capture inside an active world-render context preserves that
context. See the [raw item audit](verification/release-3.0.42/items-bsl.json).

All **836 unit tests pass**, with zero failures/errors/skips in **205 suites**.
Regressions cover cached-part/dynamic selection, exact reusable signatures,
texture padding, item color/normal packing, section clipping and the existing
simulator behavior. See [unit results](verification/release-3.0.42/units.json).

The native Sodium 0.8.13 / Iris 1.8.14-beta.1 / BSL 10.1.8 probe passes, including
29 real placed devices, the full registered catalog, every decorative fixture,
animation, visual changes, resource/atlas reload and removal/re-addition. The
atlas includes 755 legacy textures at 2048×2048 and retains mip level 4.
Comparison images:
[baked](verification/release-3.0.42/bsl-terrain.png),
[original](verification/release-3.0.42/bsl-original.png).
The standard Minecraft renderer also passes the same catalog, preview,
animation and lifecycle checks. Standard comparison images:
[baked](verification/release-3.0.42/vanilla-terrain.png),
[original](verification/release-3.0.42/vanilla-original.png).
Raw results: [BSL](verification/release-3.0.42/native-bsl.json),
[standard renderer](verification/release-3.0.42/native-vanilla.json).

The warmed CPU benchmark measures 20 actual models per trial, with 40 warmup
and 60 measured trials. These are model-emission costs, not frame timings:

| Renderer | Original median | Baked body + live parts | Original vertices | Live vertices |
| --- | ---: | ---: | ---: | ---: |
| Three-phase lit pole | 5.071 ms | 0.020 ms | 99,200 | 0 |
| Chandelier | 14.452 ms | 0.042 ms | 252,400 | 0 |
| Legacy generator | 3.985 ms | 2.796 ms | 75,840 | 47,360 |
| Logic module | 0.425 ms | 0.153 ms | 6,560 | 2,400 |

The legacy generator retains its rotating shaft and power indicators. The logic
module retains its moving door and LEDs. Their remaining live cost is expected.
Vanilla and shader terrain passes can shade transparent materials differently;
the conversion preserves the underlying model, rather than promising identical
shader output for every material.

The opt-in production-renderer catalog audit runs with:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.terrainChecks=true -Deln.terrainCatalogChecks=true" ./gradlew --no-daemon --max-workers=3 runClient
```

It checks every registered six-node/transparent-node descriptor, compares all
original vertices/materials against the union of baked and live geometry, and
checks that unchanged legacy state does not rebuild its mesh. It also audits
item previews, exercises real placed devices, checks animation and visual-state
changes, and tests resource reload and source removal/re-addition. The client
writes JSON reports and comparison screenshots, then exits.

Baking removes repeated CPU model emission and lets Minecraft batch stationary
geometry in its section buffers. Meshes still consume GPU time and memory, and
section rebuilding has a cost. Item models also still emit cached quads through
the normal item renderer. Neither path has zero rendering cost, and a reduction
in model-emission CPU time is not a measured whole-game FPS increase.

Build with JDK 21 using the [Ubuntu instructions](building-on-ubuntu-24.04.md).
The release is source-only so the compiled `ElectricalAge-3.0.42.jar` can be
attached separately.
