# Master Plan: Open-Source Stremio Replica for Android TV

**Audience:** Claude Code (Claude Fable 5), operating across multiple accounts/sessions on one machine.
**Owner:** Adam (adamsavoyull@gmail.com)
**Status:** Pre-Phase-0. Nothing built yet. Read this file top to bottom before doing anything.

---

## 0. Prime Directives

1. **The repo is the only memory.** Sessions will be killed by hourly limits and resumed under a different Claude account. Anything not written to disk and committed is lost. See §2 (Session Continuity Protocol) — it overrides everything else.
2. **KISS / YAGNI / SOLID.** The maintainer is a motivated layman. Every file must be understandable by a new developer. Prefer boring, well-documented code over clever code. Comment the *why*, not just the *what*. Before adding complexity, write the justification in `docs/DECISIONS.md`.
3. **Better-running than Stremio, not feature-parity with Stremio.** Priorities in order: (a) playback that never crashes or stalls silently, (b) flawless series autoplay, (c) a fast, dense, customizable 10-foot UI, (d) full addon-protocol compatibility.
4. **Never block future Live TV.** HDHomeRun / TVHeadend channel support comes later. Do not build it now, but every interface decision must pass the test in §8.
5. **Open source from day one.** Public GitHub repo, buildable by anyone with Android Studio, no proprietary blobs.

---

## 1. Ground Truth: Existing Public Code (verified 2026-07-04)

Use these as references. Read code before reinventing; do not copy GPL code into this repo without matching the license (§11).

| Resource | URL | Use for |
|---|---|---|
| stremio-core (Rust, MIT) | https://github.com/Stremio/stremio-core | Reference for state/model semantics, addon transport behavior |
| stremio-core-kotlin | https://github.com/Stremio/stremio-core-kotlin | Reference only (see §3 decision). Kotlin bindings to the Rust core |
| stremio-addon-sdk protocol docs | https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md | **Authoritative addon protocol spec.** Manifest, catalog/meta/stream/subtitle resources, behaviorHints |
| stremio-web (GPL) | https://github.com/Stremio/stremio-web | UI/UX reference for screens and flows. GPL — reference, don't copy unless we adopt GPL |
| NuvioTV (official) | https://github.com/NuvioMedia/NuvioTV | Closest analog: Kotlin, TV-first Stremio-addon client. Study its player + focus handling |
| NuvioMobile | https://github.com/NuvioMedia/NuvioMobile | Kotlin Multiplatform + Compose Stremio-addon client |
| Nuvio TV (React Native lineage) | https://github.com/tapframe/NuvioTV | Autoplay/UX ideas; RN stack, not our stack |
| Lumera | https://github.com/LumeraD3v/Lumera | Compose-for-TV Stremio-addon app; UI patterns |
| AIOStreams | https://github.com/Viren070/AIOStreams | Primary stream-aggregator addon to support first-class |
| AIOMetadata | https://github.com/cedya77/aiometadata | Primary catalog/metadata addon to support first-class |
| VLC Android intents | https://wiki.videolan.org/Android_Player_Intents/ | External player launch spec |
| Compose for TV releases | https://developer.android.com/jetpack/androidx/releases/tv | Current TV library status |
| TV lazy layout migration | https://developer.android.com/training/tv/playback/compose/lists | Confirms standard `LazyRow`/`LazyColumn` are the recommended TV lists |

Also in this repo's `docs/reference/`: place the Gemini research doc the owner supplied (`Stremio Android TV Clone Plan.md`). Treat it as background reading, not authority; where it conflicts with this file (e.g., it recommends `TvLazyRow`, which is deprecated — see migration link above), **this file wins**.

---

## 2. Session Continuity Protocol (multi-account handoff)

The owner rotates between 3–4 Claude Pro accounts on the same machine as hourly limits hit. A fresh session under a new account has zero chat memory. The following makes handoff automatic.

### 2.1 Files that constitute project memory

