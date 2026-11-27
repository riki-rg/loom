# Loom

Android music player for YouTube Music and SoundCloud. Kotlin/Compose UI, Rust core over JNI.

## Build

```bash
bash build-core.sh      # cross-compiles Rust -> app/src/main/jniLibs/{arm64-v8a,armeabi-v7a}
./gw assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

`gw` uses local JDK 21 + Gradle + Android SDK; no wrapper jar needed. Requires `ANDROID_HOME` set to an SDK with platform 35, build-tools 35.0.0 and NDK 27.2.12479018.

## How the sources work

All three are unofficial APIs; none of the services offers a public audio API.

- **YouTube Music** — search via `WEB_REMIX` InnerTube. Audio via the `VISIONOS` client, which returns direct `googlevideo` URLs. It only does so when the request carries a `visitorData` id fetched from `youtubei/v1/visitor_id`; without it every track answers `LOGIN_REQUIRED: Sign in to confirm you're not a bot`. Other clients (ANDROID_VR, WEB_REMIX, IOS, ANDROID_MUSIC, TVHTML5, MWEB) were tested and fail on label tracks. Home shelves, playlists and autoplay come from `youtubei/v1/browse` and `youtubei/v1/next`; the radio queue id is `RDAMVM<videoId>`.
- **Spotify** — search uses the anonymous token from `open.spotify.com/embed/api/token` plus the internal Pathfinder `searchDesktop` query. Playback is Spotify's 30-second preview MP3 only; full tracks need OAuth with a Premium account. The persisted-query hash in `SP_SEARCH_HASH` rotates, so search breaks when Spotify updates it.
- **SoundCloud** — `client_id` is scraped from the site at runtime rather than baked in, since it rotates. SoundCloud blocks datacenter IPs, so this path is untested from a server and may still 403 from some networks.

## Versioning

`0.<feature>.<fix>`. Bug fixes bump the last number; features bump the middle one; when the middle reaches 9, bump the first and reset the middle to 0.

## Limits

- No download, no offline cache, no login. Search and streaming only.
- Spotify is preview-only (30s) without a user Premium sign-in.
- YouTube can rotate the VISIONOS client or start requiring `po_token`; when that happens `yt_stream` returns an error and needs a new client. This is not fixable once and for all.
- SoundCloud is best-effort. If scraping breaks, its search goes dark until patched.

## License

MIT for this code. The unofficial endpoints are used without permission from either service.
