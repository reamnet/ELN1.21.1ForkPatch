# 3.0.53: transmission insulator visibility and distant conductors

Two separate client rendering faults affected the 69/230 kV equipment and other models using the same paths.

## Insulator sections containing only air

Large rotated models can extend into a neighbouring 16×16×16 section containing no blocks. ELN already clips and registers that portion of the baked mesh in the neighbouring section. Sodium 0.8.13, however, exits its section preparation before asking NeoForge for additional geometry when the block section contains only air. Placing a block under the missing insulator makes that section nonempty and allows its geometry to compile, explaining the apparent repair.

Optional client-only hooks let sections containing ELN's registered additional geometry pass that empty-section rendering check. A section already marked empty is also registered for its first mesh rebuild, rather than waiting for a real block to make it renderable. They then compile through the normal NeoForge additional-geometry hook into Sodium's terrain buffers. This changes neither block occupancy nor lighting, collision or simulation. Sections with no ELN mesh retain Sodium's normal empty-section behaviour. No filler blocks or per-frame replacement models are added. NeoForge's vanilla renderer already supports additional geometry in empty sections.

The native regression places both 69 kV and 230 kV poles across chunk borders, connects diagonal three-phase spans to rotate their crossarms, and checks actual Sodium section uploads where the underlying block sections remain entirely air. It checks close flight and a distant-and-return trip without placing support blocks. The diagnostic property `eln.disableSodiumTerrainFix=true` restores the previous rendering check for the comparison.

## Blocky overhead wires with Distant Horizons

DH's custom-object interface uses axis-aligned boxes. Previously each entire diagonal catenary segment became one box. A long span could therefore produce boxes several blocks wide or tall, resembling slabs or a staircase.

The distant representation now subdivides each segment before taking its bounds. Each piece advances at most 0.25 blocks on any axis, with the existing 0.035-block radius padding. Every resulting box is at most **0.32 blocks on each axis**, with continuous overlap along the wire and the original conductor colour. Long, diagonal and vertical spans retain their endpoints. Normal nearby cable geometry is unchanged.

DH handoff now uses the loaded chunk footprint and the camera's chunk coordinates, matching the chunk-based render distance rather than comparing the pole's block position with a moving distance square. Activation updates on camera chunk crossings and render-distance changes, as well as the existing periodic refresh. The distant-object cache uses a new version so old oversized wire boxes are not restored. Previously visited equipment is recaptured automatically when loaded.

## Verification

- **923 tests in 224 suites passed**, with no failures, errors or skips. [Full-suite results](verification/release-3.0.53/units.json).
- A final focused check passes **26 tests in five suites**, including compilation of the adjusted distant-cache entry limit. The cache reader accepts the same 150,000-box total budget enforced by the writer, so a junction owning several subdivided spans remains readable.
- The new regressions cover long diagonal, sloped and vertical wire proxies, continuous endpoints and maximum piece dimensions, plus loaded-chunk handoff on both sides of coordinate zero. Existing section clipping/winding and transmission electrical tests also pass.
- With `eln.disableSodiumTerrainFix=true`, the native fixture reproduces the missing terrain: a real air-only render section has no uploaded geometry (`flags=0`). [Expected baseline failure](verification/release-3.0.53/native-baseline.json).
- With the fix enabled, actual Sodium terrain uploads are present in all **12 air-only overhang sections** across connected 69/230 kV poles. The client then passes close flight, distant rendering after normal chunk unload, and a return trip without adding blocks. Nearby DH proxies are inactive. [Native results](verification/release-3.0.53/native.json).
- Reviewed screenshots: [near](verification/release-3.0.53/near.png), [complete insulators up close](verification/release-3.0.53/insulators.png), [distant](verification/release-3.0.53/distant.png), and [returned](verification/release-3.0.53/returned.png).

The client runtime uses Minecraft 1.21.1 / NeoForge 21.1.249, Sodium 0.8.13, Iris 1.8.14-beta.1, BSL v10.1.8 and Distant Horizons 3.3.3. The cloud client's Mesa llvmpipe renderer checks correctness, not FPS gains. Both fixes are client-side; electrical solver behaviour, pole ratings, IDs and recipes are unchanged.

This is a source-only prerelease. With a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.53.jar`. See [Ubuntu build instructions](building-on-ubuntu-24.04.md).
