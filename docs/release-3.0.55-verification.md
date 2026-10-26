# 3.0.55: Cable gauges, triplex and service splices

Overhead spans now derive their visual diameter from the cable stack actually installed on the link. Utility conductors scale with the square root of their area, preserving gauge-to-gauge diameter ratios rather than using one fixed thickness. Bare copper is copper-colored; bare aluminum is silver. Insulated conductors retain dark jackets and exposed metal at individual terminations. Original ELN power cables have no area/material metadata, so their existing render dimensions and a copper fallback are used.

New 120/240 V spans require intact insulated power cable. The first-click handler and authoritative link API check the selected service terminals, including either connection order and individual L1/L2/240 V circuits. Invalid attempts do not consume cable. HV compatibility and voltage ratings are unchanged. Existing bare service links still load, retain their paid cable/thermal state, and render as bare conductors. They must use insulated cable when replaced.

A complete insulated secondary/service bundle renders as triplex: two jacketed conductors wrap around a metallic messenger. The twist matches the terminal fan-out at each end. Individual selected circuits remain jacketed single-conductor branches. The three underlying ELN scalar L1/L2/240 V equivalents stay separate; this visual messenger does not add a simulated neutral or alter power flow, protection, transformer ratios or the solver.

The secondary rack now uses a compact suspension point. Main pole-to-pole triplex hangs through it without service splice fittings. Connecting a weatherhead service drop, or an individual secondary tap, adds sleeves/bands on the actual matching main-line strands; the drop leads start at those positions. Removing the drop removes those fittings and leads. If no main secondary is present, the drop attaches at the ordinary pole support without inventing a main-line splice. Splices work regardless of which endpoint owns the network packet.

Geometry is built when the cable, endpoints or topology change. Cables and splice parts use a separate static terrain surface attached to their node, partitioned across the sections they actually cross. Nearby complete spans bake into ordinary Minecraft/Sodium terrain buffers rather than expanding their vertices through the device renderer every frame. Pole bodies and cable surfaces have separate keys, so changing a drop does not overwrite the pole mesh or its distant silhouette. Unchanged status packets reuse both the native source mesh and the baked mesh.

A span crossing the edge of the loaded/rendered chunk footprint keeps the existing whole-span renderer as a fallback. Baking only its nearby pieces would introduce a gap before its owning pole hands off to DH. The residency check uses a conservative interior margin and does not load chunks. Terrain-disabled/unsupported cases retain the normal fallback too. This is not a claim of zero GPU cost or a measured hardware FPS improvement.

The server caches main-line geometry used to locate branch splices, invalidating it when gauge or orientation changes. Cache keys are weak references, so removed links/worlds are not retained. DH snapshots carry each strand's own radius and color and keep the previous short-box subdivision and retention limits. Its snapshot signature check still runs for unchanged cables, allowing an evicted distant snapshot to be restored without rebuilding the native mesh.

Both client and server must update to **3.0.55**: the span publication format changed, and its network protocol version prevents an older peer from decoding it incorrectly. Saved item IDs, link terminal IDs, cable length/resistance/thermal state and electrical equations are unchanged.

## Verification

- The broad regression run passes **935 tests across 228 suites**, with no failures, errors or skips. [Full-suite results](verification/release-3.0.55/units.json).
- The final focused run passes **40 tests across 7 suites**, with no failures, errors or skips. It covers gauge/material scaling, packet round trips and validation, triplex end geometry, authoritative insulation rules and non-consumption, saved bare spans, service splice alignment in both ownership orders and four orientations, main-geometry cache invalidation, all registered device/cable compatibility, and colored/gauged distant boxes. It also covers terrain partitioning/vertex packing and whole-span residency, including missing chunks, render boundaries, negative coordinates and distant teleports. The server-cache and residency regressions were added after the broad run. [Focused results](verification/release-3.0.55/focused-final.json).
- The native gallery checks seven behaviors through real server span packets and the client renderer, including service-drop removal/reconnection, baked sections, unchanged native/baked mesh identity, zero extra device draw calls for a baked span, and a complete fallback at the normal-render boundary. The gallery captures **2,640 baked cable/splice quads across 18 sections**; the final DH wire-group callbacks record **294 actual distant draw calls** after normal chunks unload. [Native report](verification/release-3.0.55/native.json).
- Native screenshots: [gauge/material gallery](verification/release-3.0.55/gauge-gallery.png), [connected splice](verification/release-3.0.55/triplex-splice.png), [main without a service drop](verification/release-3.0.55/triplex-without-drop.png), [reconnected drop](verification/release-3.0.55/triplex-reconnected.png) [boundary fallback](verification/release-3.0.55/boundary-fallback.png) and [distant view](verification/release-3.0.55/distant.png).

Final results and native screenshots are stored in [release-3.0.55](verification/release-3.0.55/). The opt-in native campaign uses Minecraft 1.21.1, NeoForge 21.1.249, Sodium 0.8.13, Iris 1.8.14-beta.1, BSL v10.1.8 and Distant Horizons 3.3.3. It creates an isolated flat test world with two copper gauges, two aluminum gauges, a suspended triplex main and a weatherhead branch. It checks real span publication, removes/reconnects the branch, verifies native mesh reuse and counts DH wire draw callbacks after ordinary chunks unload. Software rendering checks correctness rather than hardware FPS.

To reproduce the native campaign, run the development client with `-Deln.overheadAppearanceChecks=true`. This flag creates a test world and exits after writing `run/client/overhead-appearance-checks/report.json` and screenshots; do not enable it during normal gameplay.

This is a source-only prerelease. Build with a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.55.jar`. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
