# TESTLOG.md — what was tested, where, and the result

Append-only. Newest entries at the top.

---

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
