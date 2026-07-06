# STATE — updated 2026-07-06 by session 13

## ⚠️ READ FIRST (session 13 handoff — Fable 5 ran out mid-task)
Session 13's big feature (one-step name setup + Welcome Guide + Expert mode)
is **built, committed, and pushed**, 236 tests green — BUT it was **never
emulator-verified** and the live setup site still runs the OLD page. It is
NOT proven working on real hardware. **Do not treat it as done.** Start at
NEXT ACTION 0 below: verify the Connect flow on the emulator, then fix
whatever's broken. Everything compiles and unit-tests pass; the risk is in
the runtime wiring (nav routing, name lookup against the real site, focus).

## Phase
Phase 3 — build units DONE; gate (§7.2 on owner's onn box) still the only
item before `phase-3-done` (A PASS 2026-07-04, B PASS 2026-07-05, C
effectively passing on sentiment; D = last box — setup hosting now 9/10
verified, see below). Phase 4 units 1–4 shipped (sessions 11–12); new
owner directives recorded in MASTER_PLAN §10 (remote management SHIPPED,
addons-screen guard / error logging / language switcher pending).

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **Session 13 (2026-07-06) — One-step name setup + Welcome Guide + Expert
  mode (owner directive, DECISIONS #27). BUILT + COMMITTED + PUSHED, 236
  tests green, NOT emulator-verified, NOT deployed. alpha.10 (versionCode 10).**
  Owner: kill link-copying — a person types ONLY their name on the TV and the
  app looks them up, fetches their profile, installs everything; nobody sees
  a URL. New: `SetupConfig` (setup.url/brand from gitignored local.properties
  via BuildConfig — the secret domain stays out of git; empty url = feature
  hidden, open-source-safe); `SetupNameLookup` (POSTs name to the site's new
  JSON `api=1` mode → Found/Ambiguous/NoMatch); `ProfileInstaller` (shared
  plan+install path — `AddAddonViewModel` refactored to delegate to it);
  `ui/connect/ConnectViewModel`+`ConnectScreen` (Welcome Guide: 3 steps →
  type name → "Hi Adam!" → Finish setup → Done; warm jargon-free copy);
  `LaunchViewModel` (fresh install + configured build → Welcome, else Home;
  `take(1)` so installing mid-Connect doesn't yank the nav graph). Expert
  mode (`ViewPrefs.expertMode`, default OFF): Home header lost "Addons"
  (title = brand), addon manager moved to Settings → Expert mode → Addons
  (expert-only); Settings gained "Connect this TV" + "Expert mode", now
  scrollable. Site: `tools/make_hosting_bundle.py` index.php gained the JSON
  api mode + friendlier copy + `--brand`; **regenerated with --brand
  SavoyStreams into the gitignored hosting dir but NOT yet uploaded to
  the owner's setup domain** (the live site is still the old HTML page — the name flow
  CANNOT work on real hardware until this is uploaded). Also fixed the
  long-standing HomeViewModelTest Main-dispatcher flake (cancel VM scopes
  before resetMain; still saw it ~1/6 — mitigated, not fully dead). 13 new
  tests (ConnectViewModelTest ×7, LaunchViewModelTest ×3, + updated).
- **Session 12 (cont., 2026-07-06) — Phase 4 unit 5: Auto-play first
  stream + "Try another server" (owner request, DECISIONS #26).**
  Settings toggle (default off): picking a movie/episode auto-plays the
  §4.1.7 top stream once the addon-order prefix settles (pure
  `firstPlayableWhenSettled`), auto-resumes (no dialogs), fires once per
  screen; errors auto-advance up to 3 consecutive streams (autoplay §7.1
  keeps precedence, Ready resets); ▼ in the player → "Not playing right?"
  confirm → next stream from the shared `StreamAlternatives` walk (addon
  order, no wrap, position carried); error panel gains the same button.
  "Ask every time" + auto-start = internal player. 226/226 tests (10 new).
  FULL emulator verify (TESTLOG): toggle → CW movie auto-played + resumed
  17:46 via owner's real AIOStreams → ▼ dialog → server switch banner →
  second server rendering at ~18:35. AVD toggle restored OFF. NOT
  deployed — alpha.10 bundle. Auto-skip-on-real-error folded into next
  owner box run.
- **Session 12 (cont., 2026-07-06) — gate-D failure root-caused: NOT the
  hosting, NOT the app.** Owner reported clarence's setup link installs but
  adam's shows "response this app couldn't read". Re-verified adam's file
  byte-perfect + structurally valid on the host, then REPRODUCED the exact
  flow on the TV emulator with adam's real link: profile previewed
  perfectly — "adam savoy", all 9 addons ✓, Install button ready
  (screenshot in transcript). Conclusion: the text entered on the owner's
  box wasn't the exact personal .json link (page URL or stale/mistyped
  link). Owner instruction: phone → setup page → type name → COPY the
  returned link → paste into the box's browser-entry page. toby-savoy-*
  .json is STILL 404 everywhere (re-drag never landed) — that one file
  must be re-uploaded to setup/ before D can fully pass.
  Addon-manifest facts captured for owner education (2026-07-06): Trakt
  Scrobble addon = catalogs only (scrobbling is Stremio-app-native →
  MASTER_PLAN owner-asks: native Trakt integration); AIOMetadata instance
  ("AIO - Friends Anime") = catalog+meta+subtitles, 66 catalogs; main
  AIOStreams = 48 catalogs, the two backups = stream-only. App gap found
  while answering: subtitles resource never fanned out (stream-embedded
  only) — recorded in MASTER_PLAN. New owner asks recorded: profile
  dashboard (add/remove/reorder + last-sync metrics), native Trakt.
- **Session 12 — setup hosting FIXED (9/10) + Phase 4 unit 4: remote addon
  management (ProfileSync, DECISIONS #25).**
  (a) **Hosting saga resolved:** owner's drag-and-drop had landed in the
  DOMAIN ROOT, not `setup/` — root copies were byte-perfect, `setup/` still
  held the robot-session's corrupt/empty files. Owner then moved them
  server-side; verified byte-for-byte via curl+sha256 against
  `docs/reference/StremioSurfer/hosting/`: **9/10 profile JSONs + index.php
  in `setup/` are now EXACT; `toby-savoy-*.json` is 404 EVERYWHERE (lost in
  the move — owner must re-drag that one file into setup/); a few JSON
  leftovers still sit in the domain root (owner should delete).** Name
  lookup POST verified live (jody m → correct link). Directory listing off
  (index.php + Options -Indexes), .htaccess 403 over HTTP. The owner's onn
  box error ("response this app couldn't read") was tested against the then-
  broken setup/ copies AND/OR by pasting the PAGE URL — the app needs the
  personal `.json` link the page hands out, not the page address itself.
  (b) **ProfileSync (Phase 4 unit 4, owner directive):** the box saves the
  setup link on a confirmed install-all (`ProfileLink` DataStore blob); on
  every app start `ProfileSync.syncIfDue` re-fetches the hosted profile and
  aligns installed addons (pure `planSync` diff: add new, remove dropped,
  NEVER touch hand-added addons; profile wins over local removals).
  15-min throttle counts only successful syncs — unreachable profile = no-op
  + retry next launch, so "I fixed it, restart the app" works. Failures
  silent-but-logged (elder rule), no URLs in logs (tokens). 216/216 tests
  (11 new). NOT deployed — needs alpha.10 versionCode bump; existing boxes
  must re-paste their setup link once so the box learns it.
  Flake note: one HomeViewModelTest Main-dispatcher failure appeared ONCE in
  a full suite run, passed in isolation and on two clean full reruns —
  watch, don't chase yet.
- **Phase 4 units 2–3 — Poster size, language memory, "Always use" player
  (session 11, same day, owner away in town).** Settings now: Home rows /
  Poster size / Player, live current-value descriptions. (a) GLOBAL density
  §5.1: `ViewPrefs.posterColumns` 4–8 (default 6), Home + Search rows obey
  (Discover keeps its own chip); emulator-verified 8-up and back.
  (b) Audio/subtitle language memory (DECISIONS #19 → #24): tracks-dialog
  picks persist (`PlaybackPrefs`), re-applied as ExoPlayer PREFERENCES
  before play (no override → graceful fallback); Subtitles-Off is itself
  remembered; tag-less picks never clobber. Full emulator round-trip
  DEFERRED (fixture movie's row sits under ~15 AIO collection rows) —
  fold into the owner's real-media check or next session.
  (c) §6.2 "Always use" player: internal/ask/VLC/MX stored, resolved
  against installed-players AT CLICK TIME (pure fn, table-tested;
  uninstalled → internal, never a dead click); Settings dialog only shows
  detected players (VLC showed on the AVD); "Ask" verified on a real
  AIOStreams list (OK → Play-with dialog); long-press stays the one-off
  override; §7.1.6 chain inherits the launched player. 205/205 tests
  (8 new). **Owner directive 2026-07-05: gate checks C/D marked
  "skip for now" — NOT ticked, NOT faked; Dreamhost panel was signed out
  when the browser connected (Windows machine), so step 0 still needs the
  owner signed in (tab left on the sign-in page). Boxes untouched on
  alpha.9.**
- **Phase 4 unit 1 — Settings skeleton + Home-row manager (session 11).**
  Home header gains "Settings" → Settings screen (deliberately short,
  large described entries — the skeleton every future setting lands in:
  player preference §6.2, autoplay §7.1.7, languages DECISIONS #19,
  global density §5.1). First entry "Home rows": reorder ▲/▼ / rename
  (trapped-focus dialog, "Use original name" restores) / hide-show every
  catalog row. `HomeRowPrefs` = one JSON blob in DataStore keyed by
  `CatalogRef.key`, stale keys ignored, untouched rows keep addon order
  (§4.1.7), moves pin the full order, hidden rows filtered BEFORE the home
  fan-out (never fetched) — all DECISIONS #23. Continue Watching stays
  unmanaged/always-first. 197/197 tests (12 new). Full flow
  screenshot-verified on the TV emulator (TESTLOG session 11): hide →
  gone from Home, rename "Nana Picks" → shows on Home, ▼ → order changes
  on Home; test prefs then reset so the AVD baseline is unchanged.
  Found+fixed: empty-state flash before the Room read (rows flow now
  null-until-first-emission). adb quirk: with the leanback IME open,
  DPAD_DOWN/CENTER hit the keyboard — submit dialog text with ENTER.
  **NOT deployed** — boxes stay on alpha.9 for the owner's gate run;
  ships with the next deploy (bump versionCode then).
- **alpha.9 — owner feedback round 6 (session 10): outlined Back, voice
  search mic, Add-addon stays put. DEPLOYED to BOTH boxes.** BackButton →
  OutlinedButton. Search gets a 🎤 (system RECOGNIZE_SPEECH via
  ActivityResult — no RECORD_AUDIO needed; hidden when no recognizer;
  both boxes have one). Installing an addon no longer bounces to the
  addon list: `UiState.Installed(summary)` keeps the screen up with
  "✓ <name> installed — paste another…", clears + refocuses the field
  (§10 mic backlog item DONE; recent-searches still open). Verified on
  the release build incl. a REAL browser-entry POST. Owner sentiment on
  feel: "beautiful… so much faster" → C effectively passing; **D (phone
  setup link) is the LAST §7.2 box** — then tag phase-3-done.
- **alpha.8 — owner feedback round 5 (session 10): loader replaced, movies
  skip details, long-press fix. DEPLOYED to BOTH boxes.** Ghost loader
  looked static/broken on real boxes → removed; ONE `LoadingMessage`
  (faint pulsing text, layer-phase alpha) now serves every loading state.
  Movie click → stream list directly (details was a one-button stop;
  series keep details for episodes — AppNavHost branch). "Play with…"
  swallows the long-press's leftover repeats/release until a fresh
  key-down, so holding OK too long no longer auto-picks the first option.
  All three emulator-verified on the release build (TESTLOG).
  **OWNER GATE RESULTS (real box): §7.2 check B PASS** — VLC + MX Player
  round-trips work, position remembered. Checks C/D still open.
  KNOWN ISSUE: one Naruto file fails in the INTERNAL player only (VLC
  plays it) — suspect codec (32-bit boxes); need box logcat while
  reproducing, then decide: better error surface + suggest external
  player, or codec fallback. 185/185 tests.
- **alpha.7 — owner feedback round 4 (session 10, commit c578d5d): R8
  release builds + Discover perf/polish. DEPLOYED to BOTH boxes.**
  (a) `assembleRelease` is now R8-minified (18.6→3.2 MB), debug-signed so
  `install -r` upgrades boxes in place, data kept (DECISIONS #20) — this is
  the "boxes run unoptimized debug builds" perf lever from the alpha.5 box
  audit, and it carries the back buttons the owner asked to see.
  (b) **R8 smoke test caught a real bug:** cleartext HTTP was blocked in
  release AND (outside the emulator) in debug — http addon streams (§8
  Live-TV) would silently fail. `usesCleartextTraffic=true` app-wide now
  (DECISIONS #21). Full release smoke on emulator: home/Discover/streams/
  resume/playback frames all PASS.
  (c) Ghost loader: figure-8 comet traces the logo while Discover loads
  (draw-phase-only animation — house rule, DECISIONS #22); skeleton shimmer
  cards for page-2+; posters paint placeholder-first (no pop-in jank);
  Discover sort memoized (was re-sorting per recomposition); PosterCard
  honors density. (d) View chip → far-edge OutlinedButton "⚙ View".
  15s first-Discover-load = addon-server latency; now masked by the ghost,
  NOT eliminated — candidate fix: prefetch default Discover catalog at app
  start (like CW prefetch). 185/185 tests.
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
**0a below is THE priority — session 13 shipped a big feature blind (ran out
of budget before emulator verify). Prove it, then fix it.** After that, the
owner-blocked gate items (0b/1/1b), then continue Phase 4 (3).

0a. **VERIFY + FIX the session-13 one-step setup on the emulator.** It
   compiles and unit-tests pass but has NEVER run on a device — assume
   something in the runtime wiring needs fixing.
   The live <setup-domain>/setup/ still serves the OLD HTML index.php, so the
   name lookup will FAIL against it as-is. Two ways to test:
   (i) **Re-upload first** the regenerated `docs/reference/StremioSurfer/
   hosting/index.php` (gitignored — has the JSON `api=1` mode) to
   <setup-domain>/setup/, then test against the real site; OR
   (ii) **Local mock:** last session left `scratchpad/mock_setup_site.py`
   (may be gone — the scratchpad is session-scoped; re-create if needed: a
   tiny http.server answering POST api=1 with `{"ok":true,"name":"Adam
   Savoy","link":"<adam's REAL .json link>"}` for "adam", the ambiguous
   `choices` shape for "myles"). Then in local.properties set
   `setup.url=http://10.0.2.2:8095/`, `assembleDebug`, install, and after
   the run RESTORE `setup.url=<setup-domain>/setup/`.
   Flow to verify on a FRESH install (clear app data or uninstall first):
   launch → Welcome/Connect screen (not Home) → type "adam s" → "Hi Adam!"
   with the addon list → Finish setup → addons install → "Start watching" →
   Home shows rows. Also check: Settings → Expert mode toggle reveals Addons;
   Home header shows "SavoyStreams" and has no Addons button. Fix focus/nav/
   copy issues as they show up (emulator D-pad + IME quirks are in the
   Environment rules below). TESTLOG the result. adam's real link is in the
   owner's message history / the hosted adam-savoy-*.json.
0b. Gate D is CLOSE: hosting is 9/10 verified (session 12) — one file re-drag
   + a clean retest away.
Quick verify next session: subtitle-language persistence round-trip on
the fixture movie (Settings → Home rows → move "Local Test" row up for a
fast path, then pick a subtitle → exit → replay → auto-selected).
0. **Setup hosting COMPLETE (verified 2026-07-06):** all 10 profiles live
   in `setup/` — 9 byte-perfect, toby content-identical (upload converted
   LF→CRLF; parses fine, all 7 addons installable); domain root clean
   (every leftover 404s); lookup POST returns correct links; adam's link
   additionally proven end-to-end on the TV emulator (full 9-addon
   preview). ONLY the owner's retest remains for gate D:
   on the PHONE open `<domain>/setup/`, type the name, COPY THE LINK IT
   RETURNS (ends in `.json`) — that link, not the page address, goes into
   the app via Addons → Add addon → browser entry. Pasting the page URL is
   exactly the "response this app couldn't read" error. When install-all
   works → gate check D PASS → tick §7.2, tag `phase-3-done`, push.
   AIOMetadata URLs in users.json are still all EMPTY — owner fills, then
   regenerate profiles (same filenames survive via profiles.config.json)
   and re-upload; boxes then follow automatically via ProfileSync (#25)
   once they run alpha.10+ and have re-pasted their link once.
1. Owner follows **docs/TESTING_ON_ONN.md**. Boxes run **alpha.9**
   (R8 release, deployed + version-confirmed session 10). Gate status:
   A PASS (2026-07-04), **B PASS (2026-07-05 — owner: VLC + MX work,
   position remembered)**, C effectively passing on owner sentiment.
   Remaining: **D (paste setup link from phone → install-all) is the
   LAST §7.2 box.** Still from alpha.6: player UP-key Audio & Subtitles
   on a real multi-language stream. When D passes → tick §7.2 in
   MASTER_PLAN, tag `phase-3-done`, push. If anything R8-weird shows on
   real hardware, suspect missing keep rules — logcat shows
   ClassNotFound/serializer errors.
1b. **Naruto file fails in internal player (owner-reported, plays in
   VLC).** Get logcat from the box while reproducing:
   `adb -s 192.168.1.x:5555 logcat | grep -iE "exo|media3|codec|decoder"`
   Likely an unsupported codec on the 32-bit boxes (HEVC10/EAC3?).
   Then: playback-error surface that offers "Play with VLC" (§10
   elder-friendly) and/or codec-aware stream badges.
2. Record results in TESTLOG (owner dictates, Claude writes), tick the gate
   in MASTER_PLAN §10, tag `phase-3-done`, push.
3. Continue Phase 4 units (units 1–4 DONE sessions 11–12: settings skeleton +
   row manager, global density, language memory, "Always use" player,
   ProfileSync remote management). Next candidates, owner-value order —
   the owner's session-12 directives FIRST (MASTER_PLAN §10):
   (a) **guard the Addons screen** (off the main path, e.g. into Settings —
   but NOT before gate D passes, its instructions say "Addons → Add addon");
   (b) **error suppression + on-device log** (friendly fallbacks
   everywhere, diagnostics to a log the owner reads in Settings);
   (c) **interface-language switcher** (Stremio/Nuvio parity);
   then (d) watched-history row; (e) Discover scroll perf prefetch;
   (f) autoplay settings + tunneling toggle + debug overlay. When the next
   box deploy happens, bump versionCode and include everything since
   alpha.9 (Settings suite + ProfileSync — a big alpha.10); after upgrade
   each box re-pastes its setup link ONCE so ProfileSync learns it.

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
- **Box deploys are now `assembleRelease`** (R8, debug-signed — DECISIONS
  #20): `install -r` upgrades in place. Bump versionCode EVERY deploy.
  Release APK: app/build.nosync/outputs/apk/release/app-release.apk
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
