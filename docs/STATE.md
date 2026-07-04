# STATE — updated 2026-07-05 by session 3

## Phase
Phase 2 — Details, streams, internal playback (3 of 5 units done).

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- Details screen (MetaRepository §4.1.6 chain w/ Cinemeta fallback; movie +
  series layouts verified on emulator) — commit d6c14f7.
- Stream list (fan-out §4.1.5, addon-order sacred §4.1.7, unsupported-source
  notes §4.1.4) — same commit.
- **Playback engine works end-to-end** (this checkpoint's commit):
  PlayableSource/SubtitleTrack (§3.2), PlayerEngine + ExoPlayerEngine
  (headers, subtitle configs, generous buffering, §6.1 plain-language error
  mapping), CurrentPlayback hand-off, PlayerScreen (PlayerView + Compose
  overlay, D-pad seek/pause, error panel w/ Retry, end panel).
  Verified with a local test addon (scratchpad script recreatable from
  TESTLOG description; serves any /stream/movie/*.json). 61/61 tests.
- Fixed a real lost-update race in all fan-out ViewModels (atomic update{}).

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
Phase 2 remaining:
1. **Watch progress + Continue Watching + resume** (§10 Phase 2 item 4):
   - Room: `watch_progress` table keyed (source_kind, external_id) = MediaRef
     (§8.4 — NOT IMDb-keyed; movies: kind="addon", id=meta id; episodes:
     id=video id). Columns: positionMs, durationMs, updatedAt, title,
     poster, type (for the row card + details resume).
   - PlayerScreen: persist position every ~10s + on exit (player.currentPosition).
   - Continue Watching row = always-first home row (§5.6) when non-empty;
     click → details (or straight to streams w/ resume position).
   - Resume dialog on details/streams when progress exists (§10): "Resume from
     X / Start over" → PlayableSource.startPositionMs.
2. **MediaSessionService** (§6.1): move ExoPlayer into a MediaSessionService
   so hardware media keys + assistant work and playback survives UI churn.
3. Gate: browse→play→resume loop vs a real AIOStreams instance (ask owner for
   manifest URL — SECRET, never commit).

## Environment rules (hard-earned — do not skip)
- **Playback testing needs a WINDOWED emulator** (`-gpu auto`, NO
  `-no-window`): the goldfish H.264 decoder fails to init headless
  (DecoderInitializationException) and videos won't play.
- **Cold-boot the emulator** (`-no-snapshot`) or verify `adb shell date -u`:
  snapshot clock drift breaks TLS for fresh certs.
- **Never run two gradle invocations concurrently.**
- Build outputs in `app/build.nosync/` (iCloud-proofing, DECISIONS #8).
- JAVA_HOME=/opt/homebrew/opt/openjdk@17; SDK/adb per CLAUDE.md.
- Real addon URLs are secrets. Emulator app-DB has Cinemeta + owner's
  AIOMetadata + "Local Test Addon" (http://10.0.2.2:8090 — harmless leftover;
  its server script is gone, so its rows just show failure chips unless the
  script is recreated per TESTLOG).
- On the Addons screen, initial D-pad focus lands on the FIRST ROW's toggle,
  not "Add addon" — press UP first. (Known UX wart for the Phase 4 audit,
  along with: reaching the home header takes one UP per row — needs a
  focus shortcut.)

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
