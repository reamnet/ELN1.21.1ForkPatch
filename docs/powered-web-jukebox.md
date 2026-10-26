# Powered Web Jukebox

The jukebox uses 120 V by default, with 240 V selectable in its GUI. Connect a matching appliance
cord or a placed cable to its blue input. Playback enabled draws 25 W; standby draws 2 W.
Loss of power stops audio. Its stationary cabinet and speakers use the baked terrain renderer.

Open the GUI, paste a direct **HTTPS audio URL** or a **YouTube video URL**, and press
**Play / restart**. Stop ends playback; volume adjusts the block's level. Nearby listeners hear a
positional sound within 32 blocks, governed by Minecraft's **Jukebox/Note Blocks (Records)** and
master-volume sliders. Muting those sliders stops web-audio preparation on that client.

Direct **Ogg Vorbis** files work with Minecraft's decoder. Other formats (MP3, AAC, MP4 audio,
Opus and similar) need **FFmpeg on the client computer**. YouTube needs **a current yt-dlp,
its recommended JavaScript runtime, and FFmpeg on the client computer**. Follow the tools'
installation instructions and keep yt-dlp current as YouTube changes its player:

- yt-dlp: <https://github.com/yt-dlp/yt-dlp#installation>
- FFmpeg: <https://ffmpeg.org/download.html>

The tools must be available on the Minecraft launcher's PATH. Optional explicit paths can be
set in the **client** `config/Eln.cfg` JSON (merge this into the existing object):

```json
{
  "media": {
    "ytdlpPath": "yt-dlp",
    "ffmpegPath": "ffmpeg"
  }
}
```

Absolute executable paths also work. On Windows, JSON backslashes must be escaped, or use
forward slashes. On Ubuntu, `sudo apt install ffmpeg` supplies FFmpeg; use the current yt-dlp
installation recommended by its project. The dedicated server needs neither tool: it simulates
power and shares the selected URL, while listeners prepare and play their own audio.

Preparation downloads the selected track on background workers, then plays a local decoded
stream. This avoids network reads on the game, render and audio threads. Only HTTPS media is
accepted; direct requests reject private/local network destinations and redirects to non-HTTPS
addresses. Each source/decoded file is limited to 64 MiB, YouTube selections are limited to one
video of at most one hour, and up to four web jukebox jobs can be active on one client. Temporary
media is removed when playback closes. Downloads/conversions have timeouts and stop when
power is lost, the player leaves range, the block unloads, or Stop is pressed.

This is a song/video-file player rather than an indefinite live-radio stream. A track starts after
preparation, and each client prepares its own copy; frame-exact multiplayer synchronization is
not provided. A completed song shows **Song finished**; press Play to restart. Missing helper
tools and unavailable links are reported in the jukebox GUI. Media tools are optional and are
not automatically installed or bundled with the mod.
