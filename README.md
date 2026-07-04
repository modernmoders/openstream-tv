# OpenStream TV

An open-source, TV-first client for the [Stremio addon protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md),
built for Android TV. Goals, in priority order: playback that never crashes or
stalls silently, flawless series autoplay, a fast and dense 10-foot UI, and full
addon-protocol compatibility.

**Status: pre-alpha.** Phase 0 (project scaffolding) — nothing usable yet.

- Full specification: [docs/MASTER_PLAN.md](docs/MASTER_PLAN.md)
- Current status / next action: [docs/STATE.md](docs/STATE.md)
- Decision log: [docs/DECISIONS.md](docs/DECISIONS.md)

## Building

Requirements: JDK 17, Android SDK with platform 37.

```sh
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

## License

[GPLv3](LICENSE). This app ships no content sources: it is a protocol client for
addons the user installs by URL.
