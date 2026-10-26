# 3.0.58: Charger slots, cabinet facing and bundled media tools

The EV Pack Charger now draws its empty battery slot above the Apply buttons, with a
"Battery / pack" label. The pad transformer and welding station also draw their inventory
slot backgrounds. Slot acceptance and stack limits remain unchanged.

Stationary industrial cabinet models now face the player when placed, including the charger
and web jukebox. Reflecting the model fronts preserves the existing left/right electrical
terminal positions. The stationary bodies and item previews retain their baked rendering.

FFmpeg, yt-dlp and QuickJS-NG are included inside the jar for Windows x64, Linux x64/ARM64
and macOS x64/ARM64. No separate installation or runtime tool download is needed. The first
relevant playback extracts and verifies the matching local executables on the audio worker.
Internet access is still needed for the selected online song. See the
[jukebox guide](powered-web-jukebox.md) for platform support, caching and optional overrides.

The tool archives add 281,854,000 bytes, and the verified local mod jar is 295,100,697 bytes
(approximately 281 MiB). Original third-party license texts, distributor records and source
locations accompany the programs in `assets/eln/media-runtime/`. The refresh script pins
publisher release hashes; executable and archive checksums are both checked during extraction.

## Verification

**27 focused tests pass across three suites**, with no failures, errors or skips:
[results](verification/release-3.0.58/units-final.json). They cover the existing industrial
device/audio behavior, all fourteen unique bundled archive hashes, five platform manifests,
cache repair, rejection of corrupted resources and platform detection. On Linux x64, all three
bundled executables run with an empty PATH. FFmpeg converts MP3 to Vorbis, which Minecraft's
audio decoder successfully reads.

An additional offline yt-dlp invocation recognizes QuickJS-NG 0.17.0 and the embedded
`yt_dlp_ejs` package, using local sample metadata without requesting remote media:
[result](verification/release-3.0.58/media-runtime-offline.json). This verifies runtime
configuration; it does not assert live YouTube playback or execution on other operating systems.
All fourteen tool archives were also read back from the assembled jar and checked against their
pinned hashes: [packaging audit](verification/release-3.0.58/packaging.json).

The native Minecraft campaign passes **seven checks** under Sodium/Iris/BSL, including actual
charger and jukebox placement in all four horizontal directions. Ordinary inventory clicks insert
one named pack, real feeder power charges it above 500 kJ, and ordinary removal and shift-clicks
preserve exactly one charged pack. All ten stationary bodies compile into terrain meshes, and
all fourteen new item previews remain baked. [Native report](verification/release-3.0.58/native.json).

- [Visible empty charger slot](verification/release-3.0.58/charger-empty-slot.png)
- [Inserted pack charging](verification/release-3.0.58/charger-inserted-pack.png)
- [Visible pad transformer/fuse slots](verification/release-3.0.58/gui-pad.png)

Run the opt-in native check with `-Deln.chargerSlotChecks=true`. It creates a separate test world,
writes its report and screenshots under `run/client/charger-slot-checks/`, and exits. Do not set
that flag for normal play. These checks verify behavior and rendering participation, not FPS.

Source-only prerelease; the compiled mod jar is left for the user to attach. Build using a full
Java 21 JDK:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.58.jar`. Update both client and server.
[Ubuntu instructions](building-on-ubuntu-24.04.md).
