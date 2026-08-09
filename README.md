# PinSpo

Client-side Fabric mod for Minecraft 1.21.11 that searches Pinterest in a native Minecraft screen and
lets you turn any pin into a persistent picture-in-picture build reference. No Chromium, no browser
runtime — just Pinterest's own image library drawn as plain textures.

## Usage

- **M** — opens PinSpo (or, when an image is already pinned, the settings tab).
- **Search** queries the same JSON endpoint pinterest.com's own web app uses (no login needed). Click a
  pin to make it your reference overlay, right-click to save it into a folder.
- **Saved** holds your folders plus a Recent list of the last five pins.
- **Friends** is a chat: add a friend by Minecraft name and send them a reference. Messages travel as
  Minecraft private messages (`/msg`), so the reference arrives in their PinSpo and one click puts it on
  their screen. On servers that block `/msg`, **Copy code** / **Paste code** does the same by hand.
- **Account** signs you in either with your email and password or by logging in with your own browser and
  pasting the `_pinterest_sess` cookie back. Pinterest often answers a password login with a bot check
  (HTTP 429); the browser route is the reliable one.
- **Escape** — closes the current screen without changing the pin.

Settings (opacity, size, screen corner, offsets, original-resolution preference, Build Battle mode) are
stored in `config/pinspo.json`. The pinned image itself survives restarts too: its bytes are cached under
`config/pinspo/images` and re-pinned on startup without hitting the network. Folders live in
`config/pinspo-saved.json`, friends and chats in `config/pinspo-friends.json`.

## Requirements

- Minecraft 1.21.11, Fabric Loader 0.19.3+, Fabric API

## Building

```
./gradlew build     # requires JDK 21
```

The mod jar is written to `build/libs/`.

## Notes

- The overlay is purely visual and click-through; only one image can be pinned at a time.
- Everything that arrives from outside the game — share codes, chat messages, pin URLs, folder and friend
  names, hand-edited config files — goes through `PinSecurity` first: images may only ever be downloaded
  from Pinterest's own CDN, names cannot contain path separators or formatting codes, friend names must
  match Minecraft's own name shape (so they cannot extend a command), and share codes carry nothing but a
  hex image name, never a URL.
