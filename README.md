<p align="center">
  <img src="assets/icon.png" alt="Notscape Mariner" width="160">
</p>

<h1 align="center">Notscape Mariner</h1>

<p align="center">
  A reading- and media-first Android web browser — private and local-only
  by design, with ad and tracker blocking, a built-in media player and
  downloads, a full e-book reader with read-aloud, playlists, an
  encrypted password vault, and a retro games library. Everything
  runs on your device.
</p>

<p align="center">
  <a href="https://github.com/DocBobert6/NotscapeMariner/releases">
    <img src="https://img.shields.io/github/v/release/DocBobert6/NotscapeMariner?display_name=tag&label=latest%20beta" alt="Latest beta release">
  </a>
</p>

## Status

**Beta — in active development.** Signed beta builds are published on the
[Releases](https://github.com/DocBobert6/NotscapeMariner/releases) page,
and the app checks for updates itself: an available update is offered
in-app, and its download is verified against the release's published
hash and signing certificate before it is installed. Features are still
being refined, so expect regular updates.

## Overview

Notscape Mariner is a full web browser built around reading, watching, and
listening. Bookmarks, history, playlists, books, downloads, and passwords
are stored on the device only. There are no accounts, no analytics, and
no telemetry. Beyond the pages you
open, the app itself only reaches the network to download filter lists,
check for updates, fetch a search engine's icon, download a
read-aloud voice when you install one, and fetch box art for games
you import.

## Features

### Browser
- Tabs in a grid or list view, with named tab groups, a separate section
  for private tabs, recently closed tabs, and session restore
- Floating address bar that can sit at the top or bottom of the screen,
  with an optional home button and a loading progress fill
- Start page with favourites, frequently visited sites, and a choice of
  wallpapers — including your own photo, adjustable to frame; optional
  custom homepage
- Bookmarks with folders, history grouped by day, find in page, save a
  page as PDF, screenshots, share, long-press menus, desktop site mode,
  text zoom, and an immersive full-screen mode
- Strict popup blocking with a per-site allow list — allowed popups open
  as real tabs, so login and player windows keep working; pop-under
  navigations are caught as well
- Per-site controls for JavaScript, images, popups, and blocking
- Tracking-parameter cleaning for copied and shared links
- Reader mode that presents web articles in the built-in reader, with an
  option to save articles to the bookshelf
- User scripts that run on matching pages, managed on-device
- Home-screen shortcuts that open a site in a chromeless app mode

### Blocking and privacy
- Ad and tracker blocking from seven maintained filter lists (including
  EasyList, EasyPrivacy, AdGuard Base, and a hosts blocklist),
  downloaded and cached on-device
- Downloaded lists are validated before they can replace the copies
  already on disk, and the previous good lists are kept to roll back to
- List rules are honoured as written — path- and site-scoped rules,
  rule exceptions, and cosmetic rules that hide ad containers network
  blocking leaves behind
- Blocking can be turned off per site; cookie controls and per-site
  cookie exceptions included
- Private tabs run in their own browsing profile and leave nothing
  behind
- Hardened browsing engine: pages get no access to device files, no
  geolocation, and no mixed content; Safe Browsing is on where the
  platform offers it

### Media
- "Find media on page" detects videos, audio, and streams (including
  HLS and DASH) on the page and in its network traffic, with a shortcut
  in the address bar when media is found
- Built-in player with background playback and system media controls,
  resume positions, gesture controls for brightness, volume, and
  seeking, playback speed, picture-in-picture, a listen mode for
  audio-only playback, and a sleep timer
- Video quality selection where a stream offers more than one quality
- Subtitle styling: font (including your own imported font), size,
  colour, background, and edge style
- Playlists with multi-select tools

### Downloads
- Downloads go to the phone's Downloads folder through the system
  download manager, with correct file names and types resolved from
  the server
- Stream (HLS and DASH) downloads are assembled into a single playable
  file, including encrypted streams; a download that fails says why
- Media downloads are kept in an offline library for watching and
  listening without a connection

### Reader
- Bookshelf for EPUB and TXT books, with list or cover-tile views
- Chapter navigation and table of contents, in-book search, and
  bookmarks (ribbons) inside books
- Read-aloud with downloadable offline voices (two voice engines, a
  choice of voices and speeds), follow-along highlighting, and a sleep
  timer — playback continues in the background while you stay in the reader; leaving the reader pauses it
- Scrolling or page-by-page reading; in landscape, a two-page spread
  with a page-curl turn
- Typesetting controls: font family and size (including your own
  imported fonts), line spacing, margins, alignment; reading themes;
  keep-screen-awake; time-left estimates
- Reading progress is always saved

### Games
- A retro games library: Game Boy, Game Boy Color, Game Boy Advance,
  NES, Super Nintendo, Genesis, Master System, and Game Gear
- Bring your own ROMs — imports are copied into the app's storage,
  and nothing ships with the app but the emulator cores (which are
  separate components under their own licences); box art is fetched
  per game from the libretro thumbnail server and cached on-device
- Touch controls in the video player's style, with per-console and
  per-orientation layouts you can edit on screen (drag to move,
  resize to the exact pixel, move the D-pad and face buttons as
  clusters), adjustable haptic strength, and adjustable control opacity
- Save states: four slots per game plus an auto-state written when
  you leave, each with a frame thumbnail; battery saves are kept
  per game
- Bluetooth controllers: DualShock 4, DualSense, Xbox One
  (Bluetooth models), and Xbox Series pads, with a positional button
  mapping, hat switch and left stick as a D-pad, hot-plug detection,
  and touch controls that stay live alongside

### Vault and data
- On-device password vault, encrypted with the Android Keystore and
  unlocked with biometrics; it locks itself again automatically
- Logins can be filled into web pages from the vault
- Export and import a full backup, optionally encrypted with a
  passphrase; the vault exports separately, encrypted with a passphrase
- A storage view shows how much room books, voices, filter lists, and
  the cache use, with a one-tap cache clear
- In-app update checks against this repository's releases, with hash
  and signature verification before installing

## Download

Signed APKs are published on the
[Releases](https://github.com/DocBobert6/NotscapeMariner/releases) page
(beta builds). Requires Android 7.0 (API 24) or newer.

## Building from source

Requirements: JDK 17 and the Android SDK (platform 36).

```sh
./gradlew :app:assembleRelease
```

Release signing is supplied through environment variables
(`ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD`); without them the build produces an unsigned APK.
Builds pushed to this repository are compiled by GitHub Actions, which
also publishes the signed APK to Releases.

## Project

- Package: `net.notscape.browser`
- Stack: Kotlin, Jetpack Compose, Android WebView, Media3 (ExoPlayer)
- Min SDK 24 · Target SDK 36
- Status: beta, in active development — see [PLAN.md](PLAN.md) for
  scope and roadmap

## License

Licensed under the GNU General Public License v3.0 — see
[LICENSE](LICENSE). Free to use, share, and modify; anything built
from it must stay open under the same license.

