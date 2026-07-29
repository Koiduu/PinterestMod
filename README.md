# PinSpo

Client-side Fabric mod for Minecraft 1.21.11 that embeds a real Pinterest browser in-game and lets you
turn any pin into a persistent picture-in-picture build reference.

## Usage

- **M** — opens the Pinterest browser (or, when an image is already pinned, the PinSpo settings screen).
- Browse/scroll/type in the embedded page like a normal browser tab; log in once, the session persists.
- **Shift + Right-Click** an image — closes the browser and pins that image as an overlay.
- **Escape** — closes the browser screen without changing the current pin.

Settings (opacity, size, screen corner, offsets, original-resolution preference) are stored in
`config/pinspo.json`.

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
