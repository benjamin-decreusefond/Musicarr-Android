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

## Installing without the Play Store

Musicarr is distributed as an APK on the
[Releases](https://github.com/benjamin-decreusefond/Musicarr-Android/releases)
page — no store account, no listing, no review. Every merge to `main`
publishes a new one automatically.

**On a phone or tablet.** Download the APK from Releases in the phone's
browser and open it. Android will ask permission to install from that browser
the first time ("Install unknown apps"); grant it and the install proceeds.
This is a supported flow, not a workaround.

**On Android TV**, where there's no usable browser, pick one of:

- **`adb`, over the network** — enable *Developer options → USB debugging* on
  the TV, then from a computer on the same network:
  ```bash
  adb connect 192.168.1.42:5555     # the TV's IP
  adb install Musicarr-1.2.0.apk
  ```
- **A sideload helper app** — "Downloader" (by AFTVnews) is the usual choice
  on Android TV and Fire TV: type the APK's URL from the Releases page and it
  downloads and installs it, no computer involved.
- **A USB stick** plus any file manager on the TV.

The app registers a leanback launcher entry, so once installed it appears on
the TV home row like any other app.

Note that an unsigned build (one produced before signing secrets were
configured) can only be installed via `adb` — Android refuses to install an
unsigned APK by tap.

## Connecting

On first launch enter your server's URL (e.g. `http://192.168.1.10:8686`),
username and password.

Plain HTTP works for a server on your local network (loopback, RFC1918,
link-local, unique-local IPv6, CGNAT ranges, and `.local`/`.lan`/`.home.arpa`
names). Anything else **must** use HTTPS: the app refuses to send your session
cookie unencrypted to a public host. If your server is reachable from outside
your network, either put it behind TLS or install its certificate on the device
— user-installed CAs are trusted.

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
- **Room** — a local catalogue of the tracks pinned for offline playback, plus
  a queue of listens recorded while offline. It is strictly a cache of server
  state and a replayable queue: the server remains the source of truth, and the
  database is rebuilt from it on the next sync.
- **Media3 `DownloadManager` + `SimpleCache`** — pinned audio, keyed by track
  id rather than URL so a track downloaded at one quality is still a cache hit
  when played at another.

## Releasing

Releases are automatic. Merging a pull request into `main` builds a signed APK
and AAB and publishes them to a GitHub Release, tagging the version as it goes.
The bump level comes from a label on the pull request:

| Label | Effect |
| --- | --- |
| `major` | `X+1.0.0` |
| `minor` | `x.Y+1.0` |
| `patch` | `x.y.Z+1` (the default when no label is set) |
| `no-release` | skip the release entirely |

`versionCode` is the commit count on `main` — monotonic by construction, so it
always satisfies the Play Store's strictly-increasing requirement without any
stored state.

You can also run the workflow by hand from the Actions tab ("auto-release"),
choosing the bump level.

### One-time signing setup

Without these secrets the workflow still runs, but publishes an **unsigned**
APK — installable with `adb install`, not by tapping it on a device, and not
uploadable to Play.

Generate a keystore (keep it somewhere safe and **back it up** — losing it
means you can never update an app already published under it):

```bash
keytool -genkeypair -v \
  -keystore musicarr-release.jks \
  -alias musicarr \
  -keyalg RSA -keysize 2048 -validity 10000
```

Base64-encode it for storage as a secret:

```bash
base64 -w0 musicarr-release.jks   # macOS: base64 -i musicarr-release.jks
```

Then add four repository secrets under **Settings → Secrets and variables →
Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the base64 output above |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | the alias (`musicarr` above) |
| `ANDROID_KEY_PASSWORD` | the key password |

Nothing else is required: no Play Store account, no Google credentials. The
release goes to GitHub Releases, and users install the APK directly.

### Building a release locally

```bash
./gradlew assembleRelease \
  -PversionName=1.2.0 -PversionCode=42 \
  -PkeystoreFile=/path/to/musicarr-release.jks \
  -PkeystorePassword=... -PkeyAlias=musicarr -PkeyPassword=...
```

Omit the keystore properties and you get an unsigned APK, which is fine for
local testing.
