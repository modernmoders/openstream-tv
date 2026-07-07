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

## 23. 2026-07-05 — Home-row customization: one JSON blob, keys tolerate staleness, hidden = never fetched

**Decision:** The Phase 4 row manager stores `HomeRowPrefs` (order list,
hidden set, renames map — all keyed by `CatalogRef.key`) as ONE JSON blob
under a single DataStore preference. The three fields always change from the
same screen, and a future schema change degrades to defaults instead of
needing a migration. Entries whose row no longer exists (addon removed,
manifest changed) are simply ignored at apply time — no eager garbage
collection. Apply rule: user-ordered rows first, everything untouched keeps
addon order after them (§4.1.7 stays the default), so a brand-new catalog
appears at the end instead of vanishing. A ▲/▼ move pins the FULL current
order (predictability over freshness). Hidden rows are filtered BEFORE the
home fan-out — a hidden row's catalog is never fetched, not just not drawn —
and prefs changes restart the fan-out via `combine`, which the DECISIONS #17
HTTP cache makes cheap. Continue Watching is not managed here (§5.6
always-first is a hard rule, and elders losing that row would be a support
call).

## 24. 2026-07-05 — Playback preferences: languages are hints, the player setting never dead-ends

**Decision:** (a) **Languages (DECISIONS #19 follow-up):** every audio/
subtitle pick in the player's tracks dialog is persisted (audio tag,
subtitle tag, or a SUBTITLES_OFF sentinel) and re-applied before the next
playback via `setPreferredAudio/TextLanguage` — PREFERENCES, not track
overrides, so a stream without the remembered language falls back to
ExoPlayer's normal choice instead of failing. Picks on tag-less/und tracks
change nothing (a stored preference must survive picking "Track 1").
(b) **"Always use" player (§6.2):** stored as `internal` / `ask` / an
`ExternalPlayer` enum name, resolved against the players installed AT CLICK
TIME (`resolvePreferredPlayer`, pure + table-tested): an uninstalled
preferred player silently falls back to internal — never a dead OK press —
and `ask` with nothing installed behaves as internal. Long-press "Play
with…" stays the one-off override regardless of the setting. The §7.1.6
external binge chain inherits whatever player actually launched.

## 25. 2026-07-05 — Remote addon management: the hosted setup profile is a live source of truth

**Decision:** Boxes remember the setup link they were installed from
(`ProfileLink` in DataStore, saved on the confirmed install-all) and
`ProfileSync` re-fetches it on every app start, throttled to one successful
sync per 15 minutes. Diffing is a pure function (`planSync`): install what
the profile added, remove what it dropped, and only ever remove addons the
profile itself installed (the "managed" set) — hand-added addons are
invisible to the sync. A profile addon the user removed by hand comes back:
the owner's hosted file always wins, that IS the feature (the owner manages
far-away family boxes by editing one JSON on his host — the Stremio
sign-into-their-account workflow, minus accounts). Failure policy is the
elder rule: sync failures are logged, never shown; an unreachable profile
changes nothing and lastSync doesn't advance, so "I fixed it — restart the
app" always works. 15-minute throttle, not daily, for the same reason.
Reorder-on-sync is deferred (YAGNI): install order follows profile order for
NEW installs; a pure reorder in the profile doesn't reshuffle existing boxes
until someone actually needs it. SECURITY: managed URLs embed tokens — they
live in app-private DataStore and are never logged (counts only).

**Rejected:** per-box push/remote-control (needs a reachable box or a relay
server); accounts with cloud sync (KISS, and the owner IS the server);
sync-on-timer while running (launch-time only is enough for a TV app that
gets fully backgrounded/killed between sessions).

## 26. 2026-07-06 — Auto-play first stream: settled-prefix rule, shared walk list, no wrap-around

