# Notscape Mariner

**Build 0.0.45**

Notscape Mariner is an Android web browser built around reading, watching, and
listening — with a full book reader, playlists, and offline media as first-class
features, not add-ons.

**Local-only and private.** Everything — bookmarks, history, playlists, books,
and passwords — is stored on your device. Notscape Mariner is designed to work
entirely on-device.

## Features

- **Browser** — Safari-style interface with a switchable top/bottom address
  bar, tab grid, private tabs, session restore, and a start page with
  favourites and continue-watching. Bookmarks and history, per-site controls,
  strict popup blocking, long-press menus, and sharing.
- **Ad and tracker blocking** — real filter lists (EasyList + EasyPrivacy)
  downloaded and cached on-device, with cookie controls.
- **Backup** — export and import your data; the vault exports separately,
  encrypted with a passphrase.
- **Reader** — a complete book reader for ePub, Mobi, and TXT: bookshelf,
  chapter navigation, typesetting controls (font, size, spacing, margins),
  themes, and reading progress that is always saved.
- **Playlists** — collect videos into playlists with categories and selection
  tools.
- **Offline media** — detect media on a page, pick a quality, download it, and
  keep it in an offline library for watching without a connection.
- **Video player** — background playback with system controls, resume
  positions, gesture controls (vertical slides for brightness and volume,
  fast-seek), playback speed control, a listen mode for audio-only playback,
  and a sleep timer.
- **Password vault** — an on-device vault, encrypted with the Android Keystore
  and unlocked with biometrics.

## Project

- Package: `net.notscape.browser`
- Stack: Kotlin, Jetpack Compose, Android WebView, Media3
- Status: in development — see [PLAN.md](PLAN.md) for the roadmap.
- Builds: source is uploaded here and built with GitHub Actions.
