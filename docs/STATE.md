# STATE — updated 2026-07-05 by session 10

## Phase
Phase 3 — build units DONE; gate (§7.2 on owner's onn box) still the only
item before `phase-3-done` (check A passed 2026-07-04; B/C/D pending).
Phase 4 items landing early by owner request (Discover redo, view options,
player track picker, search focus rule, CW prefetch — all SHIPPED).

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **On-screen Back button on every screen below Home (session 10, commit
  6919cbc):** shared `BackButton` component; pops exactly one level, same
  as remote BACK (§10 elder-friendly). Back never takes initial focus —
  screens anchor their primary action with a FocusRequester instead:
  Details → View streams / first season chip / first episode (season-less),
  Streams → FIRST playable stream, Addons → "Add addon", Discover → Type
  picker; Search/Add-addon text fields keep their existing anchors. The
  Streams and Addons anchors also FIX two of the "Focus warts" below.
  Full flow screenshot-verified on the TV emulator (TESTLOG 2026-07-05).
  185/185 tests. NOT yet deployed — boxes still run alpha.6; ships with
  the next build so the owner's alpha.6 gate run isn't disturbed.
- **alpha.6 — owner feedback round 3 (session 9): player audio & subtitle
  picker, search focus fix, Continue Watching prefetch.** DPAD_UP in the
  player opens a two-section trapped-focus dialog: audio tracks named by
  language/layout, subtitles Off/tracks incl. addon-provided .srt
  (DECISIONS #19; pure RawTrack menu model, 10 new JVM tests). Full caption
  loop emulator-verified: select English → cues render on time → switch
  Spanish → Off (TESTLOG 2026-07-05 alpha.6). Search results now enter on
  the FIRST card (`focusRestorer` + first-card anchor — owner's "picker
  starts mid-row" bug; §10 (c) done for search rows). Home prefetches the 2
  newest Continue Watching metas into the DECISIONS #17 HTTP cache.
  Fixture addon now serves 2 synthetic subtitle tracks (cue every 10 s).
  185/185 tests. **Deployed to BOTH onn boxes as alpha.6** (network adb,
  versions confirmed). NOT yet verified: audio switching with real
  multi-language media (fixture is single-track) — owner's real streams
  will exercise it.
- **Dreamhost upload attempt (session 9):** Claude CAN drive the panel via
  the Chrome extension, but panel.dreamhost.com was signed out and Claude
  must not enter passwords. Owner was connecting the right browser when
  usage/session ended — see NEXT ACTION 0.
- **alpha.5 — owner feedback round 2 (session 8, same day as the tree):**
  (a) **Addon HTTP disk cache** (DECISIONS #17): catalog/meta cached 30 min,
  relaunch = ZERO network bytes (measured); addon-down serves stale instead
  of an error; streams never cached. Hard-earned: Cloudflare's `Age` header
  made entries stale-on-arrival — must strip Age/Expires (regression-tested).
  (b) **Focus-clip fix app-wide** (§5.3): scroll containers clip on the
  scroll axis; every lazy list/grid/dialog now has focusHeadroom
  contentPadding; details switched to contentPadding + season-chip edge pad.
  (c) **Discover View chip** (DECISIONS #18): density 6/8 columns + client-
  side sort (A–Z/newest/top-rated), DataStore-persisted behind a ViewPrefs
  seam. (d) **Box audit**: both onn boxes = Android TV 14/API 34, 32-bit
  armeabi-v7a, 2–3 GB RAM — DEBUG builds are the remaining perf lever
  (R8/release next). 174/174 tests. Deployed to both boxes as alpha.5.
- **Discover redo — Stremio-style category tree (owner request, session 8):**
  studied web.stremio.com Discover live in-browser, then replaced the
  left-rail catalog list with Type → Catalog → Genre picker chips
  (trapped-focus dialogs, initial focus on current selection, ✓ marker,
  addon sublabels in the catalog picker). Upstream pick resets downstream,
  like Stremio. Genre-required catalogs (Cinemeta "New"/year lists) are now
  browsable in Discover via new `ManifestCatalog.isDiscoverable` +
  auto-selected first genre; home rows still use `isBrowsableFeed`.
  `loadMore` keeps the active genre (`genre=X&skip=N`). 165/165 unit tests
  (7 new); full picker flow screenshot-verified on the TV emulator against
  Cinemeta + owner's AIOMetadata/AIOStreams (TESTLOG 2026-07-05,
  DECISIONS #16). Owner's custom AIOMetadata types (Trending, Anime,
  Networks…) surface as first-class Types — correct per §8.
- **Owner real-box feedback round (alpha.3):** autoplay = §7.2 check A PASS
  on real hardware (TESTLOG 2026-07-04). Full feedback triaged into
  MASTER_PLAN §10 Phase 4 backlog (scroll perf/prefetch, density default+
  setting, mic + recent searches + search focus rule, watched row, clipping
  bugs, player controls, skip-intro/credits options, pastel accents, 360p
  artifact investigation). Quick fix shipped as **alpha.4**: resume-dialog
  floor 60s→15s (Continue Watching keeps 60s) so swapping streams
  mid-episode resumes instead of restarting.
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
**Finish the Dreamhost upload with the owner, then owner verifies alpha.6 +
Phase 3 gate (§7.2).**
0. **Dreamhost upload (Claude drives, owner unlocks):** the owner must have
   Chrome (the profile signed into panel.dreamhost.com, or ready to sign
   in — Claude must NEVER enter the password) with the Claude extension
   CONNECTED (`list_connected_browsers` must show it; last session it
   showed [] after the owner switched windows). Then: Panel → Websites →
   Manage Files → create `setup/` on the chosen domain → upload the
   CONTENTS of `docs/reference/StremioSurfer/hosting/` (10 profile JSONs +
   index.php + .htaccess) → open `https://<domain>/setup/` and live-check
   the PHP name lookup (type "myles m" → link appears; REQUEST_URI path
   handling is the untested part). SECURITY: those JSONs embed real addon
   tokens — fine to upload to the owner's own host, never into git/chat.
   AIOMetadata URLs in users.json are still all EMPTY — owner fills, then
   regenerate profiles (same filenames survive via profiles.config.json).
1. Owner follows **docs/TESTING_ON_ONN.md**. Boxes now run **alpha.6**
   (deployed + version-confirmed this session). Checks:
   A (3-episode chain — already PASS 2026-07-04), B (VLC round-trip incl.
   §7.1.6), C (feel), D (paste setup link from phone → install-all).
   NEW for alpha.6: player UP-key Audio & Subtitles dialog on a real
   stream — captions on/off/switch, and audio-language switching on any
   multi-language title (emulator could only test single-audio media).
   Still pending from alpha.5: clip-fix eyeball, Discover tree/View, and
   relaunch speed (if still slow → R8 minified-debug build is the lever).
2. Record results in TESTLOG (owner dictates, Claude writes), tick the gate
   in MASTER_PLAN §10, tag `phase-3-done`, push.
3. Then Phase 4 unit 1: row/catalog manager (reorder/rename/hide) — start
   with the settings screen skeleton it and the player-preference /
   autoplay-settings items all need (§10 Phase 4, §7.1.7, §6.2 "Always
   use" setting; per-launch dialog exists, DECISIONS #12). The settings
   screen is also where the preferred audio/subtitle language persistence
   goes (DECISIONS #19) and the elder-friendly audit begins (§10).

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
  present locally, gitignored; movie stream now carries 2 synthetic .srt
  subtitle tracks for caption testing — cue every 10 s). Fast path to the
  fixture series: home → Continue Watching (while progress rows last).
- Focus warts for Phase 4 audit: home header needs one UP per row.
  FIXED in session 10: Addons now enters on "Add addon"; stream list now
  enters on the first playable card (no DOWN/UP dance needed before
  CENTER). Long-press OK = `input keyevent --longpress
  KEYCODE_DPAD_CENTER` (opens Play with…).
- Emulator D-pad quirk: too many BACKs exits the app entirely; relaunch with
  `adb shell am start -n dev.openstream.tv/.MainActivity`. An old prototype
  `com.openstream.tv` also lives on this AVD — don't confuse the two. The
  AVD also has Android's intent-resolver stubs handling video/*, so "Other
  apps…" appears even with no real second player.

## Blockers / open questions
- §7.2 gate sign-off = owner's onn box run (see NEXT ACTION 1) — now also
  carries the external-player round-trip checks the emulator can't do.
