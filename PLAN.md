# Notscape Mariner — Development Plan

**Status: beta — in active development**

Notscape Mariner is an Android web browser built around reading, watching, and
listening. It is **local-only and private by design**: bookmarks, history,
playlists, books, and passwords are stored on the device, and the app is built
to work entirely on-device.

- Package: `net.notscape.browser`
- Platform: Android 7.0 (API 24) and newer
- Stack: Kotlin, Jetpack Compose, Android WebView, Media3

## Design principles

1. **On-device first.** All user data lives on the device, in app-private
   storage. Features are designed to work without any server.
2. **Reading and media are core.** The book reader, playlists, and offline
   media are first-class features, with the same depth as browsing itself.
3. **Calm interface.** Appearance settings, wallpapers, and reader themes are
   user-controlled, with sensible defaults and no clutter.

## Feature scope

### Browser
- Tabs in grid or list view, with named tab groups and private tabs
- Start page: favourites, frequently visited sites, wallpaper (including
  a custom photo), appearance settings
- Bookmarks with folders and day-grouped history
- Per-site controls and permissions
- Ad and tracker blocking from maintained filter lists (validated
  before they are applied), cosmetic hiding, cookie controls,
  hardened engine settings
- Save a page as PDF, screenshots, find in page, link cleaning

### Reader
- Bookshelf for EPUB and TXT books, list or cover-tile views
- Chapter navigation and table of contents, in-book search, ribbons
- Read-aloud with downloadable offline voices, follow-along
  highlighting, and a sleep timer
- Typesetting: font family and size (including imported fonts), line
  spacing, margins, alignment
- Themes, two-page spread with a page-curl turn in landscape,
  keep-screen-awake, time-left estimates
- Reading progress, always saved
- A reading mode that presents web articles in the reader, with an
  option to save articles to the bookshelf

### Media
- Media detection on web pages, including embedded players and streams
- Playlists with multi-select tools
- Offline downloads (including assembled HLS streams) and an offline
  library
- Video player: gesture controls for brightness, volume, and seeking;
  playback speed control; picture-in-picture; video quality selection;
  styled subtitles
- Listen mode: audio-only playback with a sleep timer

### Vault
- On-device password vault
- Android Keystore encryption, biometric unlock, automatic relock
- Search, import, and export (encrypted export with a passphrase)

## Roadmap

- **Phase 1 — Browser shell.** ✅ Tabs, start page, bookmarks/history, blocking,
  settings and appearance. Installable builds via GitHub Actions.
- **Phase 2 — Media.** ✅ Media detection, playlists, offline downloads and
  library, video player with gestures, listen mode and sleep timer.
- **Phase 3 — Reader.** ✅ Bookshelf, EPUB/TXT import, reading view with
  the full typesetting and theme system, article reading mode,
  read-aloud.
- **Phase 4 — Vault and polish.** ✅ Password vault, per-site controls,
  data management.
- **Phase 5 — Release preparation.** In progress: polish, device
  testing, documentation, and verified in-app updates. A first
  release candidate (version 0.1.0) follows once the beta has had its
  refining pass — it is not imminent.

## Testing

- Primary test device: Pixel 10a
- CI: GitHub Actions builds a signed release APK on every push and
  publishes it as a beta GitHub release.
