# STATE — updated 2026-07-05 by session 5

## Phase
Phase 3 — Autoplay + external players (unit 1+2 of 3 done; external players remain).

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **Autoplay fully wired and verified on emulator** (commit 2223871, TESTLOG):
  3-episode chain vs local fixture series with zero transition presses —
  Ended → Up Next countdown → fan-out → tier-1 bingeGroup pick → next episode
  plays; Back-cancel; series end → finished panel; 20s-delayed addon survives
  via patient 50s-read-timeout client (DECISIONS #11 — the interactive 15s
  budget had killed §7.1's 60s patience promise). MASTER_PLAN §10 Phase 3
  item 1 ticked; §7.2 gate emulator half done, owner's onn box run pending.
- Also fixed: player panels (finished/error) never received OK — key handler
  swallowed DPAD_CENTER unconditionally (pre-existing since Phase 2).
- `tools/test_addon_server.py` now serves "Bunny: The Series" (3 eps,
  bingeGroup + decoy streams; STREAM_DELAY_S=<sec> delays episodes 2+ for the
  §7.2 delayed case).
- 124/124 unit tests.

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
**Phase 3 unit 3: external players (VLC + MX Player + generic), resume
round-trip.**
1. `ExternalPlayerLauncher` (player pkg, §3.2 note in PlayerEngine.kt):
   build ACTION_VIEW intents — VLC (`org.videolan.vlc`, extras
   `position`/`from_start`, result extra `extra_position`), MX Player
   (`com.mxtech.videoplayer.ad/.pro`, extras `position`/`return_result`),
   plus a chooser fallback. Feed PlayableSource url+headers (VLC supports
   `http-headers` extra; MX uses `headers` string-array).
2. Player preference: for now a per-launch "Play with…" long-press or row on
   the stream list (full settings screen is Phase 4) — record choice in
   DECISIONS.
3. On ActivityResult: map returned position → ProgressRepository.save (same
   §8.4 MediaRef), and per §7.1.6 show the Up Next card in-app when the
   returned position is ≥95%/last 30s (best-effort autoplay for external).
4. Unit-test intent construction + result parsing (pure functions); emulator
   check needs a VLC apk sideloaded (or verify chooser path only — VLC on the
   owner's boxes).
5. Checkpoint per protocol. Then §7.2 gate on owner's onn box (OWNER ACTION)
   closes Phase 3.

## Environment rules (hard-earned — do not skip)
- **Playback testing needs a WINDOWED emulator** (`-gpu auto`, NO
  `-no-window`): goldfish H.264 decoder fails headless.
- **Cold-boot the emulator** (`-no-snapshot`) or verify `adb shell date -u`
  matches host: snapshot clock drift breaks TLS for fresh certs.
- **Emulator degrades after ~2.5h of heavy adb input**: TV launcher ANRs,
  input focus lost, black screencaps, presses leak into the launcher
  (landed in launcher settings once — check `dumpsys activity` before typing
  key bursts). `adb reboot` does NOT recover rendering; kill and cold-boot
  the emulator process. Pace keyevents; screenshot-verify between bursts.
- **Never run two gradle invocations concurrently.**
- Build outputs in `app/build.nosync/` (iCloud-proofing, DECISIONS #8).
  APK: app/build.nosync/outputs/apk/debug/app-debug.apk
- JAVA_HOME=/opt/homebrew/opt/openjdk@17; SDK/adb per CLAUDE.md.
- Real addon URLs are secrets. Emulator app-DB has Cinemeta + owner's
  AIOMetadata + Local Test Addon v1.1.0 + owner's AIOStreams (that order —
  Discover rail: Local Test Series is entry index 35 from the top).
  Fixture: `python3 tools/test_addon_server.py` (bbb_720p.mov download in
  docstring; file present but gitignored).
- Focus warts for Phase 4 audit: Addons screen initial focus lands on first
  row's toggle (UP reaches "Add addon"); home header needs one UP per row;
  stream list initial focus misses the first card (send DOWN then UP to
  anchor before CENTER).
- Emulator D-pad quirk: too many BACKs exits the app entirely; relaunch with
  `adb shell am start -n dev.openstream.tv/.MainActivity`. An old prototype
  `com.openstream.tv` also lives on this AVD — don't confuse the two.

## Blockers / open questions
- §7.2 gate final sign-off needs a 3-episode run on the owner's onn box
  (OWNER ACTION, after external players land or in parallel).
