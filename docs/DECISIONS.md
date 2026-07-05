# DECISIONS.md — append-only log of non-obvious choices

Format per entry: date, decision, rationale, alternatives rejected. Never rewrite
old entries; add a new entry that supersedes.

---

## 1. 2026-07-04 — Pure Kotlin addon client, no stremio-core JNI (v1)

**Decision:** Implement the Stremio addon protocol natively in Kotlin. Do not
integrate `stremio-core` (Rust) via JNI in v1.

**Rationale:** The addon protocol is plain REST + JSON — a Kotlin client is a few
hundred lines. JNI + Rust toolchain (cargo-ndk, ABI matrix, memory-ownership
hazards) multiplies build complexity and locks out layman contributors, violating
Prime Directive 2 (KISS). We don't need Stremio account sync, its P2P engine, or
its Ctx state machine for v1.

**Escape hatch:** All addon access goes through a single `AddonClient` interface;
`stremio-core-kotlin` can be swapped in behind it later without touching UI.

**Rejected:** stremio-core-kotlin JNI bindings (build complexity), WebView-wrapping
stremio-web (GPL entanglement + not a real TV app).

## 2. 2026-07-04 — Project name: OpenStream TV

**Decision:** Working name **OpenStream TV**, repo slug `openstream-tv`,
applicationId/namespace `dev.openstream.tv`.

**Rationale:** The machine already had a TV emulator AVD named
`openstream_tv_api34` created by the owner during environment prep — strongest
available signal of the owner's preferred name. MASTER_PLAN §10 Phase 0 allowed
the session to pick.

**Note:** Trivially renameable until the public GitHub repo is created (repo name)
— but `applicationId` should be considered frozen once any build is distributed.
Owner may override; if so, add a superseding entry here.

## 3. 2026-07-04 — License: GPLv3

**Decision:** GPLv3 for the whole app (LICENSE at repo root).

**Rationale:** MASTER_PLAN §11 recommendation: matches ecosystem expectations for
Stremio-client projects, keeps forks open, and removes all ambiguity about
referencing GPL stremio-web code. Provisional until the owner confirms; switching
to MIT/Apache-2.0 is possible only while there are no outside contributors and no
stremio-web-derived code (currently true).

**Rejected (for now):** MIT/Apache-2.0 — would forbid ever copying from
stremio-web and offers weaker fork-openness guarantees.

## 4. 2026-07-04 — Toolchain: AGP 9.2.1 (built-in Kotlin), Gradle 9.6.1, current AndroidX line

**Decision:** AGP 9.2.1 + Gradle 9.6.1 wrapper + AGP built-in Kotlin (no
`org.jetbrains.kotlin.android` plugin) + Kotlin plugin line 2.3.21, KSP 2.3.9,
Hilt 2.60, Compose BOM 2026.06.01, tv-material 1.1.0, Media3 1.10.1, Room 2.8.4,
Coil 3.5.0, OkHttp 5.4.0. compileSdk/targetSdk 37, **minSdk 23**.

**Rationale (verified by building, not from docs alone):**
- Hilt ≥2.59 hard-requires AGP 9; the mid-2026 AndroidX line (lifecycle 2.11,
  Compose BOM 2026.06) requires AGP 9.1+ and compileSdk 37. Staying on AGP 8.x
  would have forced downgrading every library and re-upgrading within months.
- AGP 9 has built-in Kotlin support; applying kotlin-android is now an error.
  See https://developer.android.com/build/migrate-to-built-in-kotlin
- minSdk 23: Compose ≥1.8 requires API 23. Owner's lowest-end devices (onn boxes)
  run API 29+, so 23 is a comfortable floor (MASTER_PLAN §3.1 allowed 21–23).
- jvmTarget defaults to compileOptions targetCompatibility (17) under built-in
  Kotlin — no explicit kotlin block needed in app/build.gradle.kts.

**Rejected:** AGP 8.13.2 + Hilt 2.58 + 2025-era AndroidX (works, but ecosystem has
moved past it; tried first and abandoned after dependency-requirement errors).

