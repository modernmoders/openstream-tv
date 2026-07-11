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

## 37. 2026-07-07 (session 16 cont.) — Poster focus-reveal, Reset this TV, player fallback, addon trim

Four independent fixes landed together; grouping the rationale here.

**Poster/Continue-Watching title reveal-on-focus.** Root cause of "artwork
covers the title" (owner report): the title sat in a plain `Text` sibling
below the `Card`, while the `Card` itself carries TV Material's default
1.1x focus scale — a transform that grows the rendered card into that space
without reserving any extra layout room, so the artwork drew over the
static text on every focus. Fix: the title now lives INSIDE the Card as a
bottom-scrim overlay, alpha-faded in/out with focus (`Modifier.alpha`,
draw-phase only, same house rule as #22) — it scales and fades WITH the
artwork instead of being run over by it. Trade-off, stated plainly: titles
are no longer visible on unfocused cards (Netflix-style hover reveal) —
this is what the owner explicitly asked for ("expand with artwork"), not an
accessibility regression (AsyncImage's contentDescription still carries the
name).

**"Reset this TV."** Owner wanted a way back to the fresh-install
"What's your name?" screen without adb (e.g. handing a box to someone
else). Built as a real Settings > Expert mode entry behind a confirm
dialog, not a hidden cheat code — discoverable, and destructive actions
should never require remembering a secret gesture. Clears
`AddonRepository.uninstallAll()` + `ProfileSyncPrefs.clear()` only —
poster density, player choice, and other personal prefs are deliberately
untouched; those aren't part of "who is this box."

**Connect screen: "Skip for now" removed.** It let a fresh box exit to
Home with zero addons installed. Replaced with a "Continue" button that
calls the same `onSubmit` the keyboard's Go/Done action already used —
submit-only, and DOWN-from-the-field now has a real, visible thing to land
on instead of nothing.

**Player: "Try a different stream" made unconditional.** It only rendered
when `canTryNext` was true (the ranked stream cascade had another
candidate); when exhausted, it vanished and DOWN-navigation from the scrub
bar/error panel landed on "Play in another app" by 2D nearest-neighbor
focus search instead — a different escape hatch than the one the owner
wanted first. `PlayerViewModel.tryAnotherStream()` now always does
something useful: walks to the next candidate if one exists, else opens
the full stream list for the same video. The error panel also grabs focus
on it explicitly via `FocusRequester` (deterministic, not a geometry
guess), and it's ordered last, right before Back (owner: wants it
rightmost for visibility).

**Addon trim + reorder (`tools/make_profiles.py`).** Every profile still
carried MediaFusion + TMDB + Trakt Scrobble alongside AIOStreams —
duplicating catalogs AIOStreams already wraps internally (Service Wrap),
per the finalize plan from 2026-07-06 that was logged but never executed.
AIOStreams/AIOMetadata are now assembled right after Cinemeta instead of
after the supplementary addons — Home/catalog order follows addon order
(§4.1.7), so the old order buried AIOStreams' own rows at the bottom.
MediaFusion + TMDB are excluded by default via
`DEFAULT_EXCLUDED_ADDON_KEYS` (owner-approved 2026-07-07); Trakt Scrobble
and Aiolists stay for now — the owner is still deciding on Trakt Scrobble
(no real scrobbling happens through it either way; see MASTER_PLAN §10)
pending confirmation that AIOLists doesn't already cover the same rows.
**users.json itself is never touched or trimmed** — only which of its
existing manifest URLs get bundled into a generated profile. Verified
against live data: all 11 existing hosted filenames unchanged (no box
re-pairing needed), Rachael keeps her AIOMetadata.

