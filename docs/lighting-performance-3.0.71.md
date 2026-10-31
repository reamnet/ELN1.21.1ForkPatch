# Temporary-light backlog and chunk-loading fix, 3.0.71

The [slow-tick capture](https://spark.lucko.me/hnCjg2VTO1) on 3.0.70 retained
22 ticks over 100 ms during a ten-minute recording. `LightBlockEntity.update`
accounted for 1,284 of 4,828 sampled milliseconds, approximately 26.6%; 1,256 ms
was in the method itself. This is the share of retained slow-tick samples, not a
whole-server CPU percentage. The recording also shows a legacy lamp's block-state
read entering synchronous chunk loading.

The earlier 49.3-second tick preceded the earlier completed recording. This new
capture identifies an additional ELN hotspot and supports the reproduction below;
it does not establish that every reported pause has the same cause.

## Reproduced defect

ELN continues simulating saved electrical nodes when vanilla chunks are not
ticking. Temporary light blocks previously appended one `LightHandle` for every
refresh. Only block-entity ticking decremented their lifetimes. A resident chunk
outside the block-ticking area could therefore collect an unbounded list of
reports from lamps that continued simulating. When ticking resumed, the light
block scanned the backlog. Expiring entries used iterator removal from an
`ArrayList`, repeatedly shifting the remaining entries and making a large expiry
quadratic.

The opt-in dedicated-server fixture explicitly checks that its test chunk is
resident but not block-ticking. It then submits 150,000 refreshes to one real
`LightBlockEntity` before resuming its update. Both versions use the same fixture
and isolated flat-world setup, with no players or other mods beyond required
runtime dependencies.

| Observation | 3.0.70 | 3.0.71 |
| --- | ---: | ---: |
| Retained entries after the paused refreshes | 150,001 | 1 |
| First resumed update | 1,717.842054 ms | 0.053144 ms |
| Refresh loop | 218.532167 ms | 83.310520 ms |

These are individual native reproduction timings, not a prediction of server TPS
or a controlled whole-server benchmark. The bounded retained count is the primary
regression check. Reports: [before](verification/release-3.0.71/light-lifetime-before.json)
and [after](verification/release-3.0.71/light-lifetime-after.json).

## Bounded, equivalent lifetimes

`LightLeases` keeps one remaining lifetime for each of the 16 possible brightness
levels. Multiple contributions with the same brightness are interchangeable: only
the longest remaining lifetime can affect the maximum light emitted on a future
tick. Refreshing that level keeps the maximum remaining duration. Each update
selects its brightness before decrementing, matching the previous final-tick
behavior. Different brightness levels retain independent lifetimes, so a dimmer
source survives a brighter source expiring. Storage and update work stay bounded
regardless of how many reports arrive while the chunk is paused.

Brightness clamping, nonpositive-timeout single-tick contributions, the next-tick
removal of an empty light block, and restoration of water are preserved. No item
IDs, block IDs, saved formats, electrical equations, beam optics, or simulation
rates change. The old report list was runtime-only, so no save migration is needed.

## Passive world access

The shared `LightBlockEntity.addLight` entry point now reads the block state and
block entity through resident-chunk helpers. An absent chunk receives no light
refresh and is never requested. Server-owned temporary light mutation also rejects
client-side and out-of-height requests. Legacy lamp and floodlight ray walks use
the same resident-state helper and stop at absent chunks.

| Dedicated-server operation | 3.0.70 added chunk entries / load events | 3.0.71 |
| --- | ---: | ---: |
| Refresh a light in an absent chunk | 575 / 3 | 0 / 0 |
| Legacy spot beam at a chunk boundary | 0 / 0 | 0 / 0 |
| Floodlight beam at a chunk boundary | 46 / 2 | 0 / 0 |

Chunk entries are the `ServerChunkCache.loadedChunksCount` delta, including
generation dependencies; they are not a count of fully ticking chunks. The spot
beam check verifies its existing boundary behavior while hardening its state-read
path. The central refresh and floodlight cases reproduce unwanted loads before
the fix. Tests used Java 21 and NeoForge 21.1.249; the reported deployment uses
NeoForge 21.1.265 and a larger modpack.

The native overlap test emits 15, 8, 8 for a bright one-tick contribution and a
dimmer three-tick contribution, then restores the original source water. Unit
tests additionally compare 5,000 randomized refresh/update sequences against
individual lifetimes, exercise a million paused refreshes, and verify brightness
and timeout edge cases.

The focused suite passed 85 lighting tests across 18 suites. Native client checks
with Sodium, Iris, and Distant Horizons passed wired/wireless pool, party, and deck
fixtures, aimed heads, source-water restoration, and powered/off string rendering.
See the [native report](verification/release-3.0.71/native-lighting.json),
[lit strings](verification/release-3.0.71/suspended-deck-strings-lit.png), and
[unpowered strings](verification/release-3.0.71/suspended-deck-strings-off.png).

The fixture is disabled unless `-Deln.lightLifetimeChecks=true` is set. It writes
its report and stops the server; use it only in an isolated test world.

The full Gradle build passed with 1024 tests across 248 suites and
zero failures, errors, or skips. See the [full-suite report](verification/release-3.0.71/full-suite.json).
