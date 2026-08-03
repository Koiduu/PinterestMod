# PinSpo

Client-side Fabric mod for Minecraft 1.21.11 that embeds a real Pinterest browser in-game and lets you
turn any pin into a persistent picture-in-picture build reference.

## Usage

- **M** — opens the Pinterest browser (or, when an image is already pinned, the PinSpo settings screen).
- Search, then click a pin to make it your reference overlay.
- In the embedded browser, **Shift + Right-Click** an image to pin it.
- **Escape** — closes the current screen without changing the pin.

**M** opens a native Pinterest search grid: it queries the same JSON endpoint pinterest.com's own web
app uses (no login needed) and draws the results as plain textures, so finding a reference costs no
Chromium at all. Click a pin to make it your overlay. The **Browser** button still opens the embedded
Chromium browser for anything the grid can't do (logging in, your own boards).

The embedded browser opens as a centred window (70% of the screen by default) over a dimmed backdrop; clicking
the backdrop closes it. It renders at a capped resolution (`maxBrowserWidth`, 960 by
default) because off-screen painting cost scales with pixel count. "Browser window size" and "Browser
quality" in the settings screen are the two knobs to turn if browsing feels slow.

Settings (opacity, size, screen corner, offsets, original-resolution preference) are stored in
`config/pinspo.json`. The pinned image itself survives restarts too: its bytes are cached under
`config/pinspo/images` and re-pinned on startup without hitting the network.

## Requirements

- Minecraft 1.21.11, Fabric Loader 0.19.3+, Fabric API
- [MCEF Modern](https://modrinth.com/project/mcef-modern) — supplies the embedded Chromium runtime.
  It downloads native Chromium binaries (~150 MB) the first time the browser is opened.

## Building

```
./gradlew build     # requires JDK 21
```

The mod jar is written to `build/libs/`.

## Notes

- Chromium is only initialized the first time you press **M**; while the browser screen is closed the
  browser stops rendering, and it is disposed on disconnect or after `idleDisposeMinutes` of disuse.
- Chromium's cookie/cache directory is managed by MCEF Modern
  (`config/mcef-modern/cache`), which is what makes the Pinterest login survive restarts.
- The overlay is purely visual and click-through; only one image can be pinned at a time.