**Decision:** The "Auto-play first stream" setting (default OFF, one toggle
in Settings) launches the §4.1.7 top-of-list stream hands-free: no stream
list to understand, no resume dialog (existing progress resumes
automatically — the elder flow is "click the show, keep watching"). Timing
is the **settled-prefix rule** (`firstPlayableWhenSettled`, pure): the top
stream is only final when every addon-order group BEFORE the first playable
result has settled (loaded/failed) — the fan-out renders incrementally and
a slow FIRST addon must not lose its top spot to a fast second. Auto-start
fires at most once per screen (`playbackStarted`, also set by manual picks)
so Back from the player lands on a calm list. "Always use VLC/MX" is
honored; "Ask every time" falls back to the internal player (can't ask
hands-free). The same setting doubles as auto-skip-on-error in the player:
up to MAX_ERROR_SKIPS(3) consecutive PlayerEvent.Errors advance to the next
stream (autoplay's §7.1 error handling keeps precedence), a Ready resets
the count, then the error panel. Manual "Try another server" (▼ in the
player → confirm dialog; also on the error panel) walks the same shared
`StreamAlternatives` list (singleton hand-off like CurrentPlayback: addon
order, playable only, position carried over, NO wrap-around — a full lap of
broken streams must end at an honest error, not spin). Autoplay episode
swaps clear the list (next episode's alternatives are unknown until its
own screen loads).

**Rejected:** wrap-around walking (infinite loop of broken streams);
auto-start racing on the literal first Loaded group (top spot not stable);
a focusable overlay button in the player (D-pad ownership conflicts with
play/pause — ▼ + trapped dialog instead).

## 27. 2026-07-06 (session 13) — One-step name setup, secret domain via BuildConfig, Expert mode

**Decision (owner directive):** Setup must be one step — a person types their
NAME on the TV and the app does everything else (look up who they are, fetch
their hosted profile, install every addon). Nobody ever sees, copies, or
pastes a link. This replaces the two-step "phone → copy link → paste on TV"
flow (which stays alive only as an Expert-mode fallback for pre-alpha.10
boxes).

**How it fits together:**
- **Secret domain stays out of git.** The setup site URL unlocks name→profile
  lookups, so it's a secret like the addon tokens (CLAUDE.md). It lives in the
  gitignored `local.properties` as `setup.url` / `setup.brand`, surfaced to
  code via `buildConfigField` → `BuildConfig.SETUP_URL/SETUP_BRAND` →
  `SetupConfig` (a plain injected value object, so everything stays
  JVM-testable). A build with no `setup.url` simply hides the whole name flow
  and behaves like the classic open-source app — the feature degrades, it
  doesn't break.
- **Site gains a JSON API, keeps one file.** `tools/make_hosting_bundle.py`'s
  `index.php` now answers `POST api=1` with JSON (`{ok, name, link}` /
  `{ok:false, choices}` / `{ok:false, error}`) for the app, alongside the
  human page. The name→file map still lives server-side, so profile filenames
  (the secret part) are never listed. Human page now leads with "set it up on
  the TV"; the raw link is demoted to a collapsible for old builds.
- **`ProfileInstaller` is the one install path.** Plan (fetch profile + every
  manifest in parallel, keep profile order) → confirm → install sequentially →
  remember the link for ProfileSync (#25). Both the name flow
  (`ConnectViewModel`) and the expert paste-a-link flow (`AddAddonViewModel`)
  delegate to it, so install/order/remember logic lives in exactly one place.
- **First launch routes by state.** `LaunchViewModel`: configured build +
  nothing installed → Welcome/Connect screen; else Home. `take(1)` so
  installing addons mid-Connect doesn't yank the nav graph out from under the
  running flow. null-until-first-emission avoids a Home flash (same trick as
  HomeViewModel).
- **Normal vs Expert (`ViewPrefs.expertMode`, default OFF).** Technical
  surfaces are invisible by default: Home lost its "Addons" button (title is
  now the brand), and the addon manager moved to Settings → Expert mode →
  Addons, shown only when Expert is on. This is the "hide a toggle deep in
  settings" the owner asked for; future logs/diagnostics land behind the same
  flag.

**Status:** code complete, 236 unit tests green (13 new). NOT yet
emulator-verified end-to-end and NOT deployed. The live <setup-domain>/setup/
still runs the OLD HTML-only index.php — the regenerated one MUST be
re-uploaded before the name flow works on real hardware.

**Rejected:** shipping the domain in a committed config or resource (it's a
secret); a separate lookup microservice (the PHP page already knows the map —
just teach it JSON); duplicating install logic in the Connect flow (extracted
`ProfileInstaller` instead); making Expert mode a build flavor (a runtime
toggle lets one box be "the admin's" without a separate APK).

## 28. 2026-07-06 (session 14) — Connect flow: no accept screen, auto-install, minimal welcome

First real-hardware run of #27 worked, but the owner found the Welcome flow
over-built: a 3-step guide, a filler intro ("we'll take care of the rest"), an
example name ("jody m") that named a real person, an accept/confirm screen
between typing and installing, and content centered where the on-screen
keyboard covered it. Reworked to the least-friction shape:

- **No accept screen.** A typed name goes straight to install — `ConnectVM`
  dropped the `Ready` state and `confirm()`; `submitName` → lookup → (Found) →
  plan → install → `Done`, all under one "Setting up your shows…" message.
  The expert Add-addon screen still previews-then-confirms (§4.1.1) for the
  paste-a-link path; the family Connect path trades the preview for zero
  friction because the profile is trusted (it's the person's own hosted set).
- **`Done` is a message that fades**, not a button — it shows "You're all set,
  <first name>!" for ~1.7 s then auto-navigates Home. The only remaining tap
  on the whole flow is typing the name.
- **Minimal copy:** just "Welcome to <brand>" + "What's your name?" + field +
  a low-key "Skip for now". No guide, no example name, no filler.
- **Keyboard-aware layout:** only the name step lifts toward the top (so the
  IME never covers it); every other step is centered. Steps cross-fade with a
  gentle lift via `AnimatedContent` + `SizeTransform(clip = false)` so the
  focus highlight is never clipped (the §5.3 clip rule, applied to a
  non-scroll screen). Ambiguous names still detour through the WhichOne picker.

**Rejected:** keeping a confirm step "for safety" (the family path has nothing
to decide — it's their own profile, and the expert path still confirms);
a manual "Start watching" button on Done (one more press the owner explicitly
didn't want).

## 29. 2026-07-06 (session 14) — Refined UI language: shared tokens, Surface rows

Owner: "the menus, typography and spacing are horrible… make it feel amazing,
snappy and smooth" but "refined, not dramatic". Established one shared visual
vocabulary in `theme/OpenStreamTheme.kt` (the single palette home): `Accent`
(#4DA3FF, the one interaction color), `SurfaceCard`/`SurfaceCardFocused`
(quiet resting surface → calm accent tint on focus, no white invert),
`Hairline` border. List rows/pickers now use TV Material `Surface` (native,
non-stuttering focus scale + color/border transitions) instead of the flat
`Button` that inverted to white — title white in both states, description
stays muted-readable, trailing chevron cues "opens", accent border on focus.
Applied first to Settings + its dialogs (owner screenshotted it as "horrible");
rolls out to the other menus in order.

**Rejected:** a heavy marquee/neon redesign (owner chose refined); per-screen
ad-hoc colors (all go through the shared tokens so a future change is one file).

## 30. 2026-07-06 (session 14) — Home featured hero (refined marquee)

Owner wanted the big-bold marquee feel from their reference pics, but
"refined, not dramatic". Added a single quiet spotlight at the top of Home:
the first item of the first catalog that returns content, shown as a
focusable Surface with the item's backdrop (when the addon provides one)
under a left→right + bottom scrim, its poster, a headline-sized title, a
one-line meta (type · year · ★rating), a 3-line description, and an accent
"Press OK to watch". Falls back gracefully to the surface tint when a catalog
item carries no backdrop (many do not). Not a rotating carousel — one calm
pick, no motion.

Two focus/scroll fixes it needed: (1) Home now anchors initial focus on the
header (Discover) so it opens at the very top with the hero in view (the §10
predictable-entry rule); (2) because a LazyColumn anchors scroll to its first
item, the hero — inserted at the top only after rows load — landed above the
viewport, so the list snaps to item 0 once when the hero first appears.

**Rejected:** a full-bleed auto-rotating hero carousel (dramatic + motion the
owner didn't want, and it fights D-pad focus); putting the hero above
Continue Watching permanently was kept simple — hero is a distinct spotlight
band, CW remains the first *row*.

## 31. 2026-07-06 (session 14) — App-wide screen motion + shared refined surfaces

Owner UX pass #4 (motion) + finishing the #3 stragglers. (a) Every NavHost
destination gets one shared transition — a quick fade + whisper of scale
(enter/exit/pop) — so screen changes feel alive but never stutter or
disorient; set once on the NavHost. (b) Extracted the refined language into
`ui/components/Surfaces.kt`: `SurfacePill` (nav/filter pills), `SurfaceRow`
(full-width list row w/ optional long-press), `OptionRow` (picker option w/
selected accent tick + optional sublabel). Applied to the Home header pills,
the Discover filter bar, the stream-list rows, and the Discover pickers — all
now speak the DECISIONS #29 language (calm surface → accent tint + border +
native scale on focus, no white invert).

Left as-is: the Settings pickers keep their earlier private row (visually
identical); the small Streams/Rename action dialogs keep plain buttons
(1–2 buttons, white-invert is fine there); Details/Streams screen titles were
already consistent. `BackButton` stays outlined so "back" reads distinct.

**Rejected:** per-screen motion overrides (one shared spec is calmer and
cheaper); sliding page transitions (read as heavy on a 10-foot UI — a fade +
micro-scale is the "refined, not dramatic" choice).

## 32. 2026-07-06 (session 14) — Player control bar: wake-then-navigate + plain-word escapes

Owner (round 8) found the old player cryptic ("▲ audio settings") and wanted a
modern control bar. Rebuilt PlayerScreen.kt: while playing the screen is clean;
ANY key wakes the bar and lands focus on a scrub bar (accent focus ring). On
the scrub bar ◀▶ seek ±10s and OK play/pauses; DOWN drops to a row of
plainly-LABELLED buttons — "Audio & subtitles", "Try a different stream" (only
when another stream exists), "Play in another app" (only when VLC/MX is
installed). Auto-hides after 5s. The error panel carries the same two escapes.

Failure model (owner's real-world cases — plays-but-no-audio / no-video /
wrong-language / ExoPlayer codec gaps on the 32-bit boxes): auto still tries
the next STREAM first (up to 3, silent), because "won't play" is the common
case; the manual "Play in another app" hands the CURRENT stream to VLC for the
"plays wrong" cases ExoPlayer can't detect. We do NOT auto-launch VLC (if it
also failed it would bounce a non-technical viewer out into a foreign app) —
it's one obvious, labelled tap instead. PlayerViewModel gained
externalPlayers + externalIntentForCurrent (pauses our engine, hands off at the
current position). Also: auto-mode no longer flashes the stream list — the
list screen shows a calm "Starting…" until play or give-up (UiState.autoStarting).

**Rejected:** OK-pauses-immediately-when-hidden (owner explicitly wanted
wake-first, "bring up the menu"); auto-falling-back to VLC (magic + risky exit);
a floating seek cursor separate from play focus (one scrub-bar focus that both
seeks and toggles is simpler on a D-pad).

## 33. 2026-07-06 (session 14) — Home header lives INSIDE the scroll (hold-UP stick fix)

Owner: scrolled deep in Home, HOLDING UP sticks partway (hero half-shown)
until a Down+Up. Mechanism: the brand/pills header was pinned OUTSIDE the
LazyColumn, so during key-repeat focus could escape the list onto the pinned
pills while the list's bring-into-view animation was still mid-flight; the
escape cancels the animation, and because the pinned header itself needs no
scrolling, nothing ever finished the scroll — Home rested half-scrolled.

Fix: the header is now the LazyColumn's item 0 (plus a branch-aware entry
focus request, since each Home branch composes its own header instance).
Reaching the header now forces the list to finish scrolling to the very top —
the stuck-partway state cannot persist. Side effect (accepted, standard TV
pattern): the header scrolls away while browsing rows, giving content the
full screen.

Verified on the emulator: entry lands on Discover with the hero fully in
view; rapid 12×UP from 6 rows deep settles at the true top. Genuine remote
key-repeat is NOT reproducible via adb (`input keyevent` sends discrete
down/up pairs; InputDispatcher only synthesizes repeats for real driver-held
keys; `emu event send EV_KEY` never reaches the input pipeline on this AVD —
verified via dumpsys input RecentQueue), so the hold-UP fix itself needs the
owner's remote for final confirmation.

**Rejected:** pivot-style BringIntoViewSpec tuning (treats the symptom —
focus could still escape to a pinned header and cancel the scroll);
key-repeat throttling via onPreviewKeyEvent (fights the framework, breaks
fast browsing).

## 34. 2026-07-06 (session 14) — On-device error log: DiagnosticsLog + App log screen

MASTER_PLAN §10 owner directive ("Don't show them the errors and stuff, but
log them"). Viewers keep the existing quiet, friendly fallbacks (toChipMessage
chips, plain-language player panel); the raw detail now lands in
`diagnostics/DiagnosticsLog` — one plain-text file in filesDir, newest last,
trimmed at 400→300 lines so it can't grow unbounded on the 32-bit boxes —
readable at Settings → Expert mode → App log (monospace, newest first,
focusable lines so the D-pad can scroll, Clear log).

Security: every line is sanitized BEFORE hitting disk (any scheme://…
becomes "‹address hidden›", 300-char cap) because addon URLs embed personal
tokens and exception messages repeat them (verified live: a dead-server
streams failure logged with its manifest URL hidden). Same reasoning removed
the full-URL logcat lines in OkHttpAddonClient/SetupProfile (now exception
class only — they'd leaked the URL+host into logcat since Phase 1).

Wiring altitude: the REPOSITORY chokepoints (CatalogRepository /
StreamRepository / MetaRepository / ProfileSync) + PlayerViewModel — not the
five ViewModels — so every surface (home/search/discover/streams/details/
player/sync) is covered by five call sites that already know the addon name.
PlayerEvent.Error gained a screen-forbidden `detail` (errorCodeName + cause):
playback failures now log the codec story, which is exactly what the
owner-reported "Naruto plays in VLC but not internally" needs diagnosed
WITHOUT an adb hookup (NEXT ACTION 1b).

`DiagnosticsSink` is a fun interface with a defaulted no-op param
(`= DiagnosticsSink.NONE`) — Dagger ignores Kotlin defaults so Hilt still
injects the real log, while the 8 test files constructing these classes
directly stay untouched. MetaRepository logs only the FINAL failure (a
stream-only addon skipping an item is normal, all addons failing is a
diagnosis).

**Rejected:** logging in each ViewModel (5× the call sites, breaks VM test
constructors); logcat-only (the owner has no adb); a Room table (a text file
the size of a poster is not a database problem).

## 35. 2026-07-06 (session 14) — Daily App-log upload to the setup site

Owner ask ("have everyone's logs sent to me or uploaded to the site once a
day"). `DiagnosticsUpload` runs beside ProfileSync on every app start: at
most one SUCCESSFUL upload per 24h (failure = silent retry next launch,
same posture), POSTing `api=log` + `who` + the log text to the setup site's
index.php, which stores it as `logs/<profile-stem>.log` next to the
profiles. The owner reads every box at
`<setup-url>/logs/<stem>.log` — no TV visits, no adb.

Privacy/security: only the ALREADY-SANITIZED log leaves the box
(DiagnosticsLog strips URLs before disk, DECISIONS #34); the box
identifies itself by its profile FILENAME STEM (unguessable, token-free) —
never the link; the PHP endpoint only accepts a `who` matching an existing
profile file (strangers can't write arbitrary files) and caps the body at
128 KB. Same family-scale-obscurity model as the profiles themselves.
Upload failures are deliberately NOT recorded to the log — that would grow
the very log we failed to ship, every launch.

No box is identified by device id — identity IS the profile, so two boxes
connected to the same person overwrite each other's log (accepted: the
newest report wins, and the family runs one box per person).

**Rejected:** email ("sent to me" literally — needs credentials on every
box); per-device ids (meaningless to the owner; person names are the unit
they think in); appending server-side (the box log is already a ring
buffer — overwrite keeps the file bounded and idempotent).

## 36. 2026-07-07 (session 16) — Client-side absolute episode numbering + brand-driven launcher label

Two owner asks landed together.

**Anime numbering.** Settings > "Episode numbering" toggles the episode list
between per-season ("Season 3, Episode 32") and straight-through absolute
("Episode 115"). The absolute number is computed IN THE APP
(`absoluteEpisodeNumbers`): every episode with season >= 1, ordered by
(season, episode), numbered 1..N; specials (season 0) are excluded and keep
their per-season label. We compute rather than trust the addon because addons
disagree — Cinemeta's `videos[].number` is only the within-season index
(Naruto S3E32 has number=32, not 115), and MAL/AIOMetadata may emit absolute
directly; computing makes the toggle uniform. Read once when Details opens (a
fresh VM per navigation picks up changes). Scoped to the Details episode list;
the player/Up-Next titles still build their own season·episode string.

**Launcher name = brand.** The app is "SStreams". The launcher label was
`@string/app_name` ("OpenStream TV"); it now uses a `${appLabel}`
manifestPlaceholder fed from the SAME owner-private `setup.brand`
(local.properties) that already drives the in-app title, defaulting to
"OpenStream TV" when absent. Keeps the owner's brand out of the public repo
(CLAUDE.md) instead of hard-coding it in strings.xml. Launcher ICON art is
untouched — the separate R3 logo step.

**Rejected:** trusting the addon's `number` field (per-season for Cinemeta,
unreliable); flattening seasons into one list in absolute mode (kept the
season selector for navigation, only relabeled episodes); hard-coding the
brand in strings.xml (leaks the owner brand into the open-source repo).
