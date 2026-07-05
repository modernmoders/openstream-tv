# STATE — updated 2026-07-04 by session 7

## Phase
Phase 3 — Autoplay + external players: ALL BUILD UNITS DONE. Gate (§7.2 on
owner's onn box) is the only item left before `phase-3-done`.

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **Setup links (DECISIONS #14):** Add-addon input (and the browser entry
  page) now also accepts a hosted profile JSON (`openstream:1` marker) →
  multi-addon preview → install-all in profile order. Owner tooling:
  `tools/make_profiles.py` generated 12 per-person profiles from private
  users.json into the gitignored `docs/reference/StremioSurfer/profiles/`
  (NEVER commit; host privately — owner mentioned getting a domain).
  Data gap found: every `aiometadata.manifest_url` in users.json is EMPTY,
  so profiles currently lack AIOMetadata — owner to fill + regenerate.
  158/158 unit tests.
- **Add-addon browser entry (§4.1.1 QoL, DECISIONS #13):** `RemoteEntryServer`
  (found orphaned+untracked from session 6, now wired in): while the
  Add-addon screen is open, the app serves a one-form page on the LAN
  (ports 8385–8389); pasting a manifest URL in a phone/computer browser
  feeds the normal on-TV fetch → preview → confirm flow. Never echoes the
  secret URL, no read endpoint, screen-lifetime only. 155/155 unit tests
  (9 real-socket server tests + 2 ViewModel remote-submit tests). NOT in the
  v0.3.0-alpha.1 pre-release — include in the next build for the owner.
- **Phase 3 unit 3: external players** (commit 5c85e64 + docs, TESTLOG,
  DECISIONS #12): pure `ExternalPlayers.kt` (VLC/MX intent dialects, result
  → Progress/Finished/Unknown mapping, §7.1.6 near-complete rule) behind an
  `ExternalPlayerPort` seam; long-press "Play with…" on the stream list;
  activity-result round-trip into ProgressRepository (same §8.4 MediaRef);
  Finished → stream-list-hosted Up Next flow, next episode relaunches the
  SAME external player, manual fallback replaces the list. Manifest
  `<queries>` added. 144/144 unit tests.
- Emulator verification (VLC 3.7.1 F-Droid arm64 sideloaded, versionCode
  13070106): Play-with detection, intent handoff (VLC opened our URL,
  decoders started — logcat-proof), responsive return ×3, generic chooser.
  **VLC cannot create a video output on the goldfish GPU** ("video output
  creation failed", every attempt; no adb root to force GLES2 vout) — the
  live position round-trip is emulator-unverifiable, folded into the
  owner's onn-box gate run below.

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
**Phase 3 gate (§7.2) — OWNER ACTION on an onn box, then tag.**
1. Owner follows **docs/TESTING_ON_ONN.md**. BOTH onn boxes (4K pro
   192.168.1.117, 4K Plus 192.168.1.231 — network adb is LIVE from this
   Mac, no Downloader needed) already run **v0.3.0-alpha.3** (fixes the
   below-the-fold preview bug the owner hit; TESTLOG 2026-07-04). Checks:
   A (3-episode chain), B (VLC round-trip incl. §7.1.6), C (feel),
   D (paste setup link from phone → install-all).
   Bonus check D: Add addon → open the shown web address on a phone → paste
   a setup link → install-all lands.
1b. **Dreamhost upload (owner action):** upload the CONTENTS of
   `docs/reference/StremioSurfer/hosting/` (10 profiles + index.php name
   lookup + .htaccess) to a `setup/` folder on one of the owner's domains
   (Panel → Websites → Manage Files). Then tell Claude the URL — Claude
   verifies the page live (PHP is unlinted locally, no php CLI on this Mac;
   the lookup logic + REQUEST_URI path handling need one live check).
   Profiles now merge each person's LIVE Stremio collection
   (tools/pull_stremio_addons.py → stremio_addons.json, DECISIONS #15).
   AIOMetadata URLs in users.json are still all EMPTY — fill + regenerate
   (same links survive via profiles.config.json).
2. Record results in TESTLOG (owner dictates, Claude writes), tick the gate
   in MASTER_PLAN §10, tag `phase-3-done`, push.
3. Then Phase 4 unit 1: row/catalog manager (reorder/rename/hide) — start
   with the settings screen skeleton it and the player-preference /
   autoplay-settings items all need (§10 Phase 4, §7.1.7, §6.2 "Always
   use" setting; per-launch dialog exists, DECISIONS #12).

## Environment rules (hard-earned — do not skip)
- **Playback testing needs a WINDOWED emulator** (`-gpu auto`, NO
  `-no-window`): goldfish H.264 decoder fails headless.
- **VLC on the emulator can launch but NEVER renders video** (vout creation
  fails on goldfish; ExoPlayer is fine). Don't burn time retrying — external
  video playback is real-hardware-only. VLC arm64 F-Droid apk = versionCode
  suffix 06 (…08 is x86_64); grant
  `appops set org.videolan.vlc MANAGE_EXTERNAL_STORAGE allow` and click
  through OnboardingActivity once before any intent test.
- **Cold-boot the emulator** (`-no-snapshot`) or verify `adb shell date -u`
  matches host: snapshot clock drift breaks TLS for fresh certs.
- **Emulator degrades after ~2.5h of heavy adb input**: TV launcher ANRs,
  input focus lost, black screencaps, presses leak into the launcher.
  `adb reboot` does NOT recover rendering; kill and cold-boot the emulator
  process. Pace keyevents; screenshot-verify between bursts.
- **Never run two gradle invocations concurrently.**
- Build outputs in `app/build.nosync/` (iCloud-proofing, DECISIONS #8).
  APK: app/build.nosync/outputs/apk/debug/app-debug.apk
- JAVA_HOME=/opt/homebrew/opt/openjdk@17; SDK/adb per CLAUDE.md.
- Real addon URLs are secrets. Emulator app-DB has Cinemeta + owner's
  AIOMetadata + Local Test Addon v1.1.0 + owner's AIOStreams (that order).
  Fixture: `python3 tools/test_addon_server.py` (port 8090; bbb_720p.mov
  present locally, gitignored). Fast path to the fixture series: home →
  Continue Watching → "S1E2 · Episode 2" card (while progress rows last).
- Focus warts for Phase 4 audit: Addons screen initial focus lands on first
  row's toggle (UP reaches "Add addon"); home header needs one UP per row;
  stream list initial focus misses the first card (send DOWN then UP to
  anchor before CENTER). Long-press OK = `input keyevent --longpress
  KEYCODE_DPAD_CENTER` (opens Play with…).
- Emulator D-pad quirk: too many BACKs exits the app entirely; relaunch with
  `adb shell am start -n dev.openstream.tv/.MainActivity`. An old prototype
  `com.openstream.tv` also lives on this AVD — don't confuse the two. The
  AVD also has Android's intent-resolver stubs handling video/*, so "Other
  apps…" appears even with no real second player.

## Blockers / open questions
- §7.2 gate sign-off = owner's onn box run (see NEXT ACTION 1) — now also
  carries the external-player round-trip checks the emulator can't do.
