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
