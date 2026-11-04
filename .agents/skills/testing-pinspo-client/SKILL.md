---
name: testing-pinspo-client
description: How to launch and drive the PinSpo Fabric dev client on a VM to test the Pinterest search grid, pinned overlay, and image loading.
---

# Testing the PinSpo dev client

## Launch
```bash
cd <repo> && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 nohup ./gradlew runClient > /tmp/runclient.log 2>&1 &
```
Takes ~60s to reach the title screen. No Mojang login needed (dev auth). Then:
1. Maximize with `wmctrl -r "Minecraft" -b add,maximized_vert,maximized_horz` (never `xdotool key super+Up`).
2. Click "Continue" on the accessibility prompt, then Singleplayer → Create New World (defaults are fine); world load ~25s.
3. In-game press **M** (`PinSpoClient.OPEN_KEY`). If nothing is pinned this opens `PinBrowseScreen` (native search grid); if an image *is* already pinned it opens `PinSettingsScreen` instead — unpin first if you need the grid.
4. Type a query in the top-left edit box and press Enter to search.

## Logs are the primary evidence
`run/logs/latest.log`, logger name `(PinSpo)`. Useful greps: `Could not load thumbnail`,
`Thumbnail download failed`, `Could not decode image`, `Pinterest search failed`.
Note `ThumbnailCache` logs only `e.toString()` (no stack trace); the same decode path in
`PinnedImage` logs a full trace, so trigger a pin click to get the stack.

## Reaching the grid when a pin is already saved
`run/config/pinspo.json` persists `pinnedUrl`, and it is restored on world load, so M opens
`PinSettingsScreen` instead of the grid. Click "Remove pin", then press M again. Escape from the
PinSpo screens drops you into the vanilla Game Menu — click "Back to Game" first.

## Driving the settings sliders
`AbstractSliderButton` ignores fast synthetic click-drags. Use
mouse_move → left_mouse_down → several mouse_move steps → left_mouse_up (no coordinate on the
button-down/up actions). Verify the slider label percentage changed, then close the screen and
screenshot the HUD, since the settings background dim makes the overlay hard to judge in place.

## Known pitfall: JPEG decoding
`NativeImage.read(byte[])` on MC 1.21.11 is PNG-only (`PngInfo.validateHeader` → `java.io.IOException: Bad PNG Signature`).
Pinterest CDN serves `image/jpeg`, so any code path feeding pinimg bytes straight into `NativeImage.read`
will fail even though the HTTP request succeeds — symptom is blank grid cells showing `...` and
"PinSpo: could not download that image." in chat. If images don't appear, check for this before
suspecting network, texture registration, or blit args. A workaround would be decoding via
`javax.imageio.ImageIO` and copying pixels into a `NativeImage`.

## Verifying infinite scroll without visible images
Page size is 25 (`PinterestApi.PAGE_SIZE`). Count unique thumbnail URLs in the log
(`grep -o "236x/[a-z0-9/]*" run/logs/latest.log | sort -u | wc -l`); >25 after scrolling to the
bottom proves a second page loaded.

## Build Battle / chat-triggered features
Since the CHAT event was registered alongside GAME, `/say The theme is: castle` triggers the auto-search;
if it ever stops working, check `PinSpoClient` still registers **both** `ClientReceiveMessageEvents.GAME`
and `CHAT`, and fall back to a system message: `/tellraw @a {"text":"The theme is: castle"}`.
Turn "Build Battle: auto random pin" OFF in Settings when you want the Search screen to open rather than a
random pin. Singleplayer worlds need commands enabled — pause → "Open to LAN" → Allow Cheats ON → Start LAN World.

## Friends tab
Friend list + inbox persist in `run/config/pinspo-friends.json` (a good headless check after a restart).
Self round-trip without a second player: pin a reference in Search (fills `PinHistory`), open Friends, click a
friend row or "Copy current pin" (writes a `PINSPO1:` code to the clipboard), then "Receive code" to import it
back — this creates an inbox row and auto-adds the sender as a friend. Left-click a message pins it and closes
the screen; right-click opens the folder picker; right-click a friend row removes them.

## Layout checks
Screens draw section headers in `render()` at fixed offsets while widgets are placed in `init()`, so header/widget
overlap is a recurring class of bug — always zoom into each panel rather than trusting a full-screen shot.
For a narrow-window pass, use `wmctrl -r "Minecraft" -e 0,50,50,800,600`; note the vanilla tutorial toast covers
the top-right tabs for the first ~30s, so move with W to dismiss it before judging the tab bar.

## Restarting the client safely
`pkill -f devlaunchinjector` can kill the shell running the command. Instead `pgrep -f devlaunchinjector` and
`kill <pid>`, then relaunch detached:
`setsid env JAVA_HOME=... ./gradlew runClient > /tmp/rc.log 2>&1 < /dev/null & disown`.

## Testing the folder sidebar scrollbar
Creating ~20 folders through the UI is slow. Kill the client, seed
`run/config/pinspo-saved.json` with extra folders (copy an existing pin object), relaunch — this also
doubles as the persistence check. The running client rewrites that file, so always edit it while the
client is stopped.

## Devin Secrets Needed
None for the grid/search/friends flows (Pinterest search needs no login). Real Pinterest credentials would be
needed to test a signed-in Account state; without them, the login path answers with
"Pinterest refused that login…" and a `(PinSpo) Pinterest rejected the login with HTTP 429` WARN, which is expected.