## 5. 2026-07-04 — Networking: plain OkHttp + kotlinx.serialization (no Retrofit, no Ktor)

**Decision:** The addon client will use OkHttp directly with kotlinx.serialization
for JSON. Neither Retrofit nor Ktor client.

**Rationale:** MASTER_PLAN §3.1 said "Retrofit or Ktor — pick one", but every addon
has a *different, user-supplied base URL* and a tiny fixed path grammar
(`/{resource}/{type}/{id}.json`). Retrofit's value (typed interfaces per base URL)
mostly disappears with fully dynamic hosts, and it adds a layer a new contributor
must learn. One `AddonClient` class building URLs by string concatenation +
lenient `Json` decoding is simpler and easier to debug. If endpoint variety grows,
revisit with a superseding entry.

**Rejected:** Retrofit (dynamic-URL awkwardness), Ktor client (second HTTP stack to
learn; OkHttp is already required by Coil and MockWebServer tests).

## 6. 2026-07-04 — Navigation: androidx navigation-compose (classic, string routes)

**Decision:** `androidx.navigation:navigation-compose` (2.9.x) with plain string
routes in one `AppNavHost`.

**Rationale:** The screen graph will grow to ~8 destinations (home, discover,
search, details, streams, player, settings, addons) — hand-rolled state
navigation stops scaling past 2–3. Classic navigation-compose is the boring,
massively documented option a new contributor already knows. Back = popBackStack
= exactly one level, satisfying MASTER_PLAN §5.4.

