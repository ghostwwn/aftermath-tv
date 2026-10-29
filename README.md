# aftermath-tv

retro early-2000s satellite-box android tv client for watching **toonami aftermath** live channels, complete with an interactive channel guide, corner pip video, crt scanlines, and live show metadata.

![Aftermath TV Guide Preview](assets/preview.png)

i wanted a proper couch-friendly way to watch toonami aftermath and classic retro cartoon streams on a tv box without dealing with clunky web browsers or kodi menus — so i built a native android tv app styled after an early-2000s digital cable / satellite receiver.

> **disclaimer**: this is an independent, unofficial fan project. it is **not affiliated with, endorsed by, or connected to** toonami aftermath, toonami, adult swim, cartoon network, nickelodeon, disney, warner bros. discovery, paramount, vaughn live, or any other network or studio. this repo contains no video or copyrighted media; the app is strictly a player that opens public stream urls listed in `Config.kt`.

## what you need

- **android tv / google tv box** (shield, chromecast with google tv, onn 4k, fire tv stick, or any android 7.0+ tv device)
- **jdk 17** and **android sdk (platform 34)** if you're building from source
- **omdb api key** *(optional)* — free from `omdbapi.com` for imdb ratings and plots. if you skip it, the guide automatically falls back to tvmaze with zero setup.

## setup

### option 1 — sideload the prebuilt apk (fastest)

grab `AftermathTV-release.apk` from the **releases** tab (or the latest github actions run) and push it over adb:

```bash
adb connect <your-tv-ip>:5555
adb install -r AftermathTV-release.apk
```

or drop the release url into the **downloader** app on your tv.

### option 2 — build from source

```bash
git clone https://github.com/ghostwwn/aftermath-tv.git
cd aftermath-tv
gradle assembleDebug assembleRelease
```

the apks land in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`. pushing to github also triggers the included actions workflow, which compiles both apks automatically and attaches them to github releases whenever you push a `v*` tag.

## first run (foolproof & dummy-proof)

1. the app powers on like an old crt tube (black screen, horizontal white line, full flash).
2. on first launch, if no omdb key is hardcoded, a retro d-pad on-screen keyboard pops up asking for one.
3. type your key with the remote **or just hit `SKIP`** — the guide works out of the box using tvmaze.
4. your key is saved locally on the device (`SharedPreferences`) and is never committed to code. press `Menu` / `Settings` on your remote anytime while watching to change it.

## usage & remote controls

| screen | key | action |
| :--- | :--- | :--- |
| `watching` | **OK / Enter** | open the interactive satellite channel guide |
| `watching` | **Left / Right** | channel down / channel up (with crt static burst) |
| `watching` | **Up** | toggle crt scanlines on / off |
| `watching` | **Down** | force reload current stream |
| `watching` | **Info** | pop up the current channel & show progress banner |
| `watching` | **Menu / Settings** | open the omdb api key setup screen |
| `guide` | **D-pad** | browse channels and upcoming time slots |
| `guide` | **OK / Enter** | tune to selected channel (or close guide if already tuned) |
| `guide` | **Back** | close guide and return to fullscreen video |

## how it works (roughly)

1. **channel discovery** — pulls the live toonami aftermath channel list (`est`, `pst`, `movies`, `radio`) on launch and merges in any direct `.m3u8` streams from `Config.EXTRA_CHANNELS`.
2. **stream resolution & playback** — queries the stream endpoint for the active url, hands `.m3u8` feeds to exoplayer's `HlsMediaSource` (with a live offset buffer for third-party servers so slow streams don't stall), and routes audio/icecast feeds through `DefaultMediaSourceFactory`.
3. **schedule engine** — fetches rolling time windows from the public schedule api for east/west and merges local timezone-aware weekly grids (`assets/retroblast.json`) for custom channels.
4. **metadata & poster lookup** — queries omdb (if a key is set) or tvmaze in a background thread pool as you move through the grid, caching show plots, release years, ratings, and poster bitmaps in memory.
5. **custom canvas ui & 3d offline scene** — zero bloated xml layouts. the satellite guide, channel banner, crt scanlines, and the 90s-style tumbling 3d polyhedron "stream is offline" screen are all rendered natively at 60fps on android `Canvas` with automaticexponential backoff reconnects.

## extra channels & retro blast schedule

`Config.kt` ships with five extra third-party streams included with permission from their operators:
- **Retro Blast** (`retroblast.tv`) — paired with the bundled `app/src/main/assets/retroblast.json` weekly schedule grid (eastern time), including block tags (toonami, adult swim, miguzi, boomerang) and dated halloween specials.
- **Vaughn Live classics** (`cnclassic`, `thatsodisney`, `nickpicks`, `nnretro`) — 24/7 classic cartoon streams.

to update `retroblast.json` from a new weekly pdf sheet of the same layout:

```bash
pip install pdfplumber
python3 tools/retroblast_pdf_to_json.py new_schedule.pdf app/src/main/assets/retroblast.json
```

## troubleshooting

| problem | thing to try |
| :--- | :--- |
| `"this stream is offline"` screen | wait a few seconds — the app automatically retries with a growing delay (`3s -> 30s`). press **Down** to force an immediate reload |
| `"Invalid API key!"` on omdb setup | new free keys from `omdbapi.com` have to be activated via the confirmation link in your email before they work. choose **SKIP** to use tvmaze in the meantime |
| `schedule unavailable` in guide | the schedule endpoint might be temporarily rate-limiting or down; the guide refreshes automatically every ~20 minutes |
| `remote has no Menu key` | clear the app data in android tv settings to bring back the first-run omdb key prompt |

## config

everything tweakable lives in `app/src/main/java/com/personal/aftermathtv/Config.kt`:

- `GUIDE_THEME` — `"gray"` (2019-style silver cells + green selection, default) or `"blue"` (classic hd lavender cells + gold selection)
- `BOOT_ANIMATION` — `true` / `false` to toggle the crt power-on effect at launch
- `WORDMARK` — header text rendered in the top-left of the guide (`"aftermath tv"`)
- `OMDB_KEY` — optional default omdb key (leave empty before committing!)
- `EXTRA_CHANNELS` — list of `ExtraChannel` entries for direct `.m3u8` streams
- `STREAM_URL_OVERRIDE` — force a single `.m3u8` url to bypass the channel api

## anti-skid & attribution

this was built from scratch for the community. if you fork, redistribute, or build on top of this repo, keep the copyright notice and attribution intact — don't strip credits and claim it as your own.

## license

mit — do whatever you want with it, but don't strip credits. see `LICENSE`.
