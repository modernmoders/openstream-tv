# TESTLOG.md — what was tested, where, and the result

Append-only. Newest entries at the top.

---

## 2026-07-05 — Phase 4 units 2–3: Poster size, language memory, "Always use" player (session 11)

Three new Settings-backed features while the owner was out (gate checks
deferred by owner — see STATE): global poster density (§5.1, owner's "only
6 posters" gripe), preferred audio/subtitle language persistence
(DECISIONS #19/#24), and the §6.2 "Always use" player setting
(Internal / VLC / MX / Ask, resolution rules in DECISIONS #24).

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `testDebugUnitTest` — 205 tests (8 new: player-pref resolution ×4, remembered-language rules ×2, density into Home state + density survives a search) | macOS, JDK 17 | PASS (205/205, two consecutive clean runs) |
| Settings now lists Home rows / Poster size / Player, each with a live description of the current value | AVD `openstream_tv_api34`, debug APK | PASS (screenshot) |
| Poster size: dialog 4–8 with ✓ + focus on current; picked 8 → Home rows render 8-up; picked 6 back → 6-up | same | PASS (screenshots) |
| Player dialog: Internal ✓ first, then VLC — DETECTED because the AVD has it sideloaded (§6.2 only-show-what-exists), then "Ask every time" + long-press hint | same | PASS (screenshot) |
| "Ask every time": plain OK on a real stream (owner's AIOStreams, The Sheep Detectives list) opened the Play-with dialog instead of playing; no launch fired | same | PASS (screenshot) |
| Back to Internal: plain OK on the fixture E1 stream played directly in the internal player (language-prefs apply path ran as a no-op with empty prefs — no regression) | same + fixture server :8090 | PASS (screenshots) |
| Tracks dialog still correct on single-track media: "Track 1 · 5.1 ✓" / subtitles "Off ✓ — this stream has no subtitles" | same | PASS (screenshot) |
| NOT yet verified on emulator: the full subtitle-language persistence round-trip (pick → exit → replay → auto-selected). The fixture's subtitle tracks are on the BBB movie whose home row sits below the owner's ~15 AIOMetadata collection rows — scroll-hunting risked the known input-degradation. The write path (`rememberedLanguage`) and fallback rules are unit-tested; the pick loop itself was verified in alpha.6. Fold into the owner's real-media audio/subtitle check (already a STATE item) or next session (fast path: Settings → Home rows → move "Local Test" up). | — | DEFERRED |
| Boxes untouched — still alpha.9; everything since ships with the next deploy | — | note |

## 2026-07-05 — Phase 4 unit 1: Settings skeleton + Home-row manager (session 11)

First Phase 4 unit proper (§10): a Settings screen (skeleton for all future
settings) whose first entry is "Home rows" — reorder (▲/▼), rename, and
hide/show every catalog row on Home. Hidden rows are dropped BEFORE the
fan-out (never fetched), Continue Watching stays unmanaged/always-first
(§5.6), and addon-order remains the default for anything the user never
touched (§4.1.7).

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `testDebugUnitTest` — 197 tests (12 new: HomeRowPrefs customizer rules ×5, HomeRowsViewModel ×4, HomeViewModel hide/rename/reorder ×3) | macOS, JDK 17 | PASS (197/197) |
| Settings skeleton: Home header gains "Settings"; screen shows Back + one large described entry "Home rows", focus lands on it | AVD `openstream_tv_api34`, debug APK | PASS (screenshot) |
| Row manager lists ALL rows (Cinemeta + owner's AIOMetadata) with addon · type sublabels; entry focus = first row's Rename | same | PASS (screenshot) |
| Hide: first row → "· hidden" dimmed, button flips to Show; Home no longer renders the row and its catalog is NOT fetched (also unit-tested) | same | PASS (screenshots) |
| Rename: dialog (trapped focus, prefilled field, Save / Use original name) → typed "Nana Picks" via on-screen IME, submitted with the IME action → list + Home both show the custom title | same | PASS (screenshots) |
| Reorder: ▼ on the renamed row → moves below "Featured · movie" in the manager AND on Home; focus follows the row (stable keys §5.7) | same | PASS (screenshots) |
| Empty-state flash found & fixed: manager briefly showed "No rows yet — install an addon first" before the Room read landed; rows flow is now null-until-first-emission and the body stays blank for that beat | same | FIXED + PASS |
| adb quirk noted: with the leanback IME open, DPAD_DOWN/CENTER go to the keyboard, not the dialog buttons — submit renames with the IME action key (ENTER), not button navigation | — | note |
| Emulator hygiene: pre-existing session went black-screencap (known ~2.5 h degradation) → cold-booted per STATE rules; test prefs reset afterwards (`run-as … rm files/datastore/home_row_prefs.preferences_pb`) so the AVD baseline is unchanged | — | note |
| NOT deployed to the boxes — they stay on alpha.9 so the owner's §7.2 gate run (C/D) is undisturbed; ships with the next deploy | — | note |

## 2026-07-05 — alpha.9: outlined Back, voice search mic, Add-addon stays put (session 10)

Owner feedback round 6 ("it's beautiful… so much faster now"): make the Back button hollow/outlined; add a microphone next to the search bar; stop bouncing back to the addon list after each install when adding several addons. Owner also confirmed the C-check sentiment (feel) — D (phone setup link) remains the last §7.2 box.

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `assembleRelease` + `testDebugUnitTest` — 185 tests (3 updated: `UiState.Installed` now carries a summary string) | macOS, JDK 17 | PASS (185/185) |
| BackButton → OutlinedButton: hollow chrome on every screen below Home | AVD, release APK | PASS (screenshot) |
| Voice search: 🎤 OutlinedButton right of the query field, shown only when `RECOGNIZE_SPEECH` resolves (new manifest `<queries>` entry); result feeds query + search. Recognizer PRESENT on both onn boxes (`pm resolve-activity`) — spoken end-to-end is owner's to try (emulator has no mic audio) | AVD + both boxes | PASS (visible; resolution confirmed on boxes) |
| Add addon: install no longer navigates away — field clears, focus returns to it, green "✓ Local Test Addon installed — paste another, or press Back when you're done". Exercised via the REAL browser-entry path (adb-forwarded POST to the LAN form, §4.1.1) | AVD, release APK | PASS (screenshots) |
| adb quirk noted: `input text` mangles `.`/`:` on this AVD — use the browser-entry server for URL entry in emulator tests | — | note |
| **Deployed alpha.9 (R8 release) to BOTH onn boxes**, versionName confirmed | onn 4K pro + 4K Plus | DONE |

## 2026-07-05 — alpha.8: pulsing loader everywhere, movies skip details, long-press fix (session 10)

Owner feedback round 5 on alpha.7 (real boxes): ghost loader "has no animation and looks horrible" on the boxes — replace ALL loading with a faint/pulsing "loading"; movie details screen is an extra step — remove it; holding OK too long on a stream auto-selects the first "Play with…" option. ALSO owner-reported gate results: **VLC and MX Player round-trips WORK, position remembered after leaving VLC → §7.2 check B = PASS (owner, real onn box, 2026-07-05).** One known issue: a specific Naruto file fails ONLY in the internal player (plays in VLC) — codec suspicion, needs box logcat.

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `assembleRelease` + `testDebugUnitTest` — 185 tests | macOS, JDK 17 | PASS (185/185) |
| Ghost loader + skeleton cards REMOVED; single `LoadingMessage` (faint pulsing "Loading…", layer-phase alpha per DECISIONS #22) now used by Home rows, Discover (first load centered + page-2 item), Details, and stream groups | AVD `openstream_tv_api34`, release APK | PASS (visible mid-fetch on stream list) |
| Movies skip details: Discover grid movie click → stream list DIRECTLY (no "View streams" stop); series unchanged (season/episode picking needs details) | same | PASS (screenshots) |
| Long-press fix: `--longpress` OK on a stream → "Play with…" opens and STAYS (stale release + repeats swallowed until a fresh repeatCount-0 key-down); fresh press then selects Internal player normally (played a real AIOStreams stream, exited immediately) | same | PASS (screenshots) |
| **Deployed alpha.8 (R8 release) to BOTH onn boxes**, versionName confirmed | onn 4K pro + 4K Plus | DONE |
| Owner still to verify on-box: pulse looks right, movie shortcut feel, the hold-to-long-press timing | — | owner check |

Owner feedback round 4: Discover takes ~15 s on first click and chip focus is choppy while loading; wants loading placeholders + an "artsy full-screen loading effect with ghostly motion" that "runs screamingly well"; wants the View chip spaced out / visually distinct; couldn't see the Back button (wasn't deployed yet).

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `assembleRelease` + `testDebugUnitTest` — 185 tests | macOS, JDK 17 | PASS (185/185; APK 18.6 MB → 3.2 MB minified) |
| **R8 regression found & fixed:** release blocked cleartext HTTP ("CLEARTEXT communication … not permitted", logcat) — old debug overlay only allowed http to 10.0.2.2, so http addons/streams were broken OFF-emulator too. Fix: `usesCleartextTraffic="true"` in main manifest, overlay deleted (DECISIONS #21) | AVD `openstream_tv_api34`, release APK | FIXED + PASS |
| Release smoke: boots over existing install (data kept — CW row intact), home rows load (kotlinx-serialization + Room + Hilt + Coil under R8), Discover tree + pickers, stream fetch, resume dialog "Resume from 6:44", playback renders frames | same | PASS (screenshots) |
| Ghost loader: dotted figure-8 with comet + fading tail fills the content area while a catalog loads; verified via `emu network speed gsm` throttle; comet position advances between shots; draw-phase-only animation (DECISIONS #22) | same | PASS (screenshots) |
| Poster placeholders: grid paints solid card shapes on frame 1, posters fill in (visible mid-load); skeleton shimmer row replaces "Loading…" text for page-2+ fetches | same | PASS |
| Discover perf: sort no longer runs inside the grid lambda (was re-sorting the full list every recomposition); items get contentType; PosterCard now honors the density setting (was hardcoded 6-col width) | code + emulator | PASS |
| View chip: pushed to the far edge (weight spacer), OutlinedButton "⚙ View" — visually distinct from the tree pickers | same | PASS (screenshot) |
| Discover "adult" genre on owner's AIO addon returns empty → "Nothing in this catalog" (not an error) | same | PASS (noted) |
| **Deployed alpha.7 (R8 release) to BOTH onn boxes** via network adb `install -r` — upgrade in place, versionName=0.3.0-alpha.7 confirmed on both | onn 4K pro + 4K Plus | DONE |
| NOT verified here: real-world smoothness on the boxes (debug→R8 is the big lever; owner eyeballs it), and the 15 s first-load is addon-server latency — masked by the ghost loader, not eliminated | — | owner check |

## 2026-07-05 — on-screen Back button on every screen below Home (session 10)

§10 elder-friendly: users shouldn't need to know which remote key escapes a screen. Shared `BackButton` on Discover/Search/Addons/Add addon/Details/Streams; pops exactly one level (same as remote BACK). Rule: Back must never take a screen's initial focus — screens anchor their primary action with a FocusRequester instead.

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `testDebugUnitTest` — 185 tests (UI-only change, no new JVM surface) | macOS, JDK 17 | PASS (185/185) |
| Discover: Back rendered left of title; entry focus on Type picker ("Movie"), NOT Back; LEFT reaches Back, OK pops exactly one level to Home | AVD `openstream_tv_api34` (cold-booted; stale instance had the known black-screencap degradation), live internet | PASS (screenshots) |
| Details (movie, Sheep Detectives/Cinemeta): Back above title; entry focus on "View streams" | same | PASS |
| Details (series, fixture Bunny: The Series): entry focus on season chip, not Back | same | PASS |
| Streams (owner's AIOStreams, real query): Back beside title; entry focus lands on FIRST playable stream — also fixes the STATE.md focus wart "stream list initial focus misses the first card" | same | PASS |
| Addons: entry focus on "Add addon" — also fixes the wart "initial focus lands on first row's toggle" | same | PASS |
| Search & Add addon: Back beside title; text field keeps entry focus (keyboard opens), Back reachable | same | PASS |
| NOT verified: season-less series (channel-style) first-episode anchor — no such item in the test addons; same FocusRequester path as the season chip | — | edge case |

Owner feedback round 3 (via handoff message): captions in the internal player, an audio-language selector "that actually selects the right language", search results starting mid-row, and preloading the newest Continue Watching titles.

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `testDebugUnitTest` — 185 tests (11 new: 10 PlayerTracks naming/dedup/off/indices, 1 HomeViewModel prefetch newest-2-only) | macOS, JDK 17 | PASS (185/185) |
| Tracks dialog (UP in player): opens over live playback, Audio "Track 1 · 5.1 ✓" (BBB has no language metadata — fallback + channel-layout naming correct), Subtitles Off ✓/English/Spanish from addon subtitles | AVD `openstream_tv_api34` (cold-booted — prior instance had the known black-screencap degradation), fixture addon v1.1 + 2 synthetic .srt tracks | PASS (screenshots) |
| Caption selection: pick English → "[English] test caption #31" rendered at 5:00 (cue #31 = 5:00–5:08, correct timing); reopen shows English ✓, Off unchecked | same | PASS |
| Caption switch + off: Spanish → "[Spanish] #34" at 5:35; Off → no caption at 6:11 | same | PASS |
| Dialog layout bug found live: platform default Dialog width squeezed the Subtitles column into a 3-char sliver — fixed with `usePlatformDefaultWidth = false`, re-verified | same | FIXED + PASS |
| Search focus rule (§10 (c)): query "bunny" via Cinemeta, DOWN from field → focus lands on FIRST result card (Dust Bunny), not mid-row | same, live internet | PASS (screenshot) |
| Resume regression alongside new UP handler: resume dialog → resumed 4:31, overlay hint now "▲ audio & subtitles" | same | PASS |
| Prefetch: newest-2 Continue Watching metas requested once, third item never fetched (MockAddonServer request log) | JVM unit test | PASS |
| NOT verified here: audio switching with real multi-language media (fixture is single-track; no ffmpeg on this Mac to synthesize) — same apply path as subtitles; owner's real streams on the onn box will exercise it | — | owner check |

## 2026-07-05 — alpha.5: HTTP cache (zero-network relaunch), clipping headroom, Discover view options (session 8)

Owner-reported on onn boxes (Projectivy launcher): force-stop -> relaunch "took forever"; top/bottom list highlights clipped; details-screen highlight clipped one side.
Box audit via live network adb: BOTH boxes = Android TV 14 (API 34), 32-bit armeabi-v7a, heap 384m; 4K pro ~3 GB RAM (~1 GB avail), 4K Plus ~2 GB (~630 MB avail). targetSdk 37 / minSdk 23 fine; NOTE boxes run unoptimized DEBUG builds — R8/release build is the next perf lever.

| Check | Environment | Result |
|---|---|---|
| `assembleDebug` + `testDebugUnitTest` — 174 tests (9 new: 4 cache incl. CDN-Age regression, 4 sort, 1 view-prefs) | macOS, JDK 17 | PASS (174/174) |
| Relaunch network cost with warm cache: /proc/net/dev eth0 delta across force-stop -> relaunch -> 12s | AVD `openstream_tv_api34`, real Cinemeta + owner's AIOMetadata/AIOStreams | **0 bytes rx / 0 bytes tx** |
| Offline relaunch (wifi disabled): home renders rows + posters from disk (stale-if-offline fallback + Coil disk cache) | same | PASS (screenshot) |
| Live bug found: Cloudflare `Age: 5793` made entries stale on arrival -> online launches refetched while offline was instant. Fixed by stripping Age/Expires in the rewrite; regression test added | same | FIXED + tested |
| Discover View dialog: Density (6/8 col) + "Sort loaded items"; Compact switched grid to 8 columns live; ✓ markers; persisted via DataStore | same | PASS (screenshots) |
| Focus-scale clipping: headroom contentPadding on home rows, search rows, discover grid, details (contentPadding + season-chip edge padding), stream list, addon manager, picker dialogs | same | Emulator-verified visually on Discover grid; owner to confirm on box |

## 2026-07-05 — Discover redo: Stremio-style category tree, emulator-verified (session 8)

| Check | Environment | Result |
|---|---|---|
| `./gradlew assembleDebug && testDebugUnitTest` — 165 tests (7 new: type-tree build, genre refetch, genre survives loadMore skip, genre-required auto-select, type-switch reset, discoverRefs filtering, isDiscoverable/genreOptions parsing) | macOS, JDK 17, local JVM | PASS (165/165) |
| Type picker: dialog opens focused on current selection ("Movie ✓"); lists Movie/Series + owner AIOMetadata custom types (Trending, Genres, Collections, Charts, By Decade, Anime, Networks) | AVD `openstream_tv_api34`, app DB: Cinemeta + owner's AIOMetadata + Local Test Addon + owner's AIOStreams | PASS (screenshot-verified) |
| Type switch Movie→Series: catalog chip resets to first series catalog (Popular·Cinemeta), grid refetches series | same | PASS |
| Genre picker: "None ✓" default + Cinemeta genre list; picking Adventure refetches grid, chip label becomes "Adventure" | same | PASS |
| Catalog picker: aggregates across all 4 addons with addon sublabels (incl. AIOStreams Netflix/HBO Max/Disney lists); genre-required Cinemeta "New" now offered; D-pad focus-scrolls the long list | same | PASS |

## 2026-07-04 — Owner real-box session (alpha.3, onn 4K pro): autoplay §7.2-A PASS; findings triaged (session 7)

Owner-reported, Claude-recorded:

| Observation | Verdict |
|---|---|
| Autoplay episode chain: "works flawlessly" | §7.2 check A **PASS on real hardware** (B: VLC round-trip, C: feel — still pending; C has open perf notes below) |
| Setup-link + browser entry flow end-to-end ("everything else is working great") | PASS |
| Switching streams mid-episode restarted from 0:00 (360p AVC w/ artifacts → 1080p HEVC) | GAP: 60s resume floor ate short progress → fixed same day (dialog floor now 15s, Continue Watching keeps 60s; alpha.4) |
| 360p AVC stream: macroblock "dying GPU" artifacts; 1080p HEVC clean | Logged for investigation (likely low-bitrate source; verify decoder selection) — MASTER_PLAN Phase 4 backlog |
| Discover scroll laggy/uneven; only 6 posters visible; focus-border/poster clipping; wants mic search, recent searches, watched row, better player controls, skip-intro/credits options, pastel-iridescent accents | All captured in MASTER_PLAN §10 Phase 4 owner-feedback backlog |

## 2026-07-04 — Add-addon: browser-submitted preview unreachable on real box → scroll + autofocus fix, emulator-verified (session 7)

Owner repro on onn box (alpha.2): setup-link preview rendered below the fold
(non-scrolling Column), D-pad couldn't reach Install → "No addons installed".

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` (158 tests incl. 9 RemoteEntryServer real-socket, 5 setup-link/remote-submit VM) | macOS, JDK 17, local JVM | PASS (158/158) |
| Full browser round-trip: Add addon open → `adb forward` 8385 → POST setup link (Cinemeta profile via 10.0.2.2) → "Sent to the TV" page | AVD `openstream_tv_api34` | PASS |
| ProfilePreview appears with ✓ Cinemeta v3.0.14 AND focus auto-jumps to "Install 1 addon" (screenshot: highlighted, no D-pad input) | same | PASS |
| KEYCODE_DPAD_CENTER → install runs → navigates back to Addons list | same | PASS |
| v0.3.0-alpha.3 (versionCode 3) sideloaded via network adb | onn 4K pro (192.168.1.117) + onn 4K Plus (192.168.1.231) | installed; owner to re-run their paste flow |

## 2026-07-05 — Phase 3 unit 3: external players (VLC/MX/generic) — launch + detection verified; video render impossible on emulator (session 6)

| Check | Environment | Result |
|---|---|---|
| `./gradlew assembleDebug && testDebugUnitTest` — 144 tests (new ×20: ExternalPlayersTest — VLC/MX launch-extra dialects incl. position/from_start/return_result/headers/subs alignment, result interpretation incl. §7.1.6 near-complete boundaries, lenient int-vs-long extras, MX playback_completion, generic-unknown, non-OK-unknown) | macOS, JDK 17 | PASS (144/144) |
| **"Play with…" long-press dialog (§6.2):** long-press OK on a stream row → dialog shows Internal player / VLC / Other apps… — VLC detected (sideloaded F-Droid 3.7.1 arm64), MX correctly absent (not installed), generic offered only because other video handlers exist. Screenshot. | AVD windowed, VLC sideloaded, local fixture addon | PASS |
| **Intent handoff to VLC:** picking VLC opens VideoPlayerActivity with our URL — logcat: `libvlc input: 'http://10.0.2.2:8090/video.mov' successfully opened`, h264+aac decoders started. Intent extras contract exercised for real. | same | PASS |
| **App responsive on return (§6.2 known-Stremio-failure check):** three VLC round-trips (incl. one canceled by VLC's first-run onboarding) — every return landed back on the intact stream list, no freeze, no crash, no bogus progress row (no-info results leave stored progress untouched). | same | PASS |
| **Environment limit: VLC cannot render video on the goldfish GPU** — `libvlc video output: video output creation failed` → VLC aborts playback ~1s in, every attempt (its android_window/opaque-vout path; our ExoPlayer renders fine on the same AVD). No adb root on this image to force VLC's GLES2 vout. Consequence: live resume-position round-trip + §7.1.6 external Up Next chain CANNOT be emulator-verified — moved to the owner's onn box run (already the §7.2 gate owner action). Pure logic is fully unit-tested. | same | BLOCKED (env) |
| VLC first-run gotcha (real devices too): onboarding (storage/scan pages) hijacks the first ACTION_VIEW launch and silently kills playback — user must open VLC once before handoff works. Worth a README/troubleshooting note at release. | same | RULE |
| Generic chooser path: "Other apps…" fires the system intent resolver (process observed), resolves to the only handler, returns; app intact. | same | PASS |

## 2026-07-05 — Phase 3 unit 2: AutoplayController wired — 3-episode chain + delayed addon verified (session 5)

| Check | Environment | Result |
|---|---|---|
| `./gradlew assembleDebug && testDebugUnitTest` — 124 tests (new ×11: AutoplayControllerTest with fake gateway + virtual time: countdown ticking, OK skip, Back cancel, 20s-slow addon, zero-playable → manual list, open-failure fallthrough, movie/series-end/no-meta inactive paths) | macOS, JDK 17 | PASS (124/124) |
| Fixture: `tools/test_addon_server.py` extended with "Bunny: The Series" (3 episodes, bingeGroup streams + decoy, STREAM_DELAY_S env delays episodes 2+ for the §7.2 case) | host | NOTE |
| **3-episode autoplay chain (§7.2 first half):** E1 → Ended → Up Next countdown (screenshot: "Up next: S1E2 · Episode 2 — Playing in 3s (OK play now · Back cancel)") → fan-out → tier-1 bingeGroup stream chosen over decoy → E2 plays → same → E3 plays ("Starting S1E3 · Episode 3" card screenshot) → series end → "Playback finished" panel, never a dead screen. Zero presses at every transition (in-episode seeks only to skip the 10-min fixture; server log confirms meta → stream/bbbs:1:2 → video → … → stream/bbbs:1:3 → video). Player overlay title updates per episode. | AVD windowed, local fixture addon | PASS (screenshots + server log) |
| **Back cancels countdown (§7.1 4a):** BACK during "Up next" → "Playback finished" panel, still on player screen | same | PASS |
| **Delayed addon (§7.2 second half):** server answering /stream/ for E2 after 20s → countdown → "Finding next episode… (0/1 addons responded, Back cancel)" patience card → E2 plays at ~31s after Ended. | same | PASS (screenshot + server log "delaying stream response 20.0s") |
| **Bug found & fixed: 15s read timeout defeated the 60s patience rule.** First delayed run: OkHttp's interactive read timeout (DECISIONS #9) killed the 20s-delayed response → addon "settled" as failed → zero playable → manual stream list. §7.1's promise never survived past 15s. Fix: autoplay fetches use a patient client (readTimeout 50s, DECISIONS #11). Re-run: PASS. Manual-fallback path itself behaved exactly per spec (landed on E2's stream list — bonus verification). | same | FIXED + PASS |
| **Bug found & fixed: finished/error panel buttons unreachable by OK.** Player key handler consumed DPAD_CENTER unconditionally (pre-existing since Phase 2), so the panel's focused "Back"/"Retry" buttons never received clicks. Now CENTER passes through when a panel is up; autoplay countdown still owns OK first. | same | FIXED |
| Emulator hygiene notes: after ~2.5h of heavy input the TV launcher ANRs and input focus is lost (black screencaps, presses leak to launcher) — cold-boot the emulator when screencaps go black. `adb reboot` did NOT recover rendering; full emulator restart did. | AVD | RULE |

## 2026-07-04 — Phase 3 unit 1: autoplay state machine + cascade (session 5)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 113 tests (new ×40: NextEpisode ordering/specials/boundaries ×7, StreamCascade tiers/extraction ×13, AutoplayStateMachine countdown/patience/fallthrough/terminals ×20) | macOS, JDK 17 | PASS (113/113) |
| §7.1 rules covered: bingeGroup tier-1 early-play; strict tier-2 ordering (addon > resolution > filename pattern > cache flag); 60s patience (no cancel at 59s, play-what-we-have at 60s, manual list only at 60s+zero-playable); 3-attempt open-failure fallthrough; Back is the only user cancel; never a dead screen (every terminal is Finished panel or manual stream list) | JVM tests | PASS |
| Not yet wired: PlayerViewModel/UI integration, Up Next overlay, fan-out driver — next unit | — | TODO |

## 2026-07-04 — Phase 2 GATE PASSED: full loop vs owner's real AIOStreams instance (sessions 4–5)

| Check | Environment | Result |
|---|---|---|
| Installed owner's AIOStreams instance (URL withheld — secret, typed via adb, never in files) through Addons → Add addon: manifest fetched (v2.30.5, 48 catalogs, stream/catalog/meta/subtitles) → preview → Install | AVD windowed, cold boot, live internet | PASS (session 4) |
| **GATE §10 Phase 2:** Continue Watching → details (owner's AIOMetadata meta) → View streams → AIOStreams group renders with server-side order preserved (starred 1080p WEB-DLs first) → real debrid HTTPS 1080p stream plays (BUFFERING→PLAYING 1.0x, frames verified) → resume dialog offers saved position → resume lands at exact saved ms (14:53 = 893400ms, DB-confirmed) → MEDIA_FAST_FORWARD seeks through the session (926s→990s) → Back (exit save) → Continue Watching → re-entry dialog shows updated 16:52 (=1012770ms in Room) | same | PASS (screenshots + dumpsys + DB dump) |
| **Bug found & fixed: stale resume position on back-stack screen.** StreamListViewModel loaded `resumePositionMs` once in `init`; returning from the player to the still-alive stream list and reselecting a stream offered the pre-playback position (14:53 instead of 16:52) while Room already held the fresh value. Fix: new `WatchProgressDao.observe(ref)` + `ProgressRepository.observeResumePosition(ref)` Flow, collected by the ViewModel. Re-tested same-screen reselect after play+seek: dialog shows fresh 17:46. | same | FIXED + PASS |
| `./gradlew assembleDebug && testDebugUnitTest` — 73 tests (new: observeResumePosition re-emits as saves land) | macOS, JDK 17 | PASS (73/73) |
| Session-4 tail note: "server is temporarily limiting requests" chip from owner's instance during first gate attempt — transient server-side throttle, gone after session gap; app surfaced it as a failure chip per §4.1.8 | live addon | BY DESIGN |
| Focus wart for Phase 4 audit: stream list initial focus misses the first card (first DPAD_CENTER no-ops; DOWN/UP re-anchors) — same family as Addons-screen wart in STATE.md | AVD | NOTE |

## 2026-07-04 — Phase 2: MediaSessionService verified (session 4, same day)

| Check | Environment | Result |
|---|---|---|
| `./gradlew assembleDebug && testDebugUnitTest` after moving the engine into PlaybackService (MediaSessionService §6.1) | macOS, JDK 17 | PASS (72/72) |
| Playback through service-owned engine: resume dialog → Resume from 3:05 → video plays (progress had persisted across `adb install -r`) | AVD windowed | PASS |
| `dumpsys media_session`: session registered, `active=true`, **"Media button session is dev.openstream.tv"** | same | PASS |
| Hardware media keys via session: `KEYCODE_MEDIA_PAUSE` → state PAUSED(2), `KEYCODE_MEDIA_PLAY` → PLAYING(3), position ticked from the resumed offset | same | PASS |
| Back from player: ServiceRecord count 0, "Media button session is null" — no leaked session/notification; Back = stop is deliberate TV UX | same | PASS |
| Build note: adding an @AndroidEntryPoint service made Dagger's generated component reference `@CanIgnoreReturnValue` → added `compileOnly(errorprone-annotations)` | host | FIXED |
| Fixture server committed as `tools/test_addon_server.py` (local-only URLs, no secrets) so future sessions stop recreating it | repo | NOTE |

## 2026-07-04 — Phase 2: watch progress + Continue Watching + resume verified (session 4)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 72 tests (new: ProgressRepository eligibility/roundtrip/observe ×8, StreamListViewModel resume ×2, HomeViewModel continue-watching ×1) | macOS, JDK 17 | PASS (72/72) |
| Room v1→v2 migration (watch_progress table) ran on the emulator's existing v1 DB via `adb install -r` — no migration errors, addons and app data intact | AVD windowed, cold boot | PASS |
| **Full progress loop:** local test addon (recreated at http://10.0.2.2:8090 with catalog+meta+stream+video w/ HTTP Range) → BBB plays → seek to 2:02 → periodic save tick → Back exits player (final save) → home shows **Continue Watching first row with progress bar (~20%)** → card click → details → View streams → stream click → **"Resume from 2:34 / Start over" dialog** → Resume → playback lands at 2:34 | same | PASS (screenshots reviewed) |
| Progress survives process death: app relaunched fresh, Continue Watching row + bar restored from Room | same | PASS |
| Resume dialog is a real Dialog window (focus trapped, Back dismisses) — list behind unreachable while up | same | PASS |
| Cinemeta + owner's AIOMetadata unaffected (AIO briefly toggled Disabled during the test to shorten D-pad paths; re-enabled after) | same | OK |
| Google `gtv-videos-bucket` sample URLs now 403 (was last session's red herring) and `download.blender.org/demo/movies/BBB/` 404s — working fixture source: `download.blender.org/peach/bigbuckbunny_movies/big_buck_bunny_720p_h264.mov` (H.264 in .mov, plays fine through Media3 mp4 extractor) | host | NOTE |
| Ended→clear path (watched item leaves Continue Watching) covered by unit tests (isResumable >95% + PlayerViewModel clearAsync on ENDED); not re-verified end-to-end on emulator | JVM tests | PASS (unit) |

## 2026-07-05 — Phase 2: details, stream list, PLAYBACK verified (session 3)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 61 tests (new: MetaRepository chain ×3, StreamListViewModel ×3, StreamMapping ×3); suite run 3× consecutively to verify race fix | macOS, JDK 17 | PASS (61/61 ×3) |
| **Concurrency bug found & fixed:** fan-out ViewModels used non-atomic `_uiState.value = _uiState.value.copy(...)` — parallel completions could drop each other's row updates (one row stuck "Loading" forever). Surfaced as a hanging test. Fix: atomic `update {}` in Home/Search/StreamList/Discover VMs. | JVM tests | FIXED |
| Details screen: movie (Toy Story 5 — backdrop, facts, cast, View streams) and series ("I Will Find You" — season selector, episode list w/ overviews) | AVD windowed, live internet | PASS |
| Stream list: honest empty state without stream addons; with local test addon → group + streams, torrent/external as unsupported notes | same | PASS |
| **PLAYBACK: full chain verified** — local test addon (http://10.0.2.2:8090, debug-only cleartext) → stream click → PlayableSource staging → ExoPlayer → 1080p H.264 plays → ENDED fires → "Playback finished" panel. Error panel verified twice with real failures (403 URL → "server rejected the request"; decoder fail → "device can't decode") | AVD **windowed** | PASS |
| **Emulator rule discovered:** goldfish H.264 decoder cannot init on a HEADLESS emulator (`-no-window` + swiftshader) — `DecoderInitializationException: c2.goldfish.h264.decoder`. Playback testing requires a windowed emulator (`-gpu auto`, no `-no-window`). Recorded in STATE env rules. | AVD | RULE |

## 2026-07-05 — Phase 1 COMPLETE: Discover + Search verified; two root causes fixed (session 2)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 52 tests (new: DiscoverViewModel select/paginate/dedupe/error, SearchViewModel fan-out/blank/failed-rows) | macOS, JDK 17, clean build | PASS (52/52) |
| Discover: left-rail catalog picker (Cinemeta + owner's AIO curated/merged catalogs), grid deep-browse of AIO "Trending" with rich posters, D-pad selection | AVD `openstream_tv_api34`, live internet | PASS (screenshots) |
| Search: "batman" fanned out to all search-capable catalogs; Popular·movie + Popular·series rows rendered in parallel | same | PASS |
| **ROOT CAUSE 1 — iCloud build corruption:** repo lives in iCloud-synced ~/Documents; CloudDocs created "name 2.class" conflict copies in build intermediates → recurring dexBuilderDebug failures, one stale APK, inflated test XMLs. Confirmed with `brctl status`. Fix: all build output → `build.nosync/` (DECISIONS #8). | host | FIXED |
| **ROOT CAUSE 2 — emulator clock drift → TLS failures:** snapshot resume left guest clock 9h behind; fresh Cloudflare edge certs "not yet valid" → "Chain validation failed" → NETWORK chips for a healthy addon. Fix: cold-boot emulator (`-no-snapshot`) so clock syncs from host RTC. Future sessions: always cold-boot or verify `adb shell date`. | AVD | FIXED |
| Also fixed: OkHttp callTimeout counted queue time with 60+ catalogs on one host (DECISIONS #9 — per-request timeouts + 16/host concurrency) | app | FIXED |
| Known-good failure: AIO "Trakt Recommendations" catalog hangs server-side 60s+ (curl too) — app shows failure chip after read timeout, exactly per §4.1.5/§4.1.8 | live addon | BY DESIGN |

## 2026-07-04 — Phase 1 GATE: catalog rows vs real AIOMetadata (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 46 tests (new: CatalogRepository refs/fetch, HomeViewModel fan-out incl. Failed rows, string-or-array regression) | macOS, JDK 17 | PASS (46/46, clean build) |
| Home rows from Cinemeta: Popular/Featured movie+series render with posters, ~6-col density, D-pad row scrolling, focus visible | AVD `openstream_tv_api34`, live internet | PASS |
| **GATE §10 Phase 1:** installed owner's private AIOMetadata instance (URL withheld — secret) via the UI; home renders its rows (Trending, Trakt catalogs, anime rows) incrementally alongside Cinemeta | same | PASS (screenshots reviewed) |
| **Wild-JSON bug found & fixed:** AIOMetadata sends `director` as a plain string where spec says array → BAD_JSON row. Fix: FlexibleStringListSerializer (string→1-elem list) on genres/director/cast + regression test | same | FIXED + PASS |
| **Security fix:** failure chips previously rendered the full addon URL (can embed personal tokens) on screen — now reason-only text, URLs never displayed | same | FIXED |
| Notes: catalogs requiring extras (e.g. Cinemeta "New" needs `genre`) correctly skipped via `isBrowsableFeed`. Known cosmetic: AIO rows titled "Trending · Trending" (addon uses odd type strings). Build-dir corruption from an earlier concurrent-gradle run caused one stale-APK confusion — resolved by full clean; NEVER run two gradle invocations concurrently. |  |  |

## 2026-07-04 — Phase 1: addon manager UI, end-to-end on emulator (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 38 tests (new: AddAddonViewModel state machine ×5) | macOS, JDK 17 | PASS (38/38) |
| D-pad-only walk: Home → Addons → Add addon → type real Cinemeta URL via on-screen keyboard → Fetch → preview (name/version/desc/types/resources/catalogs) → Install → list shows addon with Enabled/▲/▼/Remove | AVD `openstream_tv_api34`, live internet | PASS (screenshots) |
| **Focus bug found & fixed:** focused `BasicTextField` consumed DPAD_DOWN → focus trapped in URL field, Fetch unreachable. Fix: `onPreviewKeyEvent` routes UP/DOWN to `focusManager.moveFocus` + ImeAction.Go submits. Re-tested: PASS. Rule recorded as DECISIONS.md #7 | same | FIXED + PASS |
| Persistence: `am force-stop` → relaunch → addon list still shows Cinemeta (Room survives process death) | same | PASS |
| GitHub Actions `ci` on push (assembleDebug + unit tests) | ubuntu-latest | PASS (run 28702277593) |

## 2026-07-04 — Phase 1: AddonRepository + Room persistence (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 33 tests total; new AddonRepositoryTest: install/persist, same-manifest-id coexistence (§4.2), reinstall keeps order+enabled, failed install stores nothing, invalid URL fast-fail, uninstall+reorder | macOS, JDK 17, local JVM | PASS (33/33) |
| `./gradlew assembleDebug` (Room schema exported to app/schemas/) | same | PASS |

## 2026-07-04 — Phase 1: addon protocol DTOs + AddonClient (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 27 tests: manifest parsing (mixed resources, legacy extra, declares()), meta/stream parsing (channel/tv preserved, bingeGroup/proxyHeaders, infoHash kept-not-playable), URL normalization (stremio://), AddonClient vs MockAddonServer (paths, malformed JSON, 404, dead server, 1s-delayed body, colon ids) | macOS, JDK 17, local JVM | PASS (27/27) |
| `./gradlew assembleDebug` (Hilt NetworkModule/AddonModule wired) | same | PASS |

## 2026-07-04 — Phase 0 scaffold (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew assembleDebug` | macOS, JDK 17.0.19, Gradle 9.6.1, AGP 9.2.1 | PASS (APK ~18.6 MB) |
| `./gradlew testDebugUnitTest` (SanityTest: lenient JSON) | same | PASS |
| Hello screen boots + button focusable on TV emulator | AVD `openstream_tv_api34` (Android TV 14, 1080p, arm64), headless | PASS — MainActivity resumed, button rendered focused, 2× KEYCODE_DPAD_CENTER updated label to "OK pressed ×2" (screenshot-verified) |
