# 3.0.56: Secondary clearance, transformer cans and cutouts

The 3.0.55 LV support used an offset along the run. A returning span could cross the wood, and the thick triplex envelope could also overlap a transformer can. The support now sits perpendicular to the run; its reach accounts for conductor size and the installed transformer. It chooses the equivalent side facing away from the can.

Each LV endpoint publishes a conservative horizontal envelope enclosing its pole and installed can. Clear spans keep their original catenary. When a span intersects an envelope, a small visibility graph selects a detour around the pole/case and reapplies sag along that path. The entire tube cross-section is allowed for, rather than checking sampled center points alone. Terminal endpoints are preserved. Triplex twist follows distance along the routed path, with frames following its bends. The same geometry feeds baked terrain, the boundary fallback and DH snapshots.

Service drops still splice into real main-line strands. The attachment search leaves room for their connector sleeves and the branch gauge. Short secondary jumpers leave the can outward first, then route around the pole/case. This avoids replacing a wood intersection with a tank intersection.

The 10, 25, 50, 75 and 100 transformer items now produce progressively larger can diameters and heights, in item previews and installed pole models. Bushings retain their top height, move with the can center, and keep their leads aligned. Can backs stay clear of the wood. Modern transformer poles and legacy transformer/street-light poles use the common model. Changing the item also publishes to adjacent nodes, so a neighbour-owned incoming wire gets the new endpoint/envelope. Existing item IDs, capacities, voltage ratios and recipes are retained; this changes physical appearance, not electrical power ratings.

Cutouts now have a fixed porcelain/contact holder with an upper jaw and lower hinge, plus a separate fiberglass fuse carrier and operating eye. Opening or blowing the fuse rotates only the carrier away from the pole. The hinge is offset so an opened tube clears the largest tank. HV leads terminate on the holder's upper contact and the can connects from its lower contact. The fixed/moving meshes are cached, and installed poses bake into terrain. Fuse operation, cartridge handling, protection timing and sounds are unchanged.

Both client and server must update to **3.0.56**, because the wire publication now includes support reach and clearance envelopes. Saved links retain their cable stacks, lengths, thermal state and electrical circuits. No electrical solver equations or simulation timestep settings change. Routing uses geometry only and does not load chunks.

## Verification

- **85 tests across 19 suites pass**, with no failures, errors or skips. [Unit results](verification/release-3.0.56/units.json).
- The native campaign passes **11 checks**. Its actual client meshes retain at least **0.1407 blocks** of conservative surface clearance beyond the tested pole/can envelopes. It records **5,280 baked wire/splice quads across 30 sections**, and DH draws the distant wire groups **637 times** after normal chunks unload. These counts establish rendering participation, not an FPS benchmark. [Native results](verification/release-3.0.56/native.json).
- Model captures confirm can diameters grow from approximately **0.454 to 0.895 blocks**, and heights from **0.781 to 1.519 blocks**, across the five ratings.
- Screenshots: [five can sizes](verification/release-3.0.56/transformer-ratings.png), [closed holder/carrier](verification/release-3.0.56/cutout-closed.png), [opened carrier](verification/release-3.0.56/cutout-open.png), [secondary clearance](verification/release-3.0.56/triplex-splice.png), [boundary fallback](verification/release-3.0.56/boundary-fallback.png) and [distant view](verification/release-3.0.56/distant.png).

Results and native screenshots are in [release-3.0.56](verification/release-3.0.56/).

The focused regressions reproduce the old returning-span/wood intersection and verify clearance through straight runs, corners, opposing ownership, independent HV/LV rotations, every can rating, thick utility cable and the original creative cable. They also check splice continuity, packet round trips, unchanged clear catenaries, secondary jumper clearance, fixed bushing heights, monotonic model sizes, cutout pivot geometry and geometry publication to a neighbour-owned span. Existing grid compatibility, terminal aiming, service interaction, protection and terrain/vertex tests are included.

The native campaign uses Minecraft 1.21.1 / NeoForge 21.1.249 with Sodium 0.8.13, Iris 1.8.14-beta.1, BSL v10.1.8 and DH 3.3.3. It measures the actual decoded client cable geometry against pole/can envelopes, verifies all five tank sizes from their baked vertices, and swaps a transformer while its neighbour owns the wire. Baked porcelain vertices must stay identical while the carrier vertices move below the hinge. It also checks service removal/reconnection, baked-mesh reuse, the normal-render boundary fallback and actual DH wire draw callbacks after chunk unload. Software rendering checks correctness rather than hardware FPS.

Run the opt-in development campaign with `-Deln.overheadAppearanceChecks=true`; it creates a separate test world, writes its report/screenshots to `run/client/overhead-appearance-checks/`, and exits. Do not enable this test flag during ordinary gameplay.

Source-only prerelease. Build with a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.56.jar`. See [Ubuntu instructions](building-on-ubuntu-24.04.md).
