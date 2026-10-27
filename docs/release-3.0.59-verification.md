# 3.0.59: Powered browser television

Adds a Powered Browser Television with a real interactive Chromium webpage, streaming video,
and stereo audio. It accepts 120/240 V appliance cords or fixed cables, draws a passive 150 W
load at nominal voltage while on, and uses 2 W standby. The cabinet, stand, screen surround and
item preview remain baked; only the browser picture updates dynamically.

Right-click to use the address bar, webpage mouse/keyboard controls, back/forward/reload,
TV power, nominal voltage, volume and Share page. Escape closes the controls while the placed
screen keeps playing. The custom browser screen avoids vanilla's backdrop blur over its video.
See the [TV guide](powered-browser-tv.md) for use, installation and limits.

MCEF NeoForge 2.1.6-1.21.1 is bundled as an unmodified nested mod. Its normal client installer
downloads the platform's Chromium runtime at first launch and checks its checksum on later
launches. Servers never browse or install Chromium. The bridge uses its official Maven
coordinate, so compatible copies can be resolved by NeoForge's normal dependency handling.
Bridge/source license notices are included inside the mod jar.

The TV uses a private browser client and Chromium's normal stereo device output, avoiding
the packet-allocation leak in this older bridge's native audio-capture handler. ELN applies
distance, TV volume and Minecraft Master/Records gain within each document to HTML5 media and
new Web Audio output connections, preserving a webpage's own mute control. Browsers close on
power loss, block removal/unload or leaving the 32-block range. There are at most two active
TVs per client, a 960×540 viewport, and at most 24 screen uploads per second.

The new item appends group 126, slot 10; all existing IDs are preserved. The server shares the
selected HTTP(S) address and electrical controls, without downloading webpages or transmitting
client cookies. Browser actions and video playback position remain local to each viewer.

## Verification

**31 focused tests pass across four suites**, with no failures, errors or skips:
[results](verification/release-3.0.59/units-final.json). Tests cover passive TV consumption and
voltage sag, power loss/restoration, saved addresses/settings, cord ratings, old item ID stability,
URL scheme rejection, and actual execution of the TV's JavaScript volume controls using the
bundled QuickJS engine. Existing industrial device and web-jukebox tests also pass.

The native campaign passes **six checks** with Minecraft 1.21.1, NeoForge 21.1.249,
Sodium 0.8.13, Iris 1.8.14-beta.1, BSL v10.1.8 and Distant Horizons 3.3.3. It streams an original
local WebM test video through real Chromium, observes advancing frames and nonzero decoded
audio samples, and measures approximately 150 W from a real placed-cable supply. Actual mouse
clicks and typed characters reach the webpage. Closing the controls retains the same browser;
removing/restoring electrical power stops/recreates it; moving beyond range releases it.
[Native report](verification/release-3.0.59/native.json).

- [TV playing in the world](verification/release-3.0.59/tv-playing-world.png)
- [Interactive browser controls](verification/release-3.0.59/tv-browser-controls.png)
- [TV with supply removed](verification/release-3.0.59/tv-power-off.png)

The audio sample check uses a headless audio configuration and does not claim listening through
a physical speaker. Native execution is verified on Linux x64. Live YouTube playback, every
streaming site/codec, DRM subscription services and other operating systems are not asserted.
These are correctness and rendering checks, not an FPS benchmark.

The assembled jar is audited for its nested MCEF dependency, TV classes/model/recipe and
browser resources: [packaging audit](verification/release-3.0.59/packaging.json).
The nested bridge matches the original publisher release SHA-512. A dedicated server also
starts successfully and stops cleanly after 40 ticks, without initializing or installing
Chromium: [server check](verification/release-3.0.59/server-boot.json).

Source-only prerelease. Compile with a full Java 21 JDK and attach the resulting jar:

```bash
./gradlew --no-daemon --max-workers=2 build
```

Output: `build/libs/ElectricalAge-3.0.59.jar`. Update both client and server.
[Ubuntu instructions](building-on-ubuntu-24.04.md).
