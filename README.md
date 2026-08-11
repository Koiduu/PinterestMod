# PinSpo

Client-side Fabric mod for Minecraft 1.21.11 that searches Pinterest in a native Minecraft screen and
lets you turn any pin into a persistent picture-in-picture build reference. No Chromium, no browser
runtime — just Pinterest's own image library drawn as plain textures.

## Usage

- **M** — opens PinSpo (or, when an image is already pinned, the settings tab).
- **Search** queries the same JSON endpoint pinterest.com's own web app uses (no login needed). Click a
  pin to make it your reference overlay, right-click for **Send or save**: pick a friend to send it to,
  or a folder to keep it in.
- **Saved** holds your folders plus a Recent list of the last five pins.
- **Friends** is a chat: add a friend by Minecraft name and send them a reference. Messages travel as
  Minecraft private messages (`/msg`), so the reference arrives in their PinSpo and one click puts it on
  their screen. On servers that block `/msg`, **Copy code** / **Paste code** does the same by hand.
- **Account** offers three ways in, easiest first:
  1. **One-click login** — opens a local setup page holding a *Log into PinSpo* bookmarklet. Drag it to
     your bookmarks bar once; from then on, clicking it while logged into pinterest.com signs Minecraft in.
     Works in any browser.
  2. Email and password straight through the mod. Pinterest usually answers this with a bot check
     (HTTP 429), so treat it as a long shot.
  3. Logging in with your own browser and pasting the cookie into the session field.

  Searching and pinning work fine without logging in at all; a session only adds your own boards.
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
- The one-click login helper (`PinLoginServer`) binds to `127.0.0.1` on an ephemeral port, only answers
  requests carrying a random per-run token, and shuts down once a session arrives or after 15 minutes. The
  bookmarklet navigates to it rather than fetching, because pinterest.com's CSP forbids page requests to
  localhost.
- Everything that arrives from outside the game — share codes, chat messages, pin URLs, folder and friend
  names, hand-edited config files — goes through `PinSecurity` first: images may only ever be downloaded
  from Pinterest's own CDN, names cannot contain path separators or formatting codes, friend names must
  match Minecraft's own name shape (so they cannot extend a command), and share codes carry nothing but a
  hex image name, never a URL.
