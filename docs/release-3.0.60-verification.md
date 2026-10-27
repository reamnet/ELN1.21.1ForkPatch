# 3.0.60: Optional EA Media split and TV controls fix

Core ELN no longer ships the powered browser TV, web jukebox, MCEF bridge, Chromium assets,
media executables, or their recipes/models/GUI code. Those devices now live in the optional
[EA Media addon](https://github.com/reamnet/EAMedia), initially released as 1.0.0. Core retains
only a small addon API and hidden save reservations for original group 126 slots 9 and 10.
All other IDs and core industrial machines remain unchanged.

With EA Media installed, both devices retain their original `eln:*` registry names, recipes,
saved voltage, volume, URL, energy counter and cord data. Without it, existing devices become
inactive placeholders with no simulation processes. Unknown saved fields are deep-copied so
reinstalling the addon can restore device settings. Their creative entries and recipes are absent.

The TV's black-screen/Go failure came from an inventory-container guard: the original TV opened
a plain browser `Screen`, but its server controls required an `IndustrialContainer`. The new
standalone addon screens use the UUID-addressed node controls with same-dimension/eight-block
validation. Existing industrial inventory controls retain their inventory check. The grid console
can still enable/disable media devices through the small remote-control interface.

Addon static cabinet and item models use the existing baked mesh path via a public marker.
Its video screen remains dynamic. A stale-resource cleanup prevents old media payloads from
leaking into the slim core jar when rebuilding without `clean`.

## Validation

The focused core industrial/save-migration suites pass 22 tests with no failures, errors or
skips: [unit results](verification/release-3.0.60/units.json). Tests check stable hidden IDs,
zero placeholder simulation processes, deep preservation of unknown saved fields/URLs/settings,
item cord removal and unchanged core industrial behavior.

A real core-only client reaches the title screen with Sodium, Iris/BSL and Distant Horizons,
without EAMedia, MCEF classes or a media-runtime resource:
[client check](verification/release-3.0.60/core-client.json).

The addon owns its browser/control/extraction tests and screenshots; see its
[release verification](https://github.com/reamnet/EAMedia/blob/main/docs/release-1.0.0-verification.md).
The core jar is **20,458,804 bytes**, down from 295,355,610 bytes (93.07% smaller), with no
media backends, native archives, MCEF dependency or media recipes/models:
[packaging audit](verification/release-3.0.60/packaging.json).
Both the [core-only dedicated server](verification/release-3.0.60/server-core.json) and the
[server with the 359 KB addon variant](verification/release-3.0.60/server-addon.json) start and
stop cleanly after 40 ticks without initializing Chromium.

Source-only prerelease; compile with a full JDK 21 and attach the jar:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.60.jar`.
[Ubuntu build instructions](building-on-ubuntu-24.04.md). Update both client and server.
