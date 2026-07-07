# Musicarr for Android

A native Android client for a self-hosted
[Musicarr](https://github.com/benjamin-decreusefond/Musicarr) server. The app
holds no music and no state of its own — it signs in to your server and
browses, downloads and streams everything from there.

Works on **phones/tablets** (touch, bottom navigation) and on **Android TV**
(leanback launcher entry, D-pad focus, side navigation rail) from a single APK.

## Features

- **Sign in** with your Musicarr username/password; the session cookie is
  stored on-device and reused (including by the audio streamer), so you stay
  signed in across restarts.
- **Home** — Made-for-you mixes, recently played, and trending tracks /
  albums / artists from your server's Deezer-backed feed.
- **Search** the Deezer catalog through your server, with on-server
  availability flagged per track.
- **Library** — the shared on-disk library (tracks, albums, artists) plus your
  liked songs.
- **Playlists** — your own and ones shared with you; play or shuffle a whole
  playlist.
- **Album & artist pages** — play what's on the server, queue a Soulseek
  download for what isn't (single tracks or whole albums).
- **Downloads** — live view of the server download queue with progress,
  retry and dismiss.
- **Playback** — queue playback via Media3/ExoPlayer streaming
  `/api/stream/:id` (with HTTP range support), background playback with a
  media notification, lock-screen/media-key controls, shuffle & repeat, and an
  Up Next queue. Every play is reported to the server so history, stats and
  On Repeat keep working from this client.

## Building

Requirements: JDK 17+, Android SDK (platform 35). Then:

```bash
./gradlew :app:assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Install on a phone or an Android TV device (both use the same APK):

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Connecting

On first launch enter your server's URL (e.g. `http://192.168.1.10:8686`),
username and password. Plain-HTTP servers on a LAN work out of the box
(`usesCleartextTraffic` is enabled); use HTTPS if your server is reachable
from outside your network.

## Architecture (short version)

- **Kotlin + Jetpack Compose (Material 3)** — one UI for phone and TV; the
  TV form factor is detected at runtime (`FEATURE_LEANBACK`) and swaps bottom
  navigation for a focusable side rail. All interactive items carry a visible
  D-pad focus ring.
- **Retrofit + OkHttp + kotlinx.serialization** — thin typed client over the
  Musicarr REST API. A persistent cookie jar keeps the `musicarr_session`
  cookie; the same OkHttp client backs ExoPlayer's HTTP data source so stream
  requests are authenticated too.
- **Media3** — `MediaSessionService` + ExoPlayer for background/TV playback;
  the UI talks to it through a `MediaController`.
- No local database: the server is the single source of truth, matching the
  server's own "no offline mode" stance.
