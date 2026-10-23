# Notscape Mariner — Development Plan

**Status: in active development**

Notscape Mariner is an Android web browser built around reading, watching, and
listening. It is **local-only and private by design**: bookmarks, history,
playlists, books, and passwords are stored on the device, and the app is built
to work entirely on-device.

- Package: `net.notscape.browser`
- Platform: Android, arm64
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
- Tabs with a visual tab list
- Start page: favourites, wallpaper, appearance settings
- Bookmarks and history
- Per-site controls and permissions
- Ad and tracker blocking (filter lists), cookie controls

### Reader
- Bookshelf for ePub, Mobi, and TXT books
- Chapter navigation and table of contents
- Typesetting: font family and size, line spacing, margins, alignment
- Themes, brightness, page-turn modes
- Reading progress and bookmarks inside books, always saved
- A reading mode that presents web articles in the reader

### Media
- Media detection on web pages, including embedded players and streams
- Playlists with categories and multi-select tools
- Offline downloads and an offline library
- Video player: gesture controls for brightness, volume, and seeking;
  playback speed control; picture-in-picture
- Listen mode: audio-only playback with a sleep timer

### Vault
- On-device password vault
- Android Keystore encryption, biometric unlock
- Search, import, and export

## Roadmap

- **Phase 1 — Browser shell.** ✅ Tabs, start page, bookmarks/history, blocking,
  settings and appearance. Installable builds via GitHub Actions.
- **Phase 2 — Media.** ✅ Media detection, playlists, offline downloads and
  library, video player with gestures, listen mode and sleep timer.
- **Phase 3 — Reader.** ✅ Bookshelf, ePub/Mobi/TXT import, reading view with
  the full typesetting and theme system, article reading mode.
- **Phase 4 — Vault and polish.** ✅ Password vault, per-site controls,
  data management.
- **Phase 5 — Release hardening.** In progress: signed release builds,
  device testing, release notes.

## Testing

- Primary test device: Pixel 10a (arm64)
- CI: GitHub Actions builds a signed release APK on every push and
  publishes it as a beta GitHub release.