| File | Purpose | Update frequency |
|---|---|---|
| `CLAUDE.md` (repo root) | Standing instructions auto-read by Claude Code at session start | Rarely; only when process changes |
| `docs/STATE.md` | Living status: current phase, current task, exact next action, known blockers, branch name | **Every stopping point** |
| `docs/DECISIONS.md` | Append-only log: decision, date, rationale, alternatives rejected | Whenever a non-obvious choice is made |
| `docs/TESTLOG.md` | What was tested, on what device/emulator, result | After every test run |
| This file (`docs/MASTER_PLAN.md`) | The spec. Mark phase checkboxes done; do not rewrite history | At phase boundaries |
| Git history | Ground truth of code changes | Continuously |

### 2.2 Rules

1. **Session start (every session, no exceptions):** read `CLAUDE.md` → `docs/STATE.md` → `git log --oneline -15` → `git status`. Then continue from the "NEXT ACTION" line in STATE.md. Do not re-plan from scratch; do not redo completed work.
2. **Commit small and often.** Every logically complete change gets a conventional commit (`feat:`, `fix:`, `docs:`, `test:`, `chore:`). Never leave >30 minutes of work uncommitted. Uncommitted work is presumed lost.
3. **Checkpoint before dying.** Whenever a limit warning appears, the session feels long, or a large task is about to start: stop, update `docs/STATE.md` (see template), commit with `chore(state): checkpoint`, push. The STATE.md "NEXT ACTION" must be executable by a stranger with no context.
4. **No memory outside the repo.** Never rely on chat history, Claude memory features, or `~/.claude` state — those do not transfer between accounts. If it matters, it goes in a tracked file.
5. **One branch at a time.** Work on `main` (or a single feature branch recorded in STATE.md). Never leave two half-finished branches.
6. **Push to GitHub after every commit** once the remote exists. The remote is the shared brain across accounts and a backup.

### 2.3 STATE.md template (create in Phase 0)

```markdown
# STATE — updated <ISO datetime> by session <n>

## Phase
Phase 2 — Details, streams, internal player

## Branch
main @ <short-sha>

## Just finished
- StreamRepository parallel fan-out with 15s per-addon timeout (committed abc1234)

## In progress (uncommitted: NO)
- none

## NEXT ACTION (start here)
1. Implement StreamListScreen sorting by AIOStreams-provided order (no re-sort).
2. File: app/src/main/java/.../ui/streams/StreamListScreen.kt
3. Acceptance: streams render in addon order; focus lands on first stream.

## Blockers / open questions
- none

## Environment notes
- Emulator: Android TV (1080p) API 34 image, AVD name "tv34"
```

### 2.4 CLAUDE.md content (create in Phase 0, verbatim skeleton)

```markdown
# CLAUDE.md
This is an open-source Stremio-replica app for Android TV. Full spec: docs/MASTER_PLAN.md.

MANDATORY session-start sequence:
1. Read docs/STATE.md
2. Run: git log --oneline -15 && git status
3. Resume from STATE.md "NEXT ACTION". Do not re-plan completed work.

MANDATORY before ending work or when a usage-limit warning appears:
1. Commit all work (conventional commits), push.
2. Update docs/STATE.md (template inside) with an executable NEXT ACTION.
3. Commit: chore(state): checkpoint. Push.

Rules:
- KISS/YAGNI/SOLID. Code must be readable by a new developer; comment the why.
- Never break the Live-TV compatibility constraints in MASTER_PLAN.md §8.
- Record non-obvious choices in docs/DECISIONS.md.
- Build check before checkpoint: ./gradlew assembleDebug && ./gradlew testDebugUnitTest
```

---

## 3. Architecture Decision: Pure Kotlin, no Rust JNI (v1)

**Decision:** Implement the Stremio addon protocol natively in Kotlin. Do **not** integrate `stremio-core` via JNI in v1.