**Rejected:** Navigation 3 (too new — thinner docs violate the layman-contributor
directive), manual back-stack state (doesn't scale, reimplements the wheel).

## 7. 2026-07-04 — TV text-field focus rule (learned from a real bug)

**Decision:** Every text input on a TV screen must route D-pad UP/DOWN out of the
field via `onPreviewKeyEvent` + `focusManager.moveFocus(...)`, and offer an IME
action (Go/Search) as the primary submit path.

**Rationale:** Found during on-emulator verification of the add-addon flow: a
focused Compose `BasicTextField` consumes D-pad DOWN, permanently trapping focus —
the §5.4 "focus never lost" rule also means "never trapped". `UrlField` in
AddAddonScreen.kt is the reference implementation for all future text inputs
(search screen!).

## 8. 2026-07-05 — Build outputs live in `build.nosync/` (iCloud-proofing)

**Decision:** All Gradle build output goes to `build.nosync/` (root
build.gradle.kts sets `layout.buildDirectory` for every project).

**Rationale:** The owner's repo lives under `~/Documents`, which macOS iCloud
Drive syncs. iCloud created `"name 2.class"` conflict copies inside
`app/build/intermediates` while builds ran, breaking D8 with unfathomable
errors and once producing a silently stale APK. Confirmed via `brctl status`
showing CloudDocs churning on build intermediates. Folders whose name ends in
`.nosync` are excluded from iCloud sync. Harmless on Linux/CI/Windows.

**Residual risk:** `.gradle/` (config cache) still syncs; if config-cache
corruption appears, either exclude `~/Documents/Claude` from iCloud or move the
repo out of `~/Documents` (owner's call).

**Rejected:** moving the repo (owner's filesystem, session paths keyed to it),
`--project-cache-dir` (CLI-flag-only, hurts "buildable by anyone").

## 9. 2026-07-05 — Addon HTTP: per-request timeouts, higher per-host concurrency

**Decision:** OkHttpClient uses connect(10s)/read(15s) timeouts — NOT
`callTimeout` — with dispatcher maxRequests=32, maxRequestsPerHost=16.

**Rationale:** One AIOMetadata instance exposes 60+ catalogs on one host. With
OkHttp's default 5-per-host and a 15s callTimeout (which counts dispatcher
QUEUE time), the home fan-out filled the queue and healthy queued calls died at
15s ("couldn't reach the addon" for an addon answering curl in 3s). Read
timeout still enforces the §4.1.5 budget against genuinely stalled addons.

## 10. 2026-07-04 — Autoplay §7.1: pure reducer + lexicographic cascade

**Decision:** `AutoplayStateMachine` is a pure `(state, event) → (state,
effect)` reducer fed 1-second Tick events; the §7.1 tier cascade is ONE
lexicographic comparator (bingeGroup match, then same-addon, same-resolution,
episode-normalized filename similarity, cache flag, then addon priority +
server order) in `StreamCascade.rank`, which returns the full ordered attempt
list.

**Rationale:** the flagship feature's rules (patience ceiling, 3-attempt
fallthrough, never-a-dead-screen) must be exhaustively unit-testable without
clocks or coroutines (§9.2). A comparator preserves the spec's STRICT tier
ordering — a weighted score would let a strong low-tier signal (cache flag)
outvote a weak higher-tier one (filename similarity).

**Non-obvious choices:**
- Attempt starts EARLY on a tier-1 bingeGroup hit (no better candidate can
  arrive by definition) and when all addons settle; the 60s patience ceiling
  only governs stragglers. At 60s: play whatever ranked, else manual list.
- All-settled-with-zero-playable goes straight to the manual list — waiting
  out the clock when every addon already answered helps nobody.
- Specials (season 0) sort AFTER regular seasons in binge order; unnumbered
  videos keep array order at the very end.
- "2160p" folds into "4k" for resolution equality; cache detection is the ⚡
  marker or the words cached/instant (AIOStreams convention).

## 11. 2026-07-05 — Autoplay stream fetches use a "patient" HTTP client

**Decision:** `AddonAutoplayGateway.fetchStreams` goes through a second
`AddonClient` built from the shared OkHttpClient with `readTimeout(50s)`
(`@Named("patientAddonClient")`, shares pool/dispatcher via newBuilder()).

**Rationale:** found live during the §7.2 delayed-addon run — the interactive
15s read timeout (#9) killed a 20s-delayed stream response, so the §7.1
"never cancel under 60s" promise could never survive past 15s: the fetch
"settled" as a failure and autoplay fell back to the manual list. Interactive
stream lists keep the snappy 15s budget; only autoplay waits patiently (50s
keeps the HTTP layer inside the machine's 60s patience ceiling).

## 12. 2026-07-05 — External players: per-launch choice, pure intent specs, same-player binge chain

**Decision:** §6.2 external playback ships as a per-launch "Play with…"
long-press on the stream list (the "Always use" setting waits for Phase 4's
settings screen). The VLC/MX intent dialects live in pure data
(`ExternalPlayers.kt`: LaunchSpec + result interpretation); the only Android
edge is `ExternalPlayerLauncher` behind an `ExternalPlayerPort` seam
(AutoplayGateway precedent — no MockK in this project).

**Non-obvious choices:**
- MX Pro package is preferred over free when both are installed — someone
  who paid expects the paid app to open.
- A successful `startActivityForResult` launch counts as autoplay attempt
  success (`onPlaybackReady`) — we cannot see inside an external player;
  ActivityNotFound is the §7.1 step 5 fallthrough.
- Result mapping is deliberately conservative: progress is saved ONLY when
  the player returned both position > 0 and duration > 0; anything else is
  Unknown and leaves stored progress untouched (a canceled launch must not
  wipe a real resume point). MX's `end_by=playback_completion` and the
  §7.1.6 near-complete rule (≥95% or last 30s) map to Finished → clear
  progress + run the Up Next flow.
- The §7.1.6 best-effort binge chain relaunches the SAME external player the
  user picked for this screen session; its manual fallback REPLACES the
  current stream list (Back must not walk the binge tail). The stream list
  hosts its own AutoplayController instance (the class is unscoped).
- VLC start-over launches send `from_start=true` so VLC's own resume memory
  cannot override the user's explicit "Start over".
- GENERIC always goes through `Intent.createChooser` — "Other apps…" must
  never silently bind to a previous "always" choice; it is only offered when
  PackageManager finds another video/* handler (no empty choosers, §5.4).

## 13. 2026-07-04 — Add-addon browser entry: hand-rolled ~100-line HTTP server

**Decision:** Typing a long manifest URL with a D-pad is the worst moment of
setup, so the Add-addon screen also serves a one-form web page on the LAN
(`RemoteEntryServer`, ports 8385–8389, first free wins). The browser is only
a long-range keyboard: the submitted URL feeds the exact same
fetch → preview → confirm flow on the TV (§4.1.1 stays on-screen; the server
never installs anything).

**Non-obvious choices:**
- Hand-rolled on `ServerSocket` instead of NanoHTTPD/Ktor: two routes,
  form-encoded bodies, `Connection: close` — a dependency would be bigger
  than the feature (KISS). Pure JVM, so tests hit it with real
  `HttpURLConnection` HTTP.
- SECURITY: manifest URLs are secrets. The page never echoes the submitted
  URL back (all response text is ours), nothing is logged, and there is no
  read endpoint — a LAN peer can submit but never enumerate installed
  addons. Test-covered (`never echoes the url`).
- Lifetime = screen lifetime: `DisposableEffect` start/stop, `onCleared` as
  the safety net. No background service listening forever.
- Fixed guessable port range instead of an ephemeral port: the TV shows the
  URL, a human types it — `:8385` is typeable, `:59371` is a typo farm.
- Off-network or all five ports taken → the hint simply doesn't render; the
  on-screen keyboard path is always there.

## 14. 2026-07-04 — Setup links instead of baked-in addons or a desktop client

**Context:** The owner wants family installs pre-configured with their
AIOStreams/AIOMetadata instances. Hardcoding those URLs is impossible — the
repo is public and the URLs embed personal tokens (CLAUDE.md security rule,
§4.2 "nothing AIOStreams-specific in code"). A desktop configuration client
is a second codebase for a one-time task (YAGNI).

**Decision:** A "setup link" — a privately-hosted JSON
(`{"openstream":1,"name":…,"addons":[{name,url}…]}`) the Add-addon input
accepts alongside manifest URLs. The TV fetches it, previews every addon
(name+version or per-entry error, URLs never rendered on screen), and
installs the good ones in profile order on one confirm. Combined with the
browser entry page (#13), a fresh box is configured by pasting one short
link on a phone. `tools/make_profiles.py` generates one profile per person
from the owner's private users.json (script is tracked and secret-free;
output goes to the gitignored private folder — host on the owner's domain
or any private URL).

**Non-obvious choices:**
- Sniffing order: URLs ending in `manifest.json` take the single-addon path;
  anything else http(s) is fetched as a profile candidate. The explicit
  `"openstream": 1` marker rejects arbitrary JSON that lenient parsing would
  otherwise coerce into an empty profile.
- Install order = profile order (metadata addons first, AIOStreams last),
  because §4.1.7 makes install order the stream-group order.
- Partial failure installs the good entries — one dead addon must not brick
  a family member's whole setup; the preview shows exactly what's broken.
- Stremio-style accounts/sync on the owner's future domain stays a §12
  non-goal for v1; users.json is the offline source of truth until then.

## 15. 2026-07-04 — Profiles mirror live Stremio accounts; name-lookup page tradeoff

**Decision:** `tools/pull_stremio_addons.py` logs into each family Stremio
account (credentials in the owner's private users.json — accounts the owner
manages) via api.strem.io login → addonCollectionGet, and saves each
account's real addon list. `make_profiles.py` unions: Cinemeta + curated
catalog addons from users.json (TMDB/AIOLists/MediaFusion — present there
but NOT in the live accounts) + the live collection in account order,
deduped by URL. Localhost transport URLs (Stremio local-files server) are
dropped — they can't work from a TV.

**Name lookup (owner-requested UX):** `tools/make_hosting_bundle.py` emits
an upload-ready folder (Dreamhost/any PHP host): index.php where a person
types "first name + last initial" and gets their setup link with a copy
button. The name→filename map stays server-side in PHP so the tokenized
filenames are never listed; ambiguous names ("myles m") get a pick-one
prompt; "Anna/Jay" style names answer to either first name.
**Accepted tradeoff:** anyone who knows the domain and a family member's
first name + initial can obtain that person's profile URL (which embeds
tokens). This is family-scale obscurity, not auth — acceptable to the owner
today; a shared PIN in index.php is the documented upgrade path if that
changes. Generated bundle lives in the gitignored private folder; only the
generators are tracked.

## 16. 2026-07-05 — Discover is a Stremio-style category tree (Type → Catalog → Genre)

**Decision:** Replaced the left-rail catalog list with the exact filter model
web.stremio.com uses (verified in-browser this session): three pickers across
the top — Type (distinct catalog types across enabled addons, first-seen
order), Catalog (all catalogs of that type, addon-then-manifest order, §4.1.7
never re-sorted), Genre (the selected catalog's declared `genre` options,
"None" default). Picking at any level resets everything below it. State lives
in DiscoverViewModel as a tree selection; the grid + skip pagination are
unchanged.

- **Genre-required catalogs are Discover-only.** Cinemeta's "New" (and year
  lists) require a genre and were previously invisible everywhere. New
  `ManifestCatalog.isDiscoverable` admits catalogs whose only *required*
  extra is a genre with declared options; the ViewModel auto-selects the
  first option so the fetch can't 404, and the picker offers no "None".
  Home rows still use `isBrowsableFeed` — unchanged.
- **Genre options come from the modern `extra` notation only.** The legacy
  short notation can say "genre supported" but never lists options, so
  there is nothing to put in a picker; such catalogs simply show no Genre
  chip (fetches still work without the extra).
- **Pickers are trapped-focus Dialogs** (same rationale as ResumeDialog,
  §5.4): initial focus on the current selection, plain scrollable Column
  (not lazy) because focus-scrolling a lazy list is unreliable and one
  AIOMetadata instance declares 60+ catalogs — fine to compose once.
- The owner's AIOMetadata instance declares custom type strings (Trending,
  Collections, Charts, By Decade, Anime, Networks…). These appear as
  first-class Types — correct per §8 (types are raw strings, never enums),
  and matches real Stremio behavior.

## 17. 2026-07-05 — Addon HTTP disk cache: 30-min TTL, stale-beats-offline, streams never cached

**Decision:** One OkHttp `Cache` (50 MB, cacheDir/addon_http) + two
interceptors in `AddonHttpCache`. Catalog/meta GETs get a forced
`Cache-Control: public, max-age=1800` stamped on successful responses (addons
rarely send usable cache headers); within the TTL a relaunch renders with
ZERO network traffic (measured on the emulator via /proc/net/dev: 0 bytes
across force-stop → relaunch). On network failure, an application-level
retry with `only-if-cached, max-stale=∞` serves the last-known copy — stale
data beats an error chip. `/stream/` responses are stamped `no-store`:
AIOStreams-style URLs embed expiring tokens.

**Hard-earned:** CDN-fronted addons (Cinemeta = Cloudflare) send `Age` in the
thousands; leaving it in place made our max-age stale ON ARRIVAL, so online
launches refetched everything while offline launches (via the stale
fallback) were instant — thoroughly confusing. The response rewrite must
strip `Age` and `Expires` so freshness counts from our receipt time.
Regression-tested (`CDN Age header must not make fresh responses stale`).

Why not a Room snapshot (SWR): the HTTP cache is ~40 lines and already
yields instant relaunches; render-then-refresh adds UI states for data that
changes daily. Revisit only if >30-min-old launches still feel slow — and
note the boxes run unoptimized DEBUG builds; R8/release is the next lever.

## 18. 2026-07-05 — Per-screen view options (Discover: density + sort) behind a ViewPrefs seam

**Decision:** Owner wants view/sort experiments "customizable on the same
screen it's used on". First slice: a View chip on Discover → dialog with
Density (6/8 columns) and Sort (addon order / A–Z / newest / top rated),
persisted in Preferences DataStore behind a `ViewPrefs` interface (JVM tests
use an in-memory fake). Sort is deliberately CLIENT-SIDE over loaded items —
addons own server-side order (§4.1.7); this is a lens, not a protocol
feature, and the dialog labels it "Sort loaded items". Picks apply live
behind the dialog; Back closes. Grid density here is the §5.1 experiment bed;
the global settings-screen version still lands with Phase 4 unit 1.

## 19. 2026-07-05 — Player track selection: pure menu model, UP-key dialog, session-scoped picks

**Decision:** Audio & subtitle selection (owner request — the family boxes
need captions, and Stremio can't switch audio language) is split at a
`RawTrack` seam: `buildTrackMenu`/`trackDisplayName` are pure functions
(naming via `java.util.Locale`, "5.1"/"7.1" channel annotation, duplicate
numbering, subtitles-off detection) with JVM tests; the media3 boundary
(`Tracks → RawTrack`, `TrackSelectionOverride` apply, text-type disable) is
two thin untested mappers. UI is a `TracksDialog` on DPAD_UP — same
trapped-focus real-Dialog pattern as Discover's pickers, two side-by-side
sections (`usePlatformDefaultWidth = false`; the platform default width
squeezed column two into a sliver — found on the emulator).

Addon subtitles were ALREADY attached as `SubtitleConfiguration`s in
`ExoPlayerEngine.play()`; the picker was the missing half. Track picks are
session-scoped on purpose: a persistent preferred-language setting belongs
to the Phase 4 settings screen (record there when it lands). The fixture
addon now serves two synthetic .srt tracks (cue every 10 s) so caption
selection stays emulator-testable without real streams.

## 20. 2026-07-05 — R8 release build, debug-signed, as the deploy channel for the boxes

**Decision:** The onn boxes (32-bit, 2–3 GB RAM) ran unoptimized DEBUG
builds — the owner's "choppy animation" report is largely debug overhead
(box audit, DECISIONS in TESTLOG 2026-07-05 alpha.5). `release` now builds
with R8 + resource shrinking (APK 18.6 MB → 3.2 MB) and is deliberately
signed with the DEBUG keystore so `adb install -r` upgrades the boxes'
existing installs in place — addon DB and watch progress survive. Proper
release signing is a Phase 5 concern. Proguard: library consumer rules are
trusted; only our kotlinx-serialization DTO serializers get explicit keeps
(app/proguard-rules.pro). Verified on-emulator before deploy: home rows,
Discover tree, stream fetch, resume dialog, playback frames.

## 21. 2026-07-05 — Cleartext HTTP allowed app-wide (both build types)

**Decision:** The first R8 release smoke test failed with "CLEARTEXT
communication not permitted": release builds had the platform default
(HTTPS-only, targetSdk ≥ 28), and the old debug-only overlay allowed http
ONLY to 10.0.2.2/localhost — meaning http addons/streams were silently
broken everywhere but the emulator fixture. Stremio addons routinely serve
plain-HTTP streams (Live-TV/IPTV especially — §8 compatibility is a hard
constraint), so `android:usesCleartextTraffic="true"` now sits in the MAIN
manifest and the debug overlay is deleted (a networkSecurityConfig would
silently override the flag). Debug and release now have identical network
policy.

## 22. 2026-07-05 — Always-running animations read their clock in the draw/layer phase only

**Decision:** The Discover ghost loader (figure-8 comet, GhostLoader.kt) and
skeleton shimmer cards animate by reading their `infiniteTransition` value
INSIDE the Canvas draw lambda / `graphicsLayer{}` block — draw/layer-phase
invalidation only, zero per-frame recomposition or layout. On 32-bit boxes
this is the difference between a free animation and a janky one; treat it
as the house rule for any loader/pulse/ambient motion added later.
