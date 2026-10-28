# PinSpo

Client-side Fabric mod for Minecraft 26.3 that searches Pinterest in a native Minecraft screen and
lets you turn any pin into a persistent picture-in-picture build reference. No Chromium, no browser
runtime — just Pinterest's own image library drawn as plain textures.

## Usage

- **M** — opens PinSpo (or, when an image is already pinned, the settings tab).
- **Search** queries the same JSON endpoint pinterest.com's own web app uses (no login needed). Click a
  pin to make it your reference overlay, right-click for **Send or save**: pick a friend to send it to,
  or a folder to keep it in.
- **Images** covers references that are not from Pinterest: paste an image link (Discord's `cdn.discordapp.com`
  and `media.discordapp.net` links work, as do Pinterest pin pages), or pick a file from your computer.
  Your Minecraft screenshots and everything you imported show up in the grid below, usable like any pin.
- **Saved** holds your folders plus a Recent list of the last five pins.
- **Friends** is a chat with a request layer: type a player's name and press **+** to ask them, and they
  accept or decline with the ✓/✗ buttons on their side before either of you can message the other.
  Requests, messages and references travel as Minecraft private messages (`/msg`), so a reference arrives
  in their PinSpo and one click puts it on their screen. On servers that block `/msg`, **Copy code** /
  **Paste code** does the same by hand.
- **Build** holds the building tools:
  - **Wall photo** — look at a wall, floor or ceiling and press **B**: your pinned image sticks to it.
    Press **B** again to move it to wherever you look; **sneak + B** opens a side panel (opacity, width,
    arrow-key nudging, rotate, "seen by: only me / friends"). Shared photos go to your PinSpo friends
    over `/msg` (Pinterest images only), so they see it on your build too.
  - **Camera points** — press **K** to plant a camera exactly where you stand and look. Each camera gets
    its own live window on the HUD (up to 4 per world); **sneak + K** lets you drag windows around and
    resize them from the red corner.
  - **Colour to block** — open the pinned image (or any file), hover or click a pixel, and get its hex
    plus the closest blocks from your current resource pack.
- **Settings** holds the overlay controls, Build Battle toggles and search personalisation.
- **Escape** — closes the current screen without changing the pin.

There is no account or login: search runs against Pinterest's public search endpoint, so everything works
straight away.

Settings (opacity, size, screen corner, offsets, original-resolution preference, Build Battle mode) are
stored in `config/pinspo.json`. The pinned image itself survives restarts too: its bytes are cached under
`config/pinspo/images` and re-pinned on startup without hitting the network. Imported files are copied into
`config/pinspo/local` so they keep working after you move the original. Folders live in
`config/pinspo-saved.json`, friends and chats in `config/pinspo-friends.json`, wall photos in
`config/pinspo-walls.json` and camera points in `config/pinspo-cameras.json`.

## Requirements

- Minecraft 26.3, Fabric Loader 0.19.3+, Fabric API 0.157.0+26.2

The 1.21.11 build lives on the `main` branch; this branch is the same mod ported to 26.2, which ships
unobfuscated, runs on Java 25, and replaced immediate-mode GUI drawing with the render-state extractor.

## Building

```
./gradlew build     # requires JDK 25
```

The mod jar is written to `build/libs/` as `pinspo-<version>+mc<minecraft version>.jar`. Another Minecraft
version can be built from the same source tree when its API matches, by overriding `minecraft_version`,
`fabric_version` and `minecraft_dependency` on the command line.

## Notes

- The overlay is purely visual and click-through; only one image can be pinned at a time.
- Search uses an internal Pinterest endpoint, so a Pinterest-side change could break it; nothing else in
  the mod depends on it.
- Everything that arrives from outside the game — share codes, chat messages, pin URLs, folder and friend
  names, hand-edited config files — goes through `PinSecurity` first: images may only ever be downloaded
  from Pinterest's own CDN or Discord's attachment CDN, imported files may only ever be read from PinSpo's
  own image folder or your screenshots folder, names cannot contain path separators or formatting codes, friend names must
  match Minecraft's own name shape (so they cannot extend a command), and share codes carry nothing but a
  hex image name, never a URL. Because of that last point, only Pinterest references can be sent to a
  friend; imported files and pasted links stay on your own machine.
