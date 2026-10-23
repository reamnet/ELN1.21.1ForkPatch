# 3.0.40 ambient climate and thermal-monitor performance

The supplied server profile places about 1.75 ms of ELN's 2.75 ms sampled cost
inside biome-based ambient temperature lookup. Thermal watchdogs repeatedly ask
for the same location's climate. Minecraft's ordinary `getBiome` delegates to
`getChunk(..., BIOMES, false)`, which can still wait for an existing pending
chunk future and process other queued chunk-load tasks while waiting. The
profile includes that wait and another mod's chunk-load scanning beneath it.

Ambient sampling now:

- Reuses the complete climate result for an exact XYZ position within the same
  world/climate frame. Multiple devices, watchdogs, publication and instrument
  reads at one position share the result.
- Refreshes the biome holder/profile once per 20 game ticks per position.
  Daylight, weather, altitude and thermal-load changes are not reduced to that
  interval: climate is recomputed each game tick, and day/time or weather
  changes also start a new frame within the tick.
- Preserves Minecraft's normal three-dimensional, seed-dependent biome zoom.
  On the server its source uses `getChunkNow`, never a blocking chunk request.
  An unloaded location retains its last known biome; a previously unseen one
  uses the generator's noise-biome source without generating a chunk. Once
  loaded, biome changes are picked up within one second.
- Bounds the position cache to 16,384 entries per world with least-recently-used
  eviction. Worlds have separate weakly keyed caches; values retain no Level
  or chunk reference. Client/server worlds with the same dimension key do not
  share weather frames.

The thermal solver, heat input, cooling, watchdog intervals, absolute temperature
limits and destruction behavior are unchanged. Ambient parameters changed in
configuration refresh on the next game tick. Loaded biome edits refresh within
one second; an edited biome in an unloaded chunk becomes visible when that
chunk is available. There are no new chunk tickets for climate monitoring.

## Native comparison

The opt-in campaign first runs against the original 3.0.39 climate service,
then against this version. It creates actual ServerLevel chunks with a
three-dimensional mix of Plains, Desert and Frozen Ocean biomes and checks
512 exact positions at eight heights. It repeats the samples at clear noon,
rainy sunset, stormy night and after an in-place biome edit, then samples two
unloaded positions.

All **2,050 recorded climate values match the original service exactly**,
including humidity, precipitation type, day blend and depth temperature.
The new unloaded-location samples caused **zero chunk loads and zero chunk-load
events**. Completely absent chunks also caused no loads in the baseline;
the blocking case in the supplied profile involves a pending chunk request.
The new server biome source has no path through that blocking request API.

For timing, 1,200 real ThermalLoadWatchDog instances read their ambient provider
six times per frame over 20 explicitly advanced daylight frames. Three trials
warm up the code; the next five are measured on the server thread. Both versions
retain the same checksum and climate data.

| Targeted thermal-watchdog workload | Median time |
| --- | ---: |
| Original 3.0.39 | 53.97 ms |
| Cached 3.0.40 | 16.15 ms |

That is about **70% less CPU time for this workload** on the development host.
It is not a measurement of whole-server tick improvement or client FPS. This
profile is server-side; client model drawing remains a separate cost.

Run with a working development-client display:

```bash
# Against 3.0.39's climate service, with the opt-in probe present:
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.climateChecks=true -Deln.climateOutput=climate40-before.json" ./gradlew --no-daemon --max-workers=3 runClient
# Against 3.0.40, retaining the reference report in run/client:
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Deln.climateChecks=true -Deln.climateOutput=climate40-after.json -Deln.climateExpected=climate40-before.json" ./gradlew --no-daemon --max-workers=3 runClient
```

The probe exits on completion. Check the JSON's failure field as well as Gradle's
exit code. [Native results](verification/release-3.0.40/native.json) retain the
individual trial times, full distinct climate states and per-position indices.

Unit regressions check sharing, frame refresh, the one-second biome refresh,
clock rollback, exact position keys, bounded eviction, world isolation and
immediate visibility of a changing thermal load to its watchdog.

All **824 unit tests passed**, with zero failures, errors or skips across
**203 suites**. See [unit results](verification/release-3.0.40/units.json).

Build `build/libs/ElectricalAge-3.0.40.jar` with JDK 21 using the
[Ubuntu build instructions](building-on-ubuntu-24.04.md).
