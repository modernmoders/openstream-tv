# STATE — updated 2026-07-06 by session 14 (day 2)

## ⚠️ READ FIRST (session 14 day 2 — UI overhaul COMPLETE, now alpha.13)
The 4-part owner UX overhaul is done and emulator-verified: (1) Settings, (2)
Home featured hero, (3) all menu lists/pills/rows + Discover pickers, (4)
app-wide screen motion — all in the shared refined language (calm surfaces,
accent focus, no white invert; DECISIONS #29/#30/#31). Current build
**alpha.13** (versionCode 13), 236 tests green, R8 release smoke PASS. The
refined language lives in `theme/OpenStreamTheme.kt` (tokens) +
`ui/components/Surfaces.kt` (`SurfacePill`/`SurfaceRow`/`OptionRow`). Only
low-value stragglers remain (Settings' identical private picker row, tiny
Streams/Rename action dialogs, Details title) — pick up only if the owner
wants more polish. Deploy target: `app-release.apk` **alpha.13**. The setup
name flow on real boxes is STILL gated on the owner re-uploading the `api=1`
index.php — see NEXT ACTION.

## ⚠️ (prior) session 14 — setup verified + owner UX overhaul, alpha.12
Two arcs this session, both emulator-verified: (a) session-13's one-step
setup was proven end-to-end against a contract mock (no bugs); (b) a big
owner-driven UX pass — the setup/Connect flow was simplified (no accept
screen, minimal copy, above-keyboard, smooth) and a **refined visual language**
was applied (shared theme tokens + TV `Surface` rows) to Settings, a new Home
featured hero, and the Addons/Home-rows lists. Current build **alpha.12**
(versionCode 12), 236 tests green, R8 release smoke PASS. Owner is happy
("new apk works, my account works"). **Active dev thread = the UI overhaul,
items 1–4 (see the top "Just finished" entry): 1–3 done, still to do are #3's
stragglers (Discover pickers, Home header pills, Details/Streams headers) and
#4 (app-wide screen-transition motion), all "refined, not dramatic."** The
setup name flow on real boxes is still gated on the owner re-uploading the
`api=1` index.php — see NEXT ACTION.

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
- **Session 14 day 2 (cont., 2026-07-06) — Owner feedback round 7 (partial).**
  Shipped the code-safe subset: (a) "E1/E2/S1E2" spelled out to "Episode N" /
  "Season N · Episode N" everywhere (Details, Up Next, both autoplay title
  builders); (b) **auto-play first stream defaults ON** now; (c) Details screen
  refined to the shared language (season chips → `SurfacePill` w/ selected
  state, episode rows → `SurfaceRow`); (d) episode list gets a bottom
  fade-to-bg + bottom padding so nothing hard-cuts. 236 tests green.
  ⚠️ NOT visually verified — emulator hit its black-screencap degradation
  mid-run; cold-boot + re-verify Details next session. versionCode still 13
  (bump when verified).
  **STILL OPEN from this feedback round — DO NEXT (needs a COLD-BOOTED
  emulator; current AVD is in its black-screencap degraded state):**
  1. Cold-boot, then VISUALLY VERIFY the round-7 Details changes (episode UI,
     "Episode N", bottom fade) — then bump versionCode 13→14 + build release.
  2. **Home scroll-up glitch** (owner-reported): scrolled down in Home, holding
     UP sticks partway (hero half-shown) until a Down+Up. Reproduce on fresh
     emulator; likely the tall hero (first LazyColumn item) + header-outside-
     list focus boundary during key-repeat. Try: header inside the scroll, or
     pivot/bring-into-view tuning.
  3. **Player controls redesign — OWNER CONFIRMED "build the full bar"
     (2026-07-06):** a proper bottom control bar. Press any key wakes it and
     puts focus on it; a scrub/progress bar you land on and press ◀▶ to
     rewind/fast-forward (keep the ±10/30s quick-seek too); OK = play/pause; a
     clearly-LABELED "Audio & subtitles" button (kill the cryptic "▲ audio
     settings" hint) + the two failure escapes below; auto-hide after ~5s. Keep
     UpNext/ended/error panels working. Real PlayerScreen.kt rewrite with TV
     focus management — verify seek/play/track/focus end-to-end on a live AVD.
     **Failure UX (owner 2026-07-06, round 8) — two plain-word buttons ALWAYS
     visible on the bar (not hidden long-press), for the "auto didn't work"
     cases:** (a) **"Try a different stream"** = next stream (the existing
     StreamAlternatives walk); (b) **"Play in another app"** = hand THIS stream
     to VLC/MX (external) — for streams that load but have no audio / no video /
     wrong-language / that only VLC decodes (ExoPlayer codec gaps on the 32-bit
     boxes). Rationale: next-STREAM-first is right for "won't play", but "plays
     wrong" needs the external-player escape, and today it's an invisible
     long-press. Put the same two buttons on the error panel. Auto still
     next-stream-first (up to 3), then surfaces the friendly panel with both.
     Autoplay-default-ON + the auto-mode "Starting…" flash fix already shipped.
  **Home reorganization — OWNER CHOSE "fix via the addon trim later"**: leave
  Home following addon order; MediaFusion-catalogs-first gets fixed when
  profiles are trimmed to 4-5 addons at finalize (see memory
  addon-endgame-and-finalize). No app-side curation now.
- **Session 14 day 2 (2026-07-06) — UI overhaul FINISHED: motion + straggler
  refinements (alpha.13, DECISIONS #31).** (#4) App-wide screen motion: one
  shared NavHost fade + micro-scale on every navigation. (#3 stragglers)
  Extracted `ui/components/Surfaces.kt` (`SurfacePill`/`SurfaceRow`/
  `OptionRow`) and applied it to the Home header pills, Discover filter bar,
  stream-list rows, and Discover pickers — all now the DECISIONS #29 language.
  236/236 tests, R8 release smoke PASS. versionCode 13 / 0.3.0-alpha.13.
  Emulator-verified (screenshots: Home pills, Discover pills, stream rows,
  Catalog picker, release Welcome). Deploy target `app-release.apk` alpha.13.
- **Session 14 (cont., 2026-07-06) — Refined UI overhaul, owner feedback
  (alpha.12, DECISIONS #29/#30). Items 1–3 of 4 done + emulator-verified.** Owner: "menus/typography/spacing
  horrible… make it feel amazing" but "refined, not dramatic"; do 1,2,3,4 in
  order. Established a shared visual language in `theme/OpenStreamTheme.kt`
  (`Accent` #4DA3FF, `SurfaceCard`, `SurfaceCardFocused`, `Hairline`) + TV
  `Surface` rows (native smooth focus, no white invert). (1) **Settings** rows
  + both picker dialogs refined. (2) **Home featured hero** — a calm marquee
  spotlight (first catalog item: backdrop+scrim, poster, headline title, meta,
  description, accent CTA); Home now header-anchors focus + snaps to item 0 so
  the hero shows on entry. (3) **Addons manager + Home-rows editor** wrapped in
  the shared card surface. 236/236 tests, R8 release smoke PASS. versionCode
  12 / 0.3.0-alpha.12. **NEXT for the UI pass:** finish #3's stragglers
  (Discover picker chips/dialogs, Home header pills, Details/Streams headers)
  and do #4 (app-wide screen-transition motion). Deploy target is now
  `app-release.apk` alpha.12 (supersedes alpha.10/.11).
- **Session 14 (cont., 2026-07-06) — Connect screen REDESIGN from owner
  feedback (alpha.11, DECISIONS #28). Built + emulator-verified + R8 smoke.**
  Owner saw the first working setup run and asked to strip it down: no "jody m"
  example (real name), no 3-step guide, no filler intro, NO accept screen
  (type name → just installs → a message that fades → Home), lift content above
  the keyboard, stop the focus highlight clipping, tighter spacing + smooth
  animation. Reworked `ConnectViewModel` (dropped `Ready`/`confirm` — auto-
  installs after lookup; the expert Add-addon paste path still previews-first)
  and `ConnectScreen` (minimal copy, name step top-anchored above the IME, all
  else centered, `AnimatedContent` cross-fade with `SizeTransform(clip=false)`,
  `Done` auto-fades to Home after ~1.7s). 236/236 tests (ConnectViewModelTest
  updated). versionCode 11 / 0.3.0-alpha.11. Fresh **release APK built + R8-
  smoke-verified** (launches clean to the new Welcome screen). Emulator flow
  re-verified end-to-end (TESTLOG 2026-07-06 Connect redesign). Boxes: deploy
  `app-release.apk` (alpha.11) — supersedes the alpha.10 build.
- **Session 14 (2026-07-06) — EMULATOR-VERIFIED the session-13 one-step setup
  (NEXT ACTION 0a). PASS end-to-end, no bugs found; docs + gate updated.**
  Session 13 shipped it without a device run; this was the verification.
  Live site still runs the OLD HTML index.php, so tested against a fresh
  contract mock (`scratchpad/mock_setup_site.py`) of the `api=1` JSON mode +
  a GET-served `openstream:1` profile listing NON-SECRET addons only
  (Cinemeta + the local fixture addon) — no owner secrets touched; the risk
  was the runtime wiring, not the specific profile. Temporarily set
  `setup.url=http://10.0.2.2:8095/`, built, installed to the emulator ONLY
  (both real onn boxes were connected via adb — every command pinned to
  `-s emulator-5554`, boxes untouched), then restored `setup.url` to the real
  domain and rebuilt. Verified (screenshots + TESTLOG): fresh install →
  Welcome/Connect (not Home) → 3-step guide + name field auto-focused →
  "adam s" + ENTER → "Hi Adam Savoy!" with "✓ Cinemeta / ✓ Local Test Addon"
  (no URLs) → Finish setup → both install → "You're all set!" → Start
  watching → Home (brand title "SavoyStreams", NO Addons button, Cinemeta
  rows) → Settings scrollable → Expert mode ON reveals the Addons manager,
  which lists both addons in profile order, both Enabled. MASTER_PLAN §10:
  the three session-13 directives ticked [x] (emulator-verified). 236/236
  tests still green (re-ran, no HomeViewModelTest flake this time). NOT
  deployed. Side effect: the AVD addon baseline is now Cinemeta + Local Test
  (was + owner's AIOMetadata/AIOStreams) — re-seed if a playback test needs
  them (their URLs are secret, not stored).
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
**The app side of alpha.10 is DONE and emulator-verified (0a below, session
14). Everything remaining is OWNER-SIDE** — the owner must (a) re-upload the
regenerated index.php so the real site speaks `api=1`, (b) deploy alpha.10 to
both boxes, (c) reconnect each box once so ProfileSync learns the link, then
run gate D. After that, continue Phase 4 (3).

0a. ✅ **DONE (session 14) — session-13 one-step setup EMULATOR-VERIFIED,
   no bugs.** Full flow passed against a contract mock of the `api=1` site
   (TESTLOG 2026-07-06 session 14). No code changes were needed — the runtime
   wiring was correct as shipped. MASTER_PLAN §10 directives ticked [x].
   The reusable mock is `scratchpad/mock_setup_site.py` (session-scoped —
   re-create from the TESTLOG description if a future session needs it: POST
   `api=1` → Found/Ambiguous/NoMatch, GET `/adam.json` → an `openstream:1`
   profile of NON-SECRET addons). To re-test: set
   `setup.url=http://10.0.2.2:8095/`, run both the mock and
   `tools/test_addon_server.py`, `assembleDebug`, install to `emulator-5554`,
   `pm clear dev.openstream.tv`, then RESTORE `setup.url` to the real domain.
0b. **OWNER: re-upload the regenerated `api=1` index.php.** The name-setup
   flow works in the app but the LIVE <setup-domain>/setup/index.php is still
   the OLD HTML-only page. Until the owner re-uploads the regenerated
   `docs/reference/StremioSurfer/hosting/index.php` (gitignored — has the JSON
   `api=1` mode, built with `tools/make_hosting_bundle.py --brand SavoyStreams`),
   real boxes can't do the name lookup. This is the one thing gating the
   name flow on hardware.
0c. Gate D is CLOSE: hosting is 9/10 verified (session 12) — one file re-drag
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
