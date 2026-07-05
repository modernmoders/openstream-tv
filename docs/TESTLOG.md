# TESTLOG.md — what was tested, where, and the result

Append-only. Newest entries at the top.

---

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
