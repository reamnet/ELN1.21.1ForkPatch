# 3.0.54: DH recovery and clearer substations

## Distant poles and conductors

A registered, active DH object is not proof that DH still draws it. A dedicated-server regression reproduces a failure with no API exception: after DH recreates its level renderer, ELN's groups stay attached to the old registry. Both pole and conductor render callbacks fall to zero while the cached groups still report active. This was not covered by the previous integrated-world unload/return test.

ELN now checks the current matching client wrapper and render registry during refreshes. A replacement registry causes the old groups to be released against their old owner and the retained geometry to be registered with the new one. An unready wrapper no longer prevents checking other matching, bound wrappers. A transient API error releases GPU groups and retries after a delay while retaining the snapshots. Missing/incompatible API members still disable the unsupported integration instead of retrying continuously. Binding generations and errors are available in the native diagnostic report and local log; no external reporting is added.

Disconnect now explicitly flushes the snapshot cache and disposes live groups. On a multiplayer reconnect, previously visited poles and wires can be restored while their normal Minecraft chunks remain unloaded. The v2 cache remains readable; an optional metadata trailer stores power-line priority and is ignored by older v2 readers.

Poles and conductors receive priority over decorative equipment when the bounded cache fills or the render-object budget is reached. Existing hard limits remain: 2,048 cached entries, 150,000 cached boxes and 256 active distant groups. This avoids increasing the overall budgets to compensate for a busy build. Snapshot persistence retains the priority classification too. As before, distant objects require data previously received by this client; the cache does not request or force-load unseen server chunks.

ELN also detects DH's disabled generic-rendering setting and logs an explicit explanation. It does not override that setting. DH's generic rendering must be enabled to draw custom pole/wire objects.

## Substation voltage and phase markings

The 230→69 kV and 69→12.5 kV substations now carry large orange primary-voltage signs, blue secondary-voltage signs and A/B/C terminal markers. Signs are visible on the outer banks and the front/back fascia, with correctly oriented lettering on both sides. Matching legends appear in the GUI and item tooltip. Disconnect equipment uses the same markings with its own nominal voltage. The signs are original texture assets captured into the baked model; they do not draw live text each frame. `tools/lighting/generate_transmission_markings.py` reproduces the texture.

Two connection problems are corrected:

- Aimed bank and phase selection now transforms the hit into the rendered model's orientation. Previously a rotated substation could select the opposite voltage bank, and a rotated transmission pole could select the wrong phase.
- A substation's voltage banks now stay at the orientation in which it was placed. Stations anchor the primary phase-order alignment; attached poles choose their equivalent half-turn around those fixed banks. The alignment code previously could turn the entire station by 180 degrees after a connection, moving both voltage banks and introducing unnecessary crossings.

Electrical A/B/C assignments, saved terminal IDs and transformer ratios are preserved. The code does not disguise a crossing by swapping electrical phases. Complex layouts can still require appropriate pole placement and routing.

## Verification

- **928 tests across 227 suites pass**, with no failures, errors or skips. Tests cover registry ownership during replacement, stable-registry reuse, eviction/render selection under decorative-device pressure, every cardinal transmission orientation, actual bank/phase aiming, stationary banks and aligned feeder phases. [Unit results](verification/release-3.0.54/units.json).
- The native campaign connects through a real multiplayer connection to a localhost dedicated server with DH installed on both sides. It includes 230 kV, 69 kV, 12.5 kV and LV poles with real three-conductor spans, plus both substations.
- With `eln.disableDistantRebind=true`, the controlled DH renderer recreation reproduces **zero pole and wire render calls** despite active cached groups and no API error. [Expected baseline failure](verification/release-3.0.54/native-baseline.json).
- With the fix enabled, DH's actual per-group render callbacks confirm both bodies and wires draw after normal chunk unload and after the renderer is recreated. After disconnecting and reconnecting outside vanilla range, the cache restores the groups and they draw again without receiving the pole chunks. The final capture records 406 pole/body calls and 172 wire calls. These counts prove draw participation, not FPS improvement. [Native results](verification/release-3.0.54/native.json).
- A decoded real multiplayer cache contains 14 entries, 4,354 boxes and 14 grid-priority records, with valid coordinates and a complete optional metadata trailer. [Cache check](verification/release-3.0.54/cache.json).
- The final focused check passes **11 tests in 4 suites** after the lettering review.
- A separate final daylight preview passes after the two-sided lettering correction. [Sign preview](verification/release-3.0.54/sign-preview.json).
- Both substations' native meshes include the voltage/phase texture as baked terrain geometry. Screenshots show [230→69 kV](verification/release-3.0.54/substation-230.png), [69→12.5 kV](verification/release-3.0.54/substation-69.png), [renderer recovery](verification/release-3.0.54/renderer-recovered.png) and [reconnection](verification/release-3.0.54/reconnected.png).
- Runtime: Minecraft 1.21.1 / NeoForge 21.1.249, DH 3.3.3, Sodium 0.8.13, Iris 1.8.14-beta.1 and BSL v10.1.8. Mesa llvmpipe is used for rendering correctness; this is not a hardware FPS benchmark. Translation catalogs are regenerated. No existing registered IDs, recipes or electrical solver equations change.

For the native test, run a prepared dedicated development server bound to localhost with `-Deln.distantMultiplayerServer=true`, then run the client with `-Deln.distantMultiplayerChecks=true`. The opt-in helper creates/reuses an isolated test layout, grants its test player creative/operator access, and reconnects to localhost port 25575. Do not enable these development flags on a real server. `eln.substationPreviewOnly=true` limits the client campaign to the sign previews.

This is a source-only prerelease. Build with a full Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.54.jar`. See [Ubuntu instructions](building-on-ubuntu-24.04.md).
