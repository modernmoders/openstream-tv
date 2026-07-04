# STATE — updated 2026-07-05 by session 2

## Phase
**Phase 1 — COMPLETE** (tag `phase-1-done`). All §10 Phase 1 boxes ticked.
Next: Phase 2 — Details, streams, internal playback.

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- Discover (curated-catalog rail + paginated grid) and Search verified on the
  emulator against Cinemeta + owner's AIOMetadata. 52/52 tests.
- Root-caused and fixed two infrastructure gremlins (TESTLOG 2026-07-05):
  iCloud corrupting builds (build output now in `build.nosync/`, DECISIONS #8)
  and emulator snapshot clock-drift breaking TLS (cold-boot rule below).

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
Phase 2 (MASTER_PLAN §10), suggested order:
1. **Details screen**: route "details/{type}/{id}"; resolve meta via §4.1.6
   fallback chain (meta-declaring addons → Cinemeta by IMDb id); backdrop,
   description, seasons/episodes from `videos` array grouped by season.
   PosterCard onClick navigates here (currently a no-op).
2. **Stream list screen** (§4.1.5/§4.1.7): parallel fan-out over stream
   addons via `declares("stream", type, id)`, incremental render, addon-order
   groups, failure chips. NOTE: owner has AIOStreams instances — ask for a
   manifest URL when testing (SECRET — never commit, same rule as always).
3. **ExoPlayer engine** (§6.1): PlayerEngine interface + ExoPlayerEngine +
   MediaSessionService + Compose overlay; PlayableSource from §3.2 (already
   spec'd; create domain/PlayableSource.kt). Wire proxyHeaders + notWebReady.
4. **Watch progress**: Room `media_ref`-keyed progress (§8.4 — NOT IMDb-keyed),
   Continue Watching row goes live, resume dialog.
5. Gate: full browse→play→resume loop vs a real AIOStreams instance.

## Environment rules (hard-earned — do not skip)
- **Cold-boot the emulator** (`-no-snapshot`) or verify `adb shell date -u`
  matches host: snapshot resume leaves the clock hours behind → TLS "chain
  validation failed" → every fresh-cert addon fails with NETWORK chips.
- **Never run two gradle invocations concurrently.**
- Build outputs are in `app/build.nosync/` (NOT app/build/) — iCloud-proofing,
  DECISIONS #8. APK: app/build.nosync/outputs/apk/debug/app-debug.apk
- JAVA_HOME=/opt/homebrew/opt/openjdk@17; SDK/adb per CLAUDE.md.
- Real addon URLs are secrets (CLAUDE.md rule). Emulator has Cinemeta +
  owner's AIOMetadata installed in-app.

## Blockers / open questions
- none.
