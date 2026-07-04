# STATE — updated 2026-07-04 by session 5

## Phase
Phase 3 — Autoplay + external players (unit 1 of 3 done; §7.1 machine built, unwired).

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **Phase 2 GATE PASSED** (commit 20eff34, TESTLOG): full loop on the owner's
  real AIOStreams instance — install → catalog → details → AIOStreams group
  (server order kept) → real debrid 1080p HTTPS plays → seek via media keys →
  exit save → Continue Watching → resume dialog at exact saved ms.
  MASTER_PLAN §10 Phase 2 all ticked.
- **Bug found+fixed during gate** (commit 9cb9f13): stream list on the back
  stack offered a stale resume position (loaded once in init). Now
  `WatchProgressDao.observe` + `ProgressRepository.observeResumePosition`
  Flow, collected by StreamListViewModel. Re-verified on emulator.
- **Phase 3 unit 1** (commit e342cc4): `autoplay/` package — NextEpisode
  (binge order, specials last), StreamCascade (§7.1 tiers as ONE lexicographic
  comparator → full ordered attempt list), AutoplayStateMachine (pure reducer:
  countdown → resolving → attempting; 60s patience; 3-attempt fallthrough;
  terminals are Finished panel or manual stream list — never dead). Design
  notes in DECISIONS.md #10. 113/113 unit tests.

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
**Phase 3 unit 2: wire AutoplayController into playback.**
1. New `AutoplayController` (player-scoped class): owns an
   `AutoplayStateMachine`, subscribes to `PlayerEngine.events`; on
   `PlayerEvent.Ended` → `machine.start(NextEpisode.nextAfter(meta.videos,
   currentVideoId))`. It needs the series meta + current video id — extend
   `PlaybackRequest` (CurrentPlayback) with `videos: List<Video>` + current
   video id, populated by StreamListViewModel.stage() (details screen already
   holds the meta; movies pass emptyList → machine.start(null) → Finished
   panel, current behavior preserved).
2. Effects: StartResolving → StreamRepository fan-out per addon (reuse the
   §4.1.5 pattern; each completion feeds Event.AddonResponded); Play →
   engine.play(candidate → PlayableSource, keep §8 constraints); 
   OpenStreamList → navigate to stream route of next episode.
3. UI on PlayerScreen: replace the bare "Playback finished" panel with the
   state-driven Up Next card (Countdown secondsLeft, Resolving "x/y responded",
   OK/Back handling). 1s ticker in the wiring layer, not the machine.
4. Unit-test the controller with fake engine/repository (Turbine), then
   emulator check vs the LOCAL test addon: extend tools/test_addon_server.py
   with a 3-episode series (bingeGroup on streams) — autoplay 2 chains with
   zero presses. Real-AIOStreams series run belongs to the §7.2 gate later.
5. Checkpoint per protocol.

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
  AIOMetadata + owner's AIOStreams + Local Test Addon (http://10.0.2.2:8090 —
  run `python3 tools/test_addon_server.py`; video download in its docstring).
- Focus warts for Phase 4 audit: Addons screen initial focus lands on first
  row's toggle (press UP for "Add addon"); home header needs one UP per row;
  stream list initial focus misses the first card (first DPAD_CENTER no-ops —
  send DOWN then UP to anchor before CENTER).
- Emulator D-pad quirk: too many BACKs exits the app entirely; relaunch with
  `adb shell am start -n dev.openstream.tv/.MainActivity`.

## Blockers / open questions
- none. (§7.2 acceptance gate will eventually want a real series run against
  the owner's AIOStreams — already installed on the emulator.)
