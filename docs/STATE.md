# STATE — updated 2026-07-04 by session 4

## Phase
Phase 2 — Details, streams, internal playback (5 of 5 units built; GATE remains).

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **Watch progress + Continue Watching + resume** (commit 17adcfd):
  MediaRef key (§8.4), Room v2 `watch_progress` + migration (schema 2.json),
  ProgressRepository (resume window 60s..95%, pure fns unit-tested),
  PlayerViewModel 10s saves + exit save + ENDED clear, resume dialog
  (real Dialog, focus-trapped) on the stream list, Continue Watching
  always-first home row with progress-bar cards. Full loop verified on
  emulator incl. process death + reinstall persistence.
- **MediaSessionService** (commit 8bfa221): PlaybackService owns
  engine+session, PlayerHolder hands engine to UI, media keys verified via
  dumpsys (PAUSED/PLAYING through the session), clean teardown on Back.
- Fixture addon server now committed: `tools/test_addon_server.py`
  (download bbb_720p.mov per its docstring; Google sample bucket is dead).
- 72/72 unit tests.

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
**Phase 2 GATE:** full browse→play→resume loop against a real AIOStreams
instance. BLOCKED on owner supplying an AIOStreams manifest URL in chat
(SECRET — never commit/display; install via the app UI only).
Steps once URL arrives:
1. Cold-boot windowed emulator, install current APK (or build main).
2. Install the AIOStreams URL via Addons → Add addon (type with adb input
   text; URL never goes in any file).
3. Pick a movie (Cinemeta/AIO catalog) → details → streams (AIO groups must
   keep server-side order) → play a debrid HTTPS stream → seek → back →
   Continue Watching → resume dialog → resume.
4. Log results in TESTLOG (no URLs), tick Phase 2 gate in MASTER_PLAN §10,
   then start Phase 3: AutoplayController state machine (§7.1) unit tests
   first — PlayerEvent.Ended + "Playback finished" panel are the hook points.

If the URL hasn't arrived: start Phase 3 §7.1 state machine (pure Kotlin,
fully testable without any addon).

## Environment rules (hard-earned — do not skip)
- **Playback testing needs a WINDOWED emulator** (`-gpu auto`, NO
  `-no-window`): goldfish H.264 decoder fails headless.
- **Cold-boot the emulator** (`-no-snapshot`) or verify `adb shell date -u`
  matches host: snapshot clock drift breaks TLS for fresh certs.
- **Never run two gradle invocations concurrently.**
- Build outputs in `app/build.nosync/` (iCloud-proofing, DECISIONS #8).
  APK: app/build.nosync/outputs/apk/debug/app-debug.apk
- JAVA_HOME=/opt/homebrew/opt/openjdk@17; SDK/adb per CLAUDE.md.
- Real addon URLs are secrets. Emulator app-DB has Cinemeta + owner's
  AIOMetadata + Local Test Addon (http://10.0.2.2:8090 — run
  `python3 tools/test_addon_server.py` to bring it alive; video download
  in its docstring).
- Focus warts for Phase 4 audit: Addons screen initial focus lands on first
  row's toggle (press UP for "Add addon"); home header needs one UP per row.
- Emulator D-pad quirk: too many BACKs exits the app entirely; relaunch with
  `adb shell am start -n dev.openstream.tv/.MainActivity`.

## Blockers / open questions
- Phase 2 gate needs owner's AIOStreams manifest URL (requested in chat).
