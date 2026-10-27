# Powered Browser Television

The Powered Browser Television is a wide, freestanding 16:9 TV with a working Chromium browser.
Webpages, HTML5 video, direct video streams and video websites render on the placed screen with
stereo audio. The cabinet, stand and inventory preview use baked geometry; only the screen is live.

Connect a 120 V cord or a placed cable to the blue input on the left or bottom. The controls can
select 240 V before attaching a matching 240 V cord. The load is 150 W while on and 2 W in standby
at nominal voltage. It uses passive resistance, so input power follows the actual supply voltage.
An insufficient or lost supply stops the browser; restoring power reloads the selected page.

## Controls

Right-click the TV to open its browser controls. Paste a complete `https://` or `http://` address
and press **Go** or Enter. Click the picture to interact with the webpage. Mouse clicks, dragging,
scrolling, keyboard input and browser text shortcuts are forwarded to Chromium. **Ctrl+L** selects
the address bar. Back, forward and reload act on your local browser.

**TV off** stops playback. **TV on** opens the address in the bar. The voltage button selects
120/240 V when no cord is attached. Minus/plus adjust the TV's volume. Escape closes the controls;
the same browser continues playing on the placed TV.

Go shares the chosen address with nearby viewers. **Share page** shares your browser's current
address after following links. Each client renders its own browser, including cookies and website
controls; typing, account state, playback position and arbitrary webpage actions are not mirrored
to other players. Videos are not frame-synchronized between clients. The server stores the chosen
address and power settings, without downloading webpages or transmitting browser cookies.

## Browser installation and sound

The unmodified MCEF 2.1.6-1.21.1 bridge is included as a nested mod dependency. A separate MCEF mod
installation is unnecessary. On first client launch its normal installer downloads the matching
Chromium runtime, then reuses it and checks the publisher checksum at subsequent launches. Allow
that first setup to finish. Dedicated servers do not install or start Chromium. FFmpeg and yt-dlp
remain bundled for the separate web jukebox; the TV uses Chromium's streaming browser instead.

Audio uses Chromium's normal stereo output device. ELN applies TV volume, distance attenuation,
and Minecraft's Master and Records sliders inside each webpage to HTML5 media and newly connected
Web Audio output. The webpage's own mute button remains independent. Audio is stereo rather than
a directional OpenAL sound. Unusual pages that replace browser APIs or create audio before the
control script is installed can behave differently from ordinary HTML5 video players.

Streaming-site support depends on the website and the embedded Chromium build. JavaScript and
ordinary browser video are supported; this does not guarantee DRM-protected subscription services,
every codec, or websites that require a newer browser. File picker dialogs and external popup
windows are not opened; ordinary HTTP(S) popup links open in the TV's current page instead.

## Performance and checks

The browser viewport is 960×540, with screen texture uploads capped at 24 per second. At most two
TV browsers run per client. Moving beyond 32 blocks, losing power or unloading/breaking the TV
closes its browser and releases its texture and audio resources. Watching streaming video still
has CPU/GPU costs; a live video screen cannot be baked into static terrain.

For the opt-in native test, serve the original local video/audio fixture:

```bash
python3 tools/media/tv_test_server.py
```

Launch the development client with `-Deln.televisionChecks=true` and
`-Deln.tvTestUrl=http://127.0.0.1:8765/index.html`. It creates a separate world and writes its
report and screenshots under `run/client/television-checks/`. Leave these flags off during normal
play. The fixture video can be regenerated with FFmpeg:

```bash
ffmpeg -f lavfi -i testsrc2=size=320x180:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 12 \
  -c:v libvpx-vp9 -deadline realtime -cpu-used 8 -b:v 300k \
  -c:a libopus tools/media/tv_fixture/test.webm
```

The bridge license and source/build links are in `assets/eln/browser-notices/` inside the jar.