**Rationale (record in DECISIONS.md at Phase 0):**
- The addon protocol is plain REST + JSON (manifest/catalog/meta/stream/subtitles). A Kotlin client is a few hundred lines. JNI + Rust toolchain (cargo-ndk, ABI matrix, memory-ownership hazards) multiplies build complexity and locks out layman contributors — direct violation of Prime Directive 2.
- We do not need Stremio account sync, its P2P engine, or its Ctx state machine for v1.
- Escape hatch: all addon access goes through a single `AddonClient` interface. If the project later needs stremio-core parity, `stremio-core-kotlin` can be swapped in behind that interface without touching UI (https://github.com/Stremio/stremio-core-kotlin).

### 3.1 Stack

| Layer | Choice | Notes |
|---|---|---|
| Language | Kotlin (latest stable) | |
| UI | Jetpack Compose + `androidx.tv:tv-material` | Use **standard** `LazyRow`/`LazyColumn`/`LazyVerticalGrid` (Foundation ≥1.7 has TV focus support). `TvLazyRow`/`TvLazyColumn` are deprecated/removed — do not use. Source: https://developer.android.com/training/tv/playback/compose/lists |
| Playback | Jetpack Media3 ExoPlayer (`androidx.media3`) | Internal default player. §6 |
| Images | Coil | Async loading, memory/disk cache |
| Networking | OkHttp + Retrofit (or Ktor client — pick one, record in DECISIONS.md) + kotlinx.serialization | Lenient JSON parsing is mandatory (addons return sloppy JSON) |
| Persistence | Room (library, watch progress, channels-ready schema) + DataStore (settings) | |
| DI | Hilt | Standard, well-documented |
| Min SDK | 21–23 range; target latest | Owner's fleet includes low-end onn boxes; verify min SDK against Media3 requirements at Phase 0 |

### 3.2 Structure (single Gradle module, strict packages)

Start with one `:app` module. Split into Gradle modules only if build times or contributor friction demand it (YAGNI). Enforce boundaries by package + interfaces:

```
app/src/main/java/<pkg>/
  addon/        // AddonClient, Manifest, protocol DTOs, AddonRepository
  data/         // Room DB, DataStore, repositories (LibraryRepository, ProgressRepository)
  domain/       // Plain models: MediaItem, Stream, Episode, PlayableSource, ContentType
  player/       // PlayerEngine interface, ExoPlayerEngine, ExternalPlayerLauncher, AutoplayController
  ui/           // Compose screens: home, discover, search, details, streams, player, settings, profiles
  di/           // Hilt modules
```

Key interfaces (these are the Live-TV insurance policy, §8):

```kotlin
// Everything playable resolves to this, whether VOD stream or (later) a live channel.
data class PlayableSource(
    val url: String,                // http(s) URL Media3 or an external player can open
    val title: String,
    val mimeTypeHint: String?,      // e.g. "video/mp2t" for future MPEG-TS
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val bingeGroup: String? = null, // from stream behaviorHints; used by autoplay (§7)
    val startPositionMs: Long = 0,
)

interface MediaSourceProvider {           // v1: AddonStreamProvider. Later: HdhrProvider, TvheadendProvider
    suspend fun sourcesFor(item: MediaRef): List<PlayableSource>
}

interface PlayerEngine {                  // v1: ExoPlayerEngine. External players go through ExternalPlayerLauncher
    fun play(source: PlayableSource)
    val events: Flow<PlayerEvent>         // ended, error, position — AutoplayController consumes this
}
```

---

## 4. Addon Protocol Implementation (the "manifest system")

Authoritative spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md — implement against it, not against memory.

### 4.1 Required behavior

1. **Install by manifest URL.** User pastes/enters a URL ending in `manifest.json` (also accept `stremio://` links by rewriting to `https://`). Fetch, validate, persist the manifest. Show name, description, types, resources, catalogs before confirming install.
2. **Resources:** support `catalog`, `meta`, `stream`, `subtitles`. Parse and store — but do not yet build UI for — types `channel` and `tv` (§8).
3. **Endpoints:** `/{resource}/{type}/{id}.json` plus `extra` path segments for `search`, `genre`, `skip` (pagination). Respect each manifest's declared `catalogs`, `resources`, `types`, `idPrefixes` — never query an addon for something it doesn't declare.
4. **Stream objects:** handle `url` streams fully. Streams carrying only `infoHash` (raw torrents) are out of scope for v1 — hide them behind a "unsupported source" note (DECISIONS.md entry; revisit later). Parse `behaviorHints` including `bingeGroup`, `notWebReady`, `proxyHeaders`.
5. **Fan-out:** query all installed stream addons in parallel; per-addon timeout ~15 s; render results incrementally as each addon responds (never wait for the slowest addon before showing anything).
6. **Metadata fallback:** if a stream-only addon matches an item with no meta, resolve meta from the user's catalog addons (AIOMetadata) or Cinemeta (`https://v3-cinemeta.strem.io/manifest.json`) by IMDb id.
7. **Ordering:** preserve the order streams arrive in from each addon (AIOStreams pre-sorts; re-sorting client-side destroys the user's configured sort). Group by addon, addon order = user's install order (reorderable in settings).
8. **Robustness:** malformed manifest or response JSON must produce a logged, user-visible "addon X failed" chip — never a crash, never a silent empty screen.

### 4.2 First-class targets (not hardcoded)

The owner runs 2–3 **AIOStreams** instances (https://github.com/Viren070/AIOStreams) + 1 **AIOMetadata** instance (https://github.com/cedya77/aiometadata). Design consequences:

- Multiple instances of the *same* addon id must coexist (key addons by manifest URL, not by manifest id).
- Catalog management UI: enable/disable, rename, reorder catalogs across all addons (AIOMetadata exposes many catalogs; the home screen must not drown).
- Nothing AIOStreams/AIOMetadata-specific in code paths — they are just protocol-compliant addons. Any community addon must work.

---

## 5. UI / Design Specification (10-foot)

Design pillars: density, ratio-correct art, total focus predictability, user control.

1. **Density is user-configurable.** Stremio's sparse grid (~5 posters visible) is a core complaint. Default Discover grid: **6 poster columns at 1080p (2:3 posters)**, settings offer 4–8 columns and "compact rows" toggles. All card sizes derive from one `CardSizeTokens` object — one place to tune ratios.
2. **Aspect ratios are contract, not decoration.** Poster 2:3, backdrop/thumbnail 16:9, square 1:1 — chosen per catalog content, never stretched. Coil `crossfade`, placeholder shimmer sized to final dimensions so rows never reflow.
3. **Overscan safety:** 48dp horizontal / 27dp vertical safe margins on every screen.
4. **Focus:** visible focus scale (1.05–1.1x) + border on every focusable; `FocusRequester` chains defined explicitly on details screen and player overlay; focus must never be lost (D-pad input with nothing focused = bug, test-covered in §9). Back always goes exactly one level up.
5. **Typography/contrast:** minimum 12sp equivalent at TV viewing distance per Android TV design guidance; test on a real TV, not just emulator.
6. **Home screen = user-composed rows:** Continue Watching (always first unless disabled), then user-ordered catalog rows. Row add/remove/reorder in settings.
7. **Performance floor:** smooth D-pad scrolling on an onn 1080p box (owner's family hardware). No image decoding on main thread, stable `key`s in all lazy lists, no recomposition storms (verify with Layout Inspector / composition counts).
8. **Personal-taste features deferred (YAGNI):** themes beyond dark, profile avatars, PIN locks. Dark theme only in v1; TV apps are dark for a reason.

---

## 6. Playback Engine

### 6.1 Internal player (default): Media3 ExoPlayer

- `ExoPlayer` hosted in a `SurfaceView`-backed `PlayerView` inside `AndroidView`; custom Compose overlay for all controls (play/pause, seek, audio track, subtitle track + offset, next-episode).
- `MediaSessionService` + `MediaSession` so remote hardware media keys, Google Assistant, and OS integration work, and playback survives UI churn.
- Buffering: generous `DefaultLoadControl` targets (long-form streaming over variable networks); surface a visible buffering indicator with kbps readout in a debug overlay toggle.
- Tunneled playback: **off by default**, user toggle in advanced settings (inconsistent across OEM chipsets).
- Track selection: prefer hardware decoders, permit software fallback. Consider the Media3 FFmpeg decoder extension **only if** codec gaps show up in testing (DECISIONS.md first — it complicates the build).
- Errors: every `PlaybackException` maps to a plain-language message + one-keypress actions: "Try next stream" / "Open in external player" / "Back". No dead-end error states.

### 6.2 External players

Support order: **internal ExoPlayer (default) → VLC → MX Player (incl. MX Player Pro) → generic Android chooser**. Setting: "Always use: [Internal | VLC | MX Player | Ask]".

- **VLC:** `ACTION_VIEW` to package `org.videolan.vlc`, extras `title`, `position` (ms), `from_start`, `subtitles_location`; launch with `startActivityForResult` and read back the result's position extra to store resume progress. Spec: https://wiki.videolan.org/Android_Player_Intents/
- **MX Player:** `ACTION_VIEW` to `com.mxtech.videoplayer.ad` / `.pro`, extras `title`, `position`, `subs`/`subs.name` arrays, `return_result` for resume position. (MX and VLC intent APIs differ and are not cross-compatible — implement two code paths behind one `ExternalPlayerLauncher` interface.)
- **Generic:** plain `ACTION_VIEW` with `video/*` for anything else the user has installed.
- Detect installed players at settings-screen load (`PackageManager`), only show what exists.
- Known Stremio failure to avoid: external-player handoff freezing the launching app. Our app must fully release its own player resources before launching the external intent, and must remain responsive on return.

---

## 7. Autoplay (series binge) — flagship feature, get this exactly right

Problem being fixed: official Stremio's Android TV autoplay is unreliable and gives up too fast (see e.g. https://github.com/Stremio/stremio-bugs/issues/2020).

### 7.1 Spec

1. **Trigger:** on `PlayerEvent.Ended` (or user presses "Next episode" in overlay, available from 95% position or last 30 s), `AutoplayController` resolves the next episode from the series meta (season/episode ordering from the meta `videos` array).
2. **Countdown UI:** "Up next: S02E05 — <title>" card with live countdown. Default 10 s before auto-advance begins *resolving*; user can hit OK to start immediately.
3. **Stream selection cascade for the next episode:**
   1. Query stream addons for the next episode id.
   2. **Tier 1 — bingeGroup match:** pick the stream whose `behaviorHints.bingeGroup` equals the current stream's bingeGroup (this is the protocol's purpose-built mechanism — same addon, same quality/rip group).
   3. **Tier 2 — similarity guess:** no bingeGroup match → score candidates: same addon > same resolution > same filename pattern (episode-number-normalized token similarity) > same debrid/cache flag. Take the top score.
   4. **Tier 3 — first stream:** take the first stream returned by the highest-priority addon.
4. **Patience rule (non-negotiable):** once resolution starts, autoplay is **never cancelled by a timer shorter than 60 seconds**. Slow addon fan-out shows "Finding next episode… (addon 2/3 responded)" progress — visible waiting, not silent death. Cancel only on: (a) user presses Back, or (b) 60 s elapsed with *zero* playable streams from *all* addons — and even then land on the manual stream list for the next episode, never a dead screen.
5. **If the chosen stream fails to open:** automatically fall through to the next candidate in the scored list (up to 3 attempts) before showing the manual list. Log every fallback to `TESTLOG.md`-visible debug log.
6. **External players:** autoplay with external players is best-effort — on return from VLC/MX with a near-complete position, show the Up Next card in our app. Document this limitation in README.
7. **Settings:** countdown length (5/10/15/30 s/off), autoplay on/off.

### 7.2 Acceptance test (Phase 3 gate)

Play 3 consecutive episodes of a test series via an AIOStreams instance with zero remote presses; then repeat with the addon artificially delayed 20 s per response (mock server) — autoplay must still succeed.

---

## 8. Live TV Future-Proofing (constraints only — DO NOT BUILD NOW)

Future feature: user's HDHomeRun tuner (direct HTTP: `http://<ip>:5004/auto/v<ch>` MPEG-TS, lineup at `http://<ip>/lineup.json` — see https://info.hdhomerun.com/info/http_api) and/or TVHeadend server links (HTTP streaming profile URLs and/or HTSP). Verify these APIs when the feature is actually built; do not implement from this paragraph.

Binding constraints on v1 code:

1. `ContentType` enum includes `MOVIE, SERIES, CHANNEL, TV, OTHER` from day one; parsers must not drop `channel`/`tv` items (store, even if no UI renders them yet).
2. All playback flows accept `PlayableSource` (§3.2) — nothing may assume "stream came from an addon" (e.g., don't require an addon id to play; don't require an IMDb id for progress tracking — key progress by opaque `MediaRef`).
3. `MediaSourceProvider` is the only door into stream resolution. A future `HdhrLineupProvider` must slot in without UI changes.
4. Room schema: `media_ref` table keys on `(source_kind, external_id)` not on IMDb id.
5. ExoPlayer path must not filter by container: MPEG-TS over HTTP must be playable through the exact same `PlayerEngine.play()` call (Media3 supports TS natively — no code needed, just don't block it with mime allowlists).
6. Home screen row system must render a row whose items are `CHANNEL` type without crashing (placeholder card is fine).
7. EPG, TIF (`TvInputService`), HTSP binary protocol: explicitly out of scope; leave no stubs (YAGNI) — the interfaces above are sufficient.

---

## 9. Testing Strategy

1. **Mock addon server first (Phase 1).** A tiny local HTTP fixture server (Kotlin, in `app/src/test` + `androidTest` via OkHttp MockWebServer) serving canned manifest/catalog/meta/stream JSON, including: valid responses, malformed JSON, 30 s-delayed responses, empty stream lists, missing bingeGroups. Every repository test and the autoplay acceptance test run against it. This is the project's most valuable test asset.
2. **Unit tests (JUnit + MockK + Turbine):** protocol DTO parsing (lenient/missing fields), stream-selection cascade scoring (§7.1 step 3 — pure function, exhaustively table-tested), autoplay state machine, progress persistence.
3. **Compose UI tests:** D-pad-only (`KEYCODE_DPAD_*`, `KEYCODE_DPAD_CENTER`, `KEYCODE_BACK`) — no touch events. Cover: focus never lost on home/details/streams screens; off-screen item focus scrolls into view; Back navigation depth.
4. **Emulator matrix (Claude Code can run these):** Android TV AVDs — API 34 (1080p) and the oldest API the minSdk allows. Every phase gate: `assembleDebug`, unit tests, UI test suite on TV AVD.
5. **Manual device matrix (owner executes; Claude Code writes the checklist per release into `docs/TESTLOG.md`):** onn 1080p/4K boxes (family fleet — low-end floor), plus whatever else is available (Chromecast with Google TV, Shield, Fire TV). Checklist covers: cold start time, scroll smoothness, 3-episode autoplay run, external player round-trip with resume position, network-drop mid-playback recovery.
6. **Definition of done for any playback change:** no regression in the autoplay acceptance test (§7.2).

---

## 10. Phases

Mark checkboxes as phases complete; each phase ends with: build green, tests green, STATE.md updated, tag `phase-N-done`.

### Phase 0 — Repo + continuity scaffolding (do first, small)
- [ ] Init public GitHub repo (working name: pick one at kickoff, e.g. `couchpilot` / owner's choice; record in DECISIONS.md). License decision per §11.
- [ ] Android Studio project: Kotlin, Compose + tv-material, Hilt, Room, Media3, Coil, kotlinx.serialization; min/target SDK decision recorded.
- [ ] Create `CLAUDE.md` (§2.4), `docs/STATE.md`, `docs/DECISIONS.md` (with §3 decision as entry #1), `docs/TESTLOG.md`, copy this file to `docs/MASTER_PLAN.md`, copy Gemini doc to `docs/reference/`.
- [ ] GitHub Actions: assembleDebug + unit tests on every PR/push.
- [ ] Empty-shell app boots on TV emulator with a focusable "hello" screen.

### Phase 1 — Addon client + catalogs
- [ ] Protocol DTOs + lenient parsing; `AddonClient`; manifest install/uninstall/reorder UI (URL entry with on-screen keyboard).
- [ ] Mock addon fixture server + parser test suite.
- [ ] Home screen: Continue Watching stub + catalog rows from installed addons; Discover grid (6-col default, density setting); search (via catalog `search` extra).
- [ ] Gate: install a real AIOMetadata instance and browse its catalogs smoothly on the TV emulator.

### Phase 2 — Details, streams, internal playback
- [ ] Details screen (meta, seasons/episodes from `videos` array, cast, backdrop).
- [ ] Stream list: parallel fan-out, incremental render, addon-order preserved, failure chips.
- [ ] ExoPlayer engine + MediaSessionService + Compose overlay controls + subtitle rendering/selection.
- [ ] Watch progress: Room persistence, Continue Watching row goes live, resume dialog.
- [ ] Gate: full browse→play→resume loop against a real AIOStreams instance.

### Phase 3 — Autoplay + external players
- [ ] `AutoplayController` per §7 (state machine unit-tested first, then wired).
- [ ] VLC + MX Player + generic launchers, resume-position round-trip.
- [ ] Gate: §7.2 acceptance test passes on emulator, then on an onn box (owner).

### Phase 4 — Customization + settings + polish
- [ ] Row/catalog manager (reorder/rename/hide), density settings, player preference, autoplay settings, tunneling toggle, debug overlay.
- [ ] Focus/UX audit of every screen against §5; performance pass on lowest-end device.
- [ ] Local profiles (separate addon sets + progress) — only if time permits; otherwise cut (YAGNI) and log it.

### Phase 5 — Release + community
- [ ] Release CI: tag → build signed APK (repo-secret keystore) → GitHub Release.
- [ ] In-app updater: background check of GitHub Releases API, dismissible prompt, download + `ACTION_VIEW` package-installer intent (sideload-friendly; needs `REQUEST_INSTALL_PACKAGES`).
- [ ] README (install via Downloader/ADB, addon setup incl. AIOStreams/AIOMetadata walkthrough), CONTRIBUTING.md, issue templates, architecture doc (mostly extracted from this plan).

---

## 11. Licensing note (resolve at Phase 0)

- `stremio-core` is MIT; the addon protocol docs are public; **stremio-web is GPL** — if any stremio-web code is copied, this project must be GPL-compatible.
- Recommendation: **GPLv3** for the whole app. It matches the ecosystem the community expects for this kind of client, permanently keeps forks open, and removes all copying-from-stremio-web ambiguity. If the owner prefers permissive (MIT/Apache-2.0), then stremio-web becomes reference-only (no code copying). Owner decides; record in DECISIONS.md and LICENSE.
- Ship no scrapers, no provider endpoints, no preinstalled third-party addons beyond user-entered URLs (and optional Cinemeta for metadata fallback). The app is a protocol client; content responsibility stays with user-configured addons. This is the same posture that keeps addon-client projects publishable.

## 12. Explicit non-goals for v1 (YAGNI ledger)

Stremio account login/sync; torrent/infoHash streaming engine; Trakt sync; Chromecast casting; mobile/tablet layout; TIF/Live Channels; EPG; HDHomeRun/TVHeadend (constraints in §8 only); themes; PIN-locked profiles. Any of these entering scope requires a DECISIONS.md entry stating the complexity cost first.