**Rejected:** a hidden reset gesture/keycode (less discoverable, easy to
trigger by accident, no confirm step); hiding the poster title permanently
instead of a focus reveal (loses the "expand with artwork" look the owner
asked for); silently dropping Trakt Scrobble alongside MediaFusion/TMDB
(not yet confirmed redundant — a wrong guess here removes a family
member's Trakt rows with no way to notice until someone complains).

## 38. 2026-07-07 (session 17) — Ambient section backgrounds, interface sounds, dual-S brand art (alpha.21)

Three round-10 requests landed together, plus the Live-TV investigation
that closed R4 without code.

**Ambient per-section backgrounds.** Owner asked for "opaque pastel
gradient / soft shapes behind the app, possibly per section." On a TV-dark
app (§5.8) literal pastels would wreck poster/text contrast, so the
interpretation is DEEP TINTS: each section keeps near-black value but gets
its own hue — Home blue, Discover teal, Search violet, Settings-family
slate, Connect warm — as a vertical gradient plus ONE soft radial glow.
`Modifier.ambientBackground(section)` (theme/AmbientBackground.kt) is a
drop-in replacement for `background(AppBackground)`: drawBehind only, no
animation, no recomposition — zero per-frame cost on the 32-bit boxes
(#22 house rule). Media surfaces (Details/Streams/Player) deliberately
stay flat AppBackground: their backdrop scrims blend to that exact color,
and artwork supplies the ambience there. Rejected: animated/blurred blobs
(GPU cost + visual noise), per-section wallpaper images (asset weight,
contrast risk).

**Interface sounds (focus tick + select dink).** The trigger is
MainActivity.dispatchKeyEvent key-downs, NOT real focus changes — Compose
has no global focus listener and instrumenting every focusable spreads
sound concerns everywhere. Consequence, stated honestly: an edge press
that moves nothing still ticks (native TV launchers share this tell).
Pure `UiSoundPolicy` (JVM-tested ×6) decides: d-pad → focus tick, with
held-repeat throttled to one per 90ms so fast travel purrs instead of
machine-gunning; OK/Enter → select dink, never on repeat (held-OK is a
long-press gesture); everything silent while the player owns the screen —
PlayerViewModel sets `UiSounds.suppressed` for its lifetime (a held seek
would rattle constantly over content). The two samples are ~45/200ms WAVs
generated in-repo (soft 1975Hz tick; E5→B5 two-note dink as overlapping
decaying sines — no phase-jump click), played via one app-lifetime
SoundPool at 0.20/0.35 volume, USAGE_ASSISTANCE_SONIFICATION. Settings
toggle "Interface sounds", default ON (the owner asked for the feature;
anyone can turn it off in one click). Emulator can't prove audibility —
owner's ears are the final check.

**Dual-S brand art (launcher icon + TV banner).** The owner's sketch:
two S's merged/nested like spoons, different colors, thin border between.
Built exactly that: two stacked-circle S strokes — teal behind, accent
blue in front, the "border" is a background-colored halo stroke under the
front S. Geometry generated programmatically (bezier circle-quarters),
previewed as PNG before committing. The TV launcher shows the BANNER
instead of the label, so the brand must live in the art itself: new
`appBanner` manifest placeholder (same pattern as appLabel, DECISIONS #36)
picks `tv_banner_sstreams` (mark + "SStreams" wordmark converted to vector
paths — DejaVu Sans Bold via matplotlib TextPath) when
local.properties setup.brand=SStreams, and the neutral mark-only
`tv_banner` otherwise — the public repo stays unbranded. The square
ic_launcher carries the mark alone (no text) and is shared by both brands.
Emulator-verified: the SStreams banner tile renders in the Google TV
launcher Apps row. Rejected: committing the wordmark into the default
banner (brands the open-source repo), adaptive mipmap icons (minSdk 23
still needs the legacy path anyway; the TV launcher uses the banner).

**Live TV / Events (R4) — investigated, NOT an app bug.** Direct fetches
against the owner's live addons: MediaFusion `tv/live_tv` and
`events/live_sport_events` return 200 with `metas: []` in every variant
(plain / each genre / skip); AIOStreams' same-named catalogs are wrappers
around that same empty MediaFusion source (`5bde3b0.*` ids). The
football-under-Movies complaint is also MediaFusion's own manifest: its
"Other Sports" catalog (40 items) declares `type: movie`. App-side URL
grammar, type mapping, and isUsable were all checked correct, and
Discover already shows "Nothing in this catalog" instead of a blank
grid. Resolution rides the existing plan: MediaFusion is already excluded
from generated profiles (#37) and R1 templates strip live-TV/events
catalogs from AIOStreams configs.

## 39. 2026-07-07 (session 17 cont.) — Logo v2: shadow-S lockup (alpha.22)

Owner rejected v1's side-by-side monogram: "wanted the spooned S's much
closer, almost like a single S but the first S would be the shadow… and
mix into the 'treams' part, so altogether it makes SStreams — not SS
SStreams." v2 does literally that: the teal S sits (3.5,3.5) behind the
blue S in the icon (reads as one letter with a colored shadow), and the
banner is a single centered lockup — shadow-S mark + "treams" wordmark,
baseline-aligned, tight gap — so the mark's S IS the wordmark's first
letter(s). Same generated-bezier + TextPath pipeline as #38; the neutral
repo banner keeps the mark alone. Deployed alpha.22 to the pro box only
(.231 offline).

## 40. 2026-07-07 (session 18) — English-audio preference + player prev/next episode (round 12)

Two owner round-12 asks, both built + unit-tested (build green, NOT yet
device-verified).

(a) **English audio unless a title is foreign-only.** The player can't read
a container's audio tracks before opening the stream, so — exactly like the
existing resolution/cache-marker heuristics — we read the addon's free-text
label. `StreamCascade.isNonEnglishAudio(stream)` is deliberately CONSERVATIVE:
a release counts non-English ONLY when it advertises another language
(word-boundaried language names + non-English flag emojis) AND carries no
English/dual/multi signal. Plain English rips are usually untagged, so
"no tag" stays English-friendly — we only push a stream DOWN, never demote an
unmarked English rip. Applied in three selection paths, NOT the visible list
(owner chose "auto-play + try-another only"; the on-screen list stays in
addon order, §4.1.7): `StreamCascade.rank` gets a new TOP tier (English above
even bingeGroup), `firstPlayableWhenSettled` prefers the first English-friendly
playable and waits on still-loading addons that might carry English, and
`orderedAlternatives` lists English before non-English. Every path has a
foreign-only fallback (when NO stream is English-friendly the tier is a
constant / the list is left in addon order) so a foreign film is never
stranded. Anime needs no special case — owner chose "prefer English dub", i.e.
treat anime like everything else.

(b) **Player ⏮/⏭ episode buttons.** New `NextEpisode.previousBefore` mirrors
`nextAfter`. PlayerViewModel resolves the series' episode list once (via
AutoplayGateway.resolveMeta — the same seam autoplay already uses; no screen
smuggles the videos list) and exposes prev/next `EpisodeTarget`s in UiState,
recomputed when autoplay advances an episode. The buttons open that episode's
stream list through the EXISTING `onOpenStreams` nav path (which auto-plays if
that setting is on) — so no new playback plumbing, and Back behaves like the
autoplay manual fallback. Buttons render only when a neighbour exists (movies
and the first/last episode never get a dead button).

## 41. 2026-07-07 (session 20) — Logo v3: "Streams" wordmark, real font glyphs (rebrand from SStreams)

Owner showed a cleaner target and said "change the current one": the v2
shadow-S ("SStreams") is out. v3 reads **"Streams"** — ONE bold rounded S
with a royal-blue 3D drop-shadow (a lifted-sticker look, NOT a second/teal
letter) flowing into a white "treams". This is a genuine rebrand SStreams →
Streams: `setup.brand` (owner-private local.properties) is now "Streams", so
the in-app title AND launcher label read "Streams"; the appBanner placeholder
condition in build.gradle.kts changed from `brand == "SStreams"` →
`brand == "Streams"`.

Pipeline change from #38/#39: instead of hand-authored bezier S-curves +
Compose TextPath, the glyphs are now EXACT outlines extracted from the system
font **SF Rounded** (`/System/Library/Fonts/SFNSRounded.ttf`, a variable font
instanced at wght 820 / GRAD 400) via fontTools — each glyph's TrueType
outline is quad→cubic converted, y-flipped and scaled into Android vector
`pathData`. This guarantees the S and "treams" are the same typeface/weight
and look professional. The composed paths were verified BEFORE wiring by
rasterizing them (even-odd, matches nonZero here since glyph counters wind
opposite the outline) — counters in e/a/s render as holes correctly.

Palette dropped the teal entirely: periwinkle `#C6D4F2` main S over a
royal-blue `#3B5CA6` shadow (offset ~down-right at the S scale), white
`#F3F6FC` "treams", navy `#1B2A40` tile/banner background. Three drawables:
`ic_launcher.xml` (S mark only, on a navy rounded tile), `tv_banner_streams.xml`
(full lockup, owner brand), `tv_banner.xml` (neutral repo default = S mark
only, no wordmark). Old `tv_banner_sstreams.xml` deleted. Build + unit gates
green; versionCode NOT bumped (bundles into the next deploy with round-12).
Generator script kept in the session scratchpad (not committed — depends on a
macOS system font, not a repo asset).

## 42. 2026-07-08 (session 20) — Macroblocking fix (software-decoder option) + English auto-play revert

Owner screenshot: anime playback decodes into heavy colored macroblocks on the
onn boxes; the SAME stream is clean in MX Player. This is N1 (MASTER_PLAN §10
R11) — a flaky vendor hardware decoder emitting garbage frames. Two decisions:

(a) **Decoder robustness.** ExoPlayer gets a `DefaultRenderersFactory` with
`setEnableDecoderFallback(true)` (always on — drops to the next decoder when one
ERRORS or fails init). But the owner's corruption is SILENT — the hw decoder
"succeeds" and emits garbage, so nothing throws for fallback to catch. The only
cure is to not use that decoder, so a Settings toggle **"Prefer software video
decoder"** (`PlaybackPrefs.preferSoftwareDecoder`, default OFF) swaps in
`MediaCodecSelector.PREFER_SOFTWARE` (software first, hardware still a
last-resort fallback) — the MX-parity path. Read once when `PlaybackService`
builds the engine (one tiny runBlocking DataStore read; the service is created
per watch session, not per frame). **Default OFF on purpose:** hardware is
faster and 4K HEVC/AV1 would stutter in software, so this is opt-in per box —
the owner flips it on the boxes that actually glitch. Rejected defaulting ON
(regresses the 4K box) and the media3 ffmpeg extension (NDK build cost; SW
video too slow for 4K anyway). Escalation path if a codec is still bad:
proactively surface "Play in another app". CAN ONLY be verified on the box.

(b) **English auto-play revert (undoes #40a).** Owner: "make any changes that
wouldn't make the first one to be english." The round-12 label-based English
preference demoted Japanese-tagged anime — the release they normally watch — so
auto-play waited on slower addons or jumped to a different stream. Removed the
English tier from `StreamCascade.rank`, restored `firstPlayableWhenSettled` to
first-playable-in-addon-order, and `orderedAlternatives` to pure addon order;
deleted `isNonEnglishAudio` + the language-marker regexes and their tests. The
auto-played "first stream" is once again the user's usual addon-order pick.

Bundled into alpha.23 (versionCode 23) with the logo v3 rebrand + round-12
episode nav. Build + unit gates green.

## 43. 2026-07-08 (session 21) — Software decoder DEFAULT ON + episode watch marks (progress bar + ✓)

Owner batch, three items. Two shipped (a, b); the third (intro/credits skip) was
answered as a scoping question, not built.

(a) **`preferSoftwareDecoder` default OFF → ON** (reverses #42a's default only;
the toggle + engine wiring are unchanged). Owner confirmed the software path
FIXES the anime macroblocking, so it becomes the out-of-the-box behavior across
the boxes instead of an opt-in the owner has to find. The 4K trade-off from #42
still stands — software HEVC/AV1 can stutter — so the pro box can turn it OFF in
Settings if 4K suffers. One-line default flip in `DataStorePlaybackPrefs`; a box
that already toggled it keeps its stored choice (DataStore only defaults an
absent key).

(b) **Per-episode watch marks in Details** (owner: "a progress bar and a
checkmark for watched episodes"). Non-obvious call: a finished episode used to be
**cleared** from the progress table on `PlayerEvent.Ended`, so the very episodes
that most deserve a ✓ left no trace. Changed Ended to **store the episode at
position == duration** instead of clearing (`PlayerViewModel.markWatched`); a
durationless Ended still clears (can't wedge Continue Watching). This is safe
because `isResumable`'s existing 95% upper bound already excludes a completed row
from Continue Watching and from resume — the same `WATCHED_FRACTION` line now
also powers `isWatched`, so a row shows a resume BAR (partial) XOR a ✓ (done),
never both. Rejected a new `watched` column / new table (Room migration, more
surface) — reusing the 95% line needs zero schema change. `ProgressRepository`
gained `observeProgressByExternalId()` (hot map keyed by video id) +
`isWatched()`; `DetailsViewModel` exposes it as a `WhileSubscribed` StateFlow so
backing out of the player updates the marks live (Details survives on the back
stack). `DetailsScreen` renders an accent resume bar on the thumbnail's bottom
edge (thin line under the text when there's no thumbnail) and a green ✓ badge.

(c) **Intro/credits skip — feasibility answered, NOT built (owner-decision
gated, R7/round-11 backlog).** A universal auto-skip has no free timestamp
source for general movies/TV (that data is proprietary/ML). For ANIME, the
crowd-sourced AniSkip API gives real OP/ED windows keyed by MAL id + episode —
a genuine "Skip Intro" button, but anime-only and its own mini-project
(id-mapping IMDb→MAL, a network client, a position-driven overlay, a setting,
tests). A manual "skip +N s" button is trivial and universal but isn't really an
intro/credits detector. Recommended AniSkip-for-anime as the next dedicated
piece; left the approach choice to the owner rather than half-build it under the
polish/stability mandate.

Bundled into alpha.25 (versionCode 25). assembleDebug + testDebugUnitTest green
(272 tests, 0 failures); assembleRelease (R8) clean. NOT device-verified — the
decoder default and the episode marks want owner eyes on a box (episode marks
render fine on the AVD but need seeded watch history; deferred with the deploy).

## 44. 2026-07-08 (session 21) — AniSkip anime intro/credits skip + config audit findings

Owner picked AniSkip-for-anime (declined the manual button). Also asked why his
profile still shows TV/Events and to audit the templates for CAM/TS/TC.

**AniSkip (built, alpha.26).** A one-press "Skip Intro"/"Skip Credits" button that
appears during an anime episode's OP/ED. Design decisions:
- **Data source IS the anime filter.** Windows come from api.aniskip.com keyed by
  (MAL id, episode). Non-anime and untimed anime return nothing, so there's no
  "is this anime?" heuristic to misfire — rejected genre-sniffing (Cinemeta tags
  anime as "Animation" alongside Pixar). Also means it needs no per-template
  wiring; it self-limits.
- **Id resolution is the hard part** (`AnimeMalIdResolver`). mal:/myanimelist:
  ids are used directly; kitsu: is mapped via Kitsu's public `mappings` endpoint;
  anilist: is stubbed (v1); **IMDb tt… returns null** — there's no confident 1:1
  IMDb→MAL, and skipping the wrong window is worse than no button. Every
  resolution is logged via DiagnosticsSink ("skip" tag) so a box's App log
  reveals what id format the family's anime actually carries — the only way to
  verify id-mapping without their (secret) streams on hand.
- **Never breaks playback.** Every network path (AniSkip fetch, Kitsu map) returns
  empty on any error; the feature can only ADD a button, never fault.
- **No TV focus tug.** The button is a non-focusable hint; OK is intercepted in
  the player's global `onPreviewKeyEvent` (priority over wake/pause) during the
  window and seeks to the segment end. Position is polled every 500ms in the VM
  (cheap no-op until segments load); the active window is a pure function
  (`activeSegmentAt`, start-inclusive/end-exclusive) so it's unit-tested.
- Setting `PlaybackPrefs.skipIntrosEnabled` (default ON), Settings toggle "Skip
  anime intros & credits". New package `player/skip/*`, DI in `SkipModule`.
- **NOT device-verified** and unverifiable here (AVD can't play their streams).
  Ships behind the honest caveat: if their anime is IMDb-sourced the button won't
  appear and the box log will say so → add a mapping next. 11 new unit tests.

**Config audit (owner's live AIOStreams — investigation only, no push).** Read the
gitignored exports in docs/reference/. Findings, for the owner to act on in the
AIOStreams UI (or a future gated push):
- **"TV and Events" on Adam's profile** = his AIOStreams config has `Live TV`
  (type=tv), `Live Sport Events` (type=events) and `Other Sports` (type=movie,
  the football-under-Movies from #38) catalogs ENABLED. They serve empty/garbage
  metas (MediaFusion). Fix = disable those three in AIOStreams. This is config,
  not app (the app faithfully renders enabled catalogs — #38 rejected app-side
  curation).
- **CAM/TS/TC:** Adam's `excludedQualities` = [CAM, TS, SCR] — **missing TC**
  (telecine). Rachael's (the session-19 family-no-anime template) = [CAM, SCR,
  TS, TC] — complete. Recommend adding TC to Adam's. No "in theaters/upcoming"
  catalog is enabled, so the cinema-junk risk is purely the TC quality gap.
- **Rachael confirmed clean:** Cinemeta + AIOMetadata + AIOStreams (wrapping
  Torrentio/Comet/MediaFusion) + AIOLists; all four cam qualities excluded; no
  live catalogs — she's the model the owner's profile should match.

alpha.26 (versionCode 26). Gates green: assembleDebug + testDebugUnitTest (283
tests, 0 fail) + assembleRelease (R8) clean.

## 45. 2026-07-08 (session 21) — Decoder default OFF + "Having trouble?" panel + resume-to-episode + TC push

Owner batch. App changes (alpha.28) + a live config push + two big features specced
for next.

(a) **Software decoder default OFF again** (reverses #43a). Owner: the only issue
with software-ON was a brief self-healing start-up stutter, so hardware is the
default and software becomes a one-press in-player fix (below) instead of the
out-of-the-box behavior. One-line default flip + the SettingsViewModel initial
value.

(b) **"Having trouble?" player panel.** The three fix-it escapes (Try a different
stream · Play in another app · **Fix blocky video**) are now grouped in a
captioned accent ring in the control bar, with a **Learn more** button opening a
plain-language help dialog (owner's own design sketch). **Fix blocky video** is
the on-demand software-decoder switch: the decoder is chosen when the engine is
built and can't be flipped live, so it persists `preferSoftwareDecoder=true`
(awaited, no race) then re-opens the current video's stream list — the fresh
playback session builds a software-decoding engine. Reuses the tested
open-stream-list path rather than a risky live engine rebuild (collectors are
bound to the original engine; rebuilding mid-VM would need re-wiring them).

(c) **Resume-to-last-episode.** Opening a series with watch history lands on the
episode you stopped on: `DetailsViewModel.resumeTarget` finds the most recently
watched episode of this series (max updatedAt), or — if that one's finished — the
next episode (continue, not re-watch), sets `selectedSeason` to its season, and
exposes `resumeVideoId`. `DetailsScreen` scrolls that row into existence THEN
focuses it (a LazyColumn hasn't composed off-screen rows, so focus-first would
throw). Computed once at open from a progress snapshot so browsing seasons later
doesn't yank the selection. Also: movie Play button now reads "Resume"/"Play
again" with a progress bar (video id == meta id).

(d) **TC pushed to Adam's primary AIOStreams — LIVE, verified.** Surgical
pull→edit→PUT via the instance API (scratchpad script, backed up first). Adding TC
was blocked by a **deprecated `usa-tv` preset** (AIOStreams rejects any save that
still references it — `USER_INVALID_CONFIG`), so that disabled preset was removed
too. Result verified: `excludedQualities = [CAM, TS, SCR, TC]`, no usa-tv. Of his
3 instances: **primary (fortheweak.cloud) fixed; nightly (elfhosted) already had
TC; backup (weebs/midnightignite) has a stale stored password (`Invalid UUID or
password`) — untouched, needs its password fixed or a re-create (new manifest URL
→ re-add on box).** Did NOT disable his Live TV/Events/Other Sports catalogs
(separate change, stayed surgical to the TC ask) — offered as a follow-up.

(e) **Trakt scrobbling — SPECCED, not built (owner asked "can we build that").**
Plan: Trakt **device OAuth** (the family types a short code at trakt.tv/activate —
no per-box browser/redirect; the "Claude" Trakt app creds already exist in the
passport, DECISIONS session-19), token in DataStore; a `TraktScrobbler` that maps
the playing item to a Trakt id (IMDb tt… is native to Trakt — easy, unlike
AniSkip's MAL problem) and POSTs scrobble **start** (on play), **pause** (on
pause/background), **stop** (on ended / ≥80% — Trakt marks watched), driven off
the same player events/position we already have. A Settings "Connect Trakt"
screen. This is app-native scrobbling (Stremio does it in-app too). Its own build;
see MASTER_PLAN §10.

(f) **Rich multi-instance profile builder — SPECCED, not built (StremioSurfer
tooling, not the app).** Owner fixed the instance topology: **2 AIOMetadata**
(1. aiometadata.elfhosted.com, 2. aiometadatafortheweak.nhyira.dev — new) and **3
AIOStreams** (1. aiostreams.fortheweak.cloud, 2. aiostreamsfortheweebs.midnightignite.me,
3. aiostreams.elfhosted.com), in that order. Rule: everyone should end up with all
5 instances configured with the recommended addons (Comet, MediaFusion, StremThru,
etc.); create whatever they're missing (have 2 AIOStreams → create the 3rd + both
AIOMetadata). Real base URLs live only in the gitignored passport/users.json, never
here. Build target: extend the passport tooling to provision missing instances per
person with a recommended-addon preset per instance. See MASTER_PLAN §10.

alpha.28 (versionCode 28). Gates green: assembleDebug + testDebugUnitTest (283
tests, 0 fail) + assembleRelease (R8) clean. App bits NOT device-verified (resume
focus/scroll + the panel want owner eyes on a box).

## 46. 2026-07-08 (session 21) — SW-decoder toggle shows ON/OFF + English-audio-first sort + Rachael provisioning root-caused

(a) **"Software video" is now a stateful toggle** (alpha.29). Owner: the "Fix
blocky video" button didn't show state. It now reads "Software video: ON/OFF",
tints when ON (`SurfacePill selected`), and flips both ways. PlayerViewModel reads
`preferSoftwareDecoder` at session start into `UiState.softwareDecoderOn`;
`toggleSoftwareDecoder()` flips the persisted value and reloads via the stream
list (decoder is fixed at engine build). Learn-more copy updated to match.

(b) **English audio first on Adam's primary AIOStreams (LIVE, verified).** Root
cause of "not playing English dub first": the `language` sort key existed but sat
**7th** in `sortCriteria.cached`/`.uncached` (after seadex→resolution→quality→…),
so a high-res Japanese/untagged stream always outranked an English one. Fix: moved
`language` to the **front** of both arrays (surgical pull→edit→PUT, backed up).
`preferredLanguages` was already `[English, Original, Unknown]`, so English now
sorts first, then quality within English. Verified `cached[0]=language`. Owner to
confirm on the box; if still off, revisit `requiredLanguages=[English]` (untagged
anime appears to pass it).

(c) **Rachael multi-instance provisioning — ROOT-CAUSED, not yet applied.** Owner:
her account shows only ~4 addons and a created instance had "~3 addons". **Cause
found:** `templates/primary.json` (the family-no-anime template) has **0 presets**
— it's empty, so any provisioning from it makes a thin instance. The tooling is
otherwise ready (users.json `instances` map has all 3 AIOStreams base URLs; her
keys — RD/TMDB/Torbox/mdblist/rpdb/tvdb — are set; she has AIOStreams primary +
1 AIOMetadata, needs +2 streams +1 meta). **Correct fix (the real build):** build
a proper recommended-addons template from Adam's LIVE rich config (21 presets incl
Comet, MediaFusion, StremThru, Torrentio, Debridio), strip anime (seadex/neko-bt)
for family + strip ALL of Adam's keys (leak-safety — push_aiostreams substitutes
per-user keys and validates against the owner to catch leaks), then POST-create her
backup+nightly AIOStreams from it. **AIOMetadata has NO provisioning tooling** (no
push_aiometadata; make_profiles doesn't touch it) — creating her 2nd AIOMetadata
(+beefing the thin 1st) needs new tooling / the AIOMetadata API reverse-engineered.
Did NOT blind-apply: provisioning from the empty template is exactly what produced
the thin instance, and leaking Adam's keys into her instances is a real risk to do
carefully, not rushed.

alpha.29 (versionCode 29). Gates green: assembleDebug + testDebugUnitTest (283
tests, 0 fail) + assembleRelease (R8) clean. (b) is live; (c) is diagnosis + plan.

## 47. 2026-07-08 (session 21) — In-player resume prompt over a looping loading animation (alpha.30)

Owner: reopening a partly-watched thing should return to the same spot **no
matter what stream/link**, and the "resume or start over?" question should be
asked **while the video is being tested** (buffering + the static
Real-Debrid/Torbox/Debridio "resolving" clips), with a **looping loading
animation** on screen.

(a) **Resume is already stream-agnostic — confirmed, no code.** Watch progress
is keyed by `MediaRef` (video id for episodes, meta id for movies), never by the
stream URL (§8.4), so any stream of the same item resumes at the same position.
This is what the owner asked for in the first clause; nothing to build.

(b) **`LoadingAnimation` component** (`ui/components/LoadingAnimation.kt`) — a
spinning accent arc (spins via `graphicsLayer { rotationZ }`, layer-phase only,
no per-frame recomposition — DECISIONS #22 discipline so the 32-bit onn boxes
stay smooth; degrades to a static ring if a box suppresses infinite animations)
plus a "Getting your show ready…" line. Replaces the black screen the player
showed while buffering.

(c) **Resume question moved INTO the internal player, over the loader.** The
player now shows the spinner during its load/test phase (driven by
`playbackState != STATE_READY`, seeded from the CURRENT engine state to dodge a
listener-attaches-after-play() race that would otherwise wedge the spinner). If
there is saved progress, a **"Resume from X / Start from the beginning"** prompt
sits over the spinner and playback is **held paused** (owner's choice — no
surprise audio; the stream still buffers so a bad link fails fast). Resume holds
initial focus (one OK to continue). `PlayerViewModel.resumePromptMs` is set from
`source.startPositionMs > 0` at init; `resumeFromSaved()` lets it go,
`startFromBeginning()` seeks to 0 first. An error-driven auto-skip during the
prompt re-holds the replacement paused so the prompt still governs.

(d) **Stream list defers to the player for the internal path.** The old blocking
pre-launch `ResumeDialog` is skipped for the internal player (it launches at the
saved position and the player asks). It's **kept for external players** (VLC/MX)
— we can't overlay another app, so those still ask before leaving.

alpha.30 (versionCode 30). Gates green: assembleDebug + testDebugUnitTest (283
tests, 0 fail) + assembleRelease (R8) clean. NOT device-verified — the AVD can't
play the family's real streams; owner to eyeball on a box (the Compose overlays
draw above the video surface, so a screencap during loading should show them).

## 48. 2026-07-10 (session 22) — Back lands on the tile you opened; season chips stop drifting (alpha.39)

Round 13 items #4 and #5. Both are focus-restoration bugs, and both were
**reproduced on the emulator against alpha.38 and re-verified fixed against
alpha.39** — the first emulator-proven before/after in this project's focus work.

### #4 Home returned to the TOP on Back

Two effects re-fired whenever Home came back off the nav back stack. Coming back
from Details, the HomeViewModel is retained (it is scoped to the HOME
NavBackStackEntry), so the rows are already loaded on Home's very first
composition:

- `LaunchedEffect(featured != null) { listState.scrollToItem(0) }` — the key is
  already `true`, so the "snap to the hero the moment it appears" effect ran again
  and re-snapped Home to item 0.
- `LaunchedEffect(showingRows) { headerFocus.requestFocus() }` — focusing the
  header pill dragged the restored scroll up behind it, because the header IS list
  item 0 (that placement is itself the DECISIONS #33 hold-UP fix, so it stays).

Measured on alpha.38: open a tile 4 rows down, press BACK → Home is scrolled to
the very top with focus on the NavRail.

Fix: latch the hero snap behind a `rememberSaveable` flag, and let the rows branch
own its entry focus instead of the shared effect. Navigating away disposes the
screen, so the focused node — and every `focusRestorer`'s memory with it — is gone
by the time we return; remembering the *card* is therefore not enough. Home now
remembers WHICH tile was opened (`rememberSaveable`, so it outlives the back
stack), and on return scrolls the column to that row, scrolls the row to that
card, and focuses it. Because both lazy lists compose late, the focus request is
probed across a few frames (`withFrameNanos`) and falls back to the header when
the row is gone (addon disabled, row hidden, process death).

The target is deliberately **never cleared**: the last tile you opened stays the
anchor for every later return to Home. Clearing it would send the next return to
the header, which — per the second bullet above — scrolls Home back to the top.

`homeRestoreIndex()` is pure and unit-tested: the hero and Continue Watching rows
are conditional, so a catalog row's LazyColumn index shifts under it.

### #5 The season selector jumped 1 → 3 → 5 → 7

Coming back UP from an episode row left the chip to Compose's geometric focus
search. Episode rows span the full width, so the search picks whichever chip sits
nearest the row's centre — never the one you left, and further right on each trip.
Measured on alpha.38 (Dark Side of the Ring, 7 seasons): chip x = 703 → 930 → 930.

Fix: `Modifier.focusRestorer(selectedChipFocus)` on the season LazyRow pins
re-entry to the chip you left, falling back to the SELECTED season — the one whose
episodes are actually on screen. The selected chip is now scrolled into view at
entry, because a resume can land on season 5 whose chip would otherwise never be
composed, and an unattached FocusRequester is one the restorer cannot focus.

Measured on alpha.39, same series: chip x = 112, stable across 4 round trips.

Also moved the touched call sites onto the non-deprecated `focusRestorer(FocusRequester)`
overload, which is stable — dropping three `ExperimentalComposeUiApi` opt-ins.
`DiscoverScreen`/`SearchScreen` still use the deprecated lambda form; migrate when
next touched.

alpha.39 (versionCode 39). Gates green: assembleDebug + testDebugUnitTest. The
`HomeViewModelTest` Main-dispatcher flake hit once and cleared on rerun (known).

## 49. 2026-07-10 (session 23) — The player picks the decoder per stream; scrubbing previews then commits (alpha.40)

Owner: "the main player is kinda trash — rainbow artifacts on some streams; I
want a premium, responsive but fluid feel." The rainbow artifacts are the known
silent hardware-decoder macroblocking (R11 N1). Design note:
docs/superpowers/specs/2026-07-10-player-quality-design.md.

### Automatic software decoding (no viewer toggle needed)

The two halves built in alpha.28/.33 — a manual software-decoder toggle and
label-based codec detection — are now wired together and made automatic:

- `VideoCodec` moved from `StreamCascade` into `domain` (with
  `hardwareDecodable(hw)`), so `PlayableSource` carries the stream's codec
  without domain importing autoplay. `StreamMapping` stamps it at conversion.
- `ExoPlayerEngine` now takes the box's `DecoderCapabilities` and decides PER
  `play()`: session override (the in-player toggle) > box-level Settings pref >
  automatic — software whenever the label says the codec is one the box's
  hardware provably can't decode (HEVC 10-bit on the onn boxes being THE case).
- The mechanism: `DefaultRenderersFactory.setMediaCodecSelector` gets a
  DELEGATING selector (PREFER_SOFTWARE vs DEFAULT off a var). The selector is
  consulted at every codec init, so the choice flips per stream with no engine
  rebuild. `play()` calls `stop()` first when not idle — codec REUSE across
  `setMediaItem` could otherwise carry a garbage decoder into the next stream.
- **Decode-error safety net:** decoder-class error codes (pure
  `isDecodeErrorCode`, table-tested) get ONE same-stream retry in software at
  the same position, before the try-another-stream walk. Catches boxes that
  CLAIM a profile and then fail loudly; the silent-garbage case is covered by
  the label heuristic, and the toggle remains the last resort.
- The "Software video" toggle now applies IN PLACE — session override + replay
  at the current position — instead of persisting a pref and bouncing through
  the stream list for a fresh engine. It still persists the box-level pref
  (semantics preserved), and its ON/OFF now mirrors `usingSoftwareDecoder`,
  the engine's per-stream truth: auto-engaged software honestly reads ON.

### Fluid controls

- **Scrubbing** (Scrubbing.kt, pure + table-tested): LEFT/RIGHT move a preview
  target instantly; the REAL seek commits after 350 ms of quiet — one rebuffer
  per gesture instead of per press (each seek rebuffers; that per-press grind
  is why held scrubbing felt like a slideshow). Steps accelerate with the press
  streak (10s→30s→60s→120s, window 600 ms — a held remote's key-repeat reaches
  the big steps in about a second). A "+2:30" delta chip shows during the
  gesture; OK mid-scrub commits immediately. `SeekParameters.CLOSEST_SYNC`
  lands seeks on keyframes instead of decoding forward to the exact frame.
- **Control bar animates** in/out (fade+slide, 150–220 ms). Boxes with
  animator scale 0 degrade to today's instant pop (same reason the R13-8
  spinner had to move to the frame clock). Because the bar now composes a
  frame late, the scrub-bar focus request probes frames like the #48 restore.
- **Paused keeps the bar** — auto-hide only runs while playing (a hidden bar
  over a paused frame gave no clue the video was paused).
- **Mid-playback rebuffer ring**: a committed seek or network stall that
  persists >400 ms shows a small centred ring — NO scrim, no focus change (the
  alpha.34 lesson: a blocking scrim swallowed keys and killed held scrubbing).

Box-only verification remains: whether auto-software truly kills the rainbow
artifacts is per-box codec truth (same as alpha.33). Emulator smoke passed:
install, MainActivity resumed, PlaybackService started directly and built the
engine + MediaSession with the injected DecoderCapabilities, no crash. Playback
was deliberately NOT exercised on the emulator — starting a stream fires the
alpha.35 Trakt check-in ping on the owner's account.

alpha.40 (versionCode 40). Gates green: assembleDebug + testDebugUnitTest
(4 new test classes: VideoCodecTest, ScrubbingTest, DecodeErrorTest, and a
codec-stamp case in StreamMappingTest).
