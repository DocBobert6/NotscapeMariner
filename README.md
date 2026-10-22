# Notscape Mariner

A local-only, privacy-first Android web browser with ad and tracker blocking,
a built-in media player, downloads, an e-book reader, playlists, and an
encrypted password vault — all running entirely on your device.

## Overview

Notscape Mariner is a full web browser built around reading, watching, and
listening. Bookmarks, history, playlists, books, downloads, and passwords are
stored on the device only. There are no accounts, no analytics, and no
telemetry. The only network requests the app itself makes are downloading
filter lists and checking for updates.

## Features

### Browser
- Tabs with a visual tab grid, private tabs in their own section, and
  session restore
- Floating address bar that can sit at the top or bottom of the screen,
  with an optional home button and a loading progress fill
- Start page with favourites, frequently visited sites, and a choice of
  wallpapers; optional custom homepage
- Bookmarks, history, find in page, share, long-press menus, desktop site
  mode, and an immersive full-screen mode
- Strict popup blocking with a per-site allow list — allowed popups open
  as real tabs, so login and player windows keep working
- Per-site controls for JavaScript, images, popups, and blocking
- Reader mode that presents web articles in the built-in reader

### Blocking and privacy
- Ad and tracker blocking from real filter lists (EasyList, EasyPrivacy,
  and a hosts-based blocklist), downloaded and cached on-device
- Path- and site-scoped list rules are honoured as written, and cosmetic
  rules hide ad containers that network blocking leaves behind
- Blocking can be turned off per site; cookie controls included

### Media
- "Find media on page" detects videos, audio, and streams (including HLS
  and DASH) on the page and in its network traffic
- Built-in player with background playback and system media controls,
  resume positions, gesture controls for brightness, volume, and seeking,
  playback speed, picture-in-picture, a listen mode for audio-only
  playback, and a sleep timer
- Playlists with categories and multi-select tools

### Downloads
- Downloads go to the phone's Downloads folder through the system
  download manager, with correct file names and types resolved from the
  server
- Stream (HLS) downloads are assembled into a single playable file
- Media downloads are kept in an offline library for watching and
  listening without a connection

### Reader
- Bookshelf for EPUB, Mobi, and TXT books
- Chapter navigation and table of contents
- Typesetting controls (font family and size, line spacing, margins,
  alignment), themes, brightness, and page-turn modes
- Reading progress and in-book bookmarks, always saved

### Vault and data
- On-device password vault, encrypted with the Android Keystore and
  unlocked with biometrics
- Export and import a full backup; the vault exports separately,
  encrypted with a passphrase
- In-app update checks against this repository's releases

## Download

Signed APKs are published on the
[Releases](https://github.com/DocBobert6/NotscapeMariner/releases) page
(beta builds). Requires Android 7.0 (API 24) or newer.

## Building from source

Requirements: JDK 17 and the Android SDK (platform 35).

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
- Min SDK 24 · Target SDK 35
- Status: in active development — see [PLAN.md](PLAN.md) for scope and
  roadmap

## License

Personal project — all rights reserved.
