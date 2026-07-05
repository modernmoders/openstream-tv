# OpenStream TV

An open-source, TV-first client for the [Stremio addon protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md),
built for Android TV. Goals, in priority order: playback that never crashes or
stalls silently, flawless series autoplay, a fast and dense 10-foot UI, and full
addon-protocol compatibility.

**Status: pre-alpha.** Phase 3 (autoplay + external players) — browse, play,
resume and series autoplay work against real addons; not yet released.

- Full specification: [docs/MASTER_PLAN.md](docs/MASTER_PLAN.md)
- Current status / next action: [docs/STATE.md](docs/STATE.md)
- Decision log: [docs/DECISIONS.md](docs/DECISIONS.md)

## External players

Streams can be handed to VLC or MX Player (long-press a stream → "Play with…").
Two limitations to know about:

- **Autoplay with external players is best-effort.** Our app can't see inside
  another player, so series autoplay relies on the position VLC/MX report back
  on exit: if you leave near the end (≥95% or the last 30 seconds), the Up Next
  countdown appears back in our app. Players that report nothing (the generic
  chooser) can't trigger autoplay or save resume positions.
- **Open VLC once before first use.** VLC's own first-run setup swallows the
  first playback handed to it.

## Building

Requirements: JDK 17, Android SDK with platform 37.

```sh
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

## License

[GPLv3](LICENSE). This app ships no content sources: it is a protocol client for
addons the user installs by URL.
