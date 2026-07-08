# Template: Family — No Anime (Rachael = first instance of this template)

**Status: READY FOR OWNER SIGN-OFF.** Settings only — NO real tokens or
manifest URLs in this file. Placeholders: `<RD_TOKEN>`, `<TMDB_READ>`,
`<TRAKT_CLIENT_ID>`. Real values live only in the private passport
(`~/Documents/Claude/StremioSurfer/users.json` + `trakt-app-claude.json`),
never in the repo.

**Scope reminder:** this template is a *new* configuration applied to
**Rachael's own** AIOStreams + AIOMetadata instances. No one else's live
Stremio/AIOStreams config is opened or edited. Nothing is applied until you
sign off below.

**Modeled on:** Myles' AIOStreams instance (already family-clean, no anime) —
used as the *design reference* only. The *target* is Rachael's instances.

---

## 0. Credentials status (for Rachael)

| Key | Status |
|---|---|
| Real-Debrid | ✅ on file (added 2026-07-07) |
| TMDB read token + API key | ✅ on file (matched what you sent) |
| Torbox | ✅ on file |
| TVDB / RPDB / MDBList | ✅ on file |
| Trakt | ⚠️ app creds saved (`trakt-app-claude.json`); Rachael must finish the **Sign-in-with-Google** OAuth authorize once (`rachaelsstreams@gmail.com`) to mint her personal token. Optional — only needed for watched-hiding / personal lists. |
| Debridio | empty (not used — fine) |
| Her instance URLs | ✅ she has her own AIOStreams + AIOMetadata instances |

---

## 1. Instance count (keep it lean — round-11 N7)

Two addons only for the family profile:

| Instance | Role | Owns in the app |
|---|---|---|
| AIOMetadata × 1 | Catalogs + posters/metadata | Most Discover + Home rows |
| AIOStreams × 1 | Stream aggregation | Playable streams + Streaming-Services rows |

Catalogs are NOT deduped across addons, so we assign **one owner per catalog**
(below) to prevent duplicate Discover rows. One AIOStreams instance is plenty
for a family profile and keeps app launch fast. Cinemeta stays installed as
the `tt` metadata fallback, behind AIOMetadata.

---

## 2. AIOMetadata (Rachael) — catalog owner

Start from the **Movies & TV preset**, not the kitchen-sink export.

**Hard toggles:**
- `sfw = true`, `includeAdult = false`, age-rating → family-appropriate.
- **Anime OFF** — MAL, AniList, "Trending Anime", "100 Anime Essentials",
  "Top Adult Animation".
- **Regional/Hindi OFF** — Sony Liv and similar.
- **Live-TV / FAST OFF** — Pluto TV, Tubi, Roku Channel, Plex, Samsung TV+
  (these caused the round-10 "football typed as movie" noise; also R4).

**Catalog rows AIOMetadata OWNS (order = Home-row order):**
1. Trending Movies
2. Trending Series
3. Popular Movies (Trakt Popular)
4. Popular Series (Trakt Popular)
5. Top Movies This Week
6. Latest Movies
7. Latest Shows
8. IMDb Top 250
9. Genre rows: Action · Comedy · Drama · Sci-Fi · Thriller · Family/Kids

Install order: **AIOMetadata first** (richest meta), Cinemeta as `tt` fallback.

---

## 3. AIOStreams (Rachael) — stream owner + Streaming-Services rows

**Debrid:**
- Real-Debrid **ON** with `<RD_TOKEN>` (now on file).
- Torbox **ON** using the **shared** Torbox key (owner decision 2026-07-07).
  Hide debrid **library/history** catalogs (`catalogModifications` library
  rows = disabled) so private history never shows on the family box.

**Scrapers (family set):** MediaFusion, Torrentio, Comet, Knaben,
TorrentsDB, Meteor, STorz, Sootio.
**Anime scrapers OFF:** SeaDex, nekoBT (`enableSeadex = false`).

**Filters:**
- **Resolution order:** 1080p → 720p → 1440p → 2160p → SD.
  (4K opt-in only — the onn boxes are 32-bit low-power.)
- **Sort:** Cached (desc) → Resolution → Seeders → Size.
- **Language:** preferred `English`; required `English, Original, Unknown`
  → English audio plays first without stranding foreign-only titles
  (matches the app's round-12 English-audio auto-pick).
- **Dedup:** ON. Clean labels: resolution / size / cache flag / audio
  language / source.

**Catalog rows AIOStreams OWNS (to avoid doubling AIOMetadata):**
- **Streaming Services only:** Netflix, Netflix Kids, Disney+, Hulu,
  HBO Max, Prime Video, Apple TV+, Paramount+, Peacock, Starz,
  Discovery+, MagellanTV, Curiosity Stream.

**Catalog rows AIOStreams must NOT expose** (AIOMetadata owns these — turn
them off in AIOStreams so Discover doesn't double up):
- Popular / Trending / Recommended movie + series rows.

**STRIP for family-no-anime:**
- **Crunchyroll** merged catalog + its TMDB rows (anime).
- **Anime Search** catalog (anime type).
- **Sony Liv** (Hindi/regional).

---

## 4. Custom addons: NONE for the family template

The custom-addon wraps (Trakt AIO, TPB, MediaFusion
re-link) need their manifest URLs re-entered by hand on every reconfigure —
your main pain point. Rachael's template uses **only AIOStreams' built-in
presets** (MediaFusion/Torrentio/Comet/etc. are native, no manual re-link).
If her built-in-only set fills streams acceptably, we drop custom addons from
the family template permanently. (Adam's account keeps them as the control.)

---

## 5. Final Discover types (what the family sees)

Movies, Series. **No Anime type. No Live TV.**

---

## 6. How it gets applied (AFTER you sign off)

1. Load Rachael's AIOStreams config in the AIOStreams web UI (or via
   `push_aiostreams.py`) → set §3 toggles → save → new manifest URL.
2. Set §2 toggles on Rachael's AIOMetadata instance.
3. `make_profiles.py --users <live users.json>` → regenerates
   `rachael-*.json` (filename preserved via `profiles.config.json` so any
   saved box link survives).
4. Upload the regenerated bundle to the setup site.
5. Type **"rachael"** on the non-pro box (192.168.1.231) → ProfileSync pulls
   it in.

---

## 7. Decisions locked (owner, 2026-07-07)

1. ✅ **Signed off** on the §2/§3 catalog + filter design.
2. ✅ **Tamtaro dropped** — it's a *person* who builds AIOStreams presets, not
   an addon/list. Nothing to add.
3. ✅ **Torbox = shared key**, kept ON alongside Real-Debrid.
4. ✅ **Trakt: ship v1 WITHOUT** watched-hiding / personal lists. Can be added
   later once Rachael does her one-time Google sign-in.

## 7a. ✅ APPLIED + VERIFIED (session 19, 2026-07-07)

**Rachael's primary AIOStreams instance is live with this template.** Built
`templates/primary.json` from Adam's `main-…-template.json` (removed anime
presets seadex/neko-bt, debridio presets, MediaFusion [leak-prevention], and
the deprecated usa-tv preset; forced per-user keys to placeholder; enableSeadex
=false). Simulated the exact payload + leak-scanned it (no owner secrets), then
`push_aiostreams.py … --user Rachael --strict`. Pulled it back and confirmed:
12 scrapers, no anime, 1080p-first, English, streaming-catalogs on, her RD.
Manifest URL unchanged (PUT update) → her box needs no re-add. All 11 users
intact; users.json.bak written. Remaining = regen her profile if you want the
AIOList row added, then upload + point her box at it.

### (historical) how it was unblocked

The 0-byte `templates/*.json` no longer blocks us. The live AIOStreams config
is retrievable via the instance API (`GET {base}/api/v1/user`, HTTP Basic
`uuid:password`, using `stremio_api.SSL_CONTEXT` for the CA bundle). Session 19
pulled both configs to the **private** passport dir (secrets — never the repo):
- `templates/pulled-myles-primary.json` (73 KB, rich family reference)
- `templates/pulled-rachael-primary.json` (8 KB, her current — also the backup)

**Config lives at `data.userData`.** AIOStreams write path = push_aiostreams.py
(`PUT {base}/api/v1/user`, body `{uuid, password, config:<userData>}`), which
knows the exact envelope + does key-substitution. Use `--dry-run` first.

### Exact changes to bring Rachael → family-no-anime (schema-grounded)
Low-risk filter/sort (cannot break playback, only reorder/filter results):
- `preferredResolutions` → `["1080p","720p","1440p","2160p","576p","480p",
  "360p","240p","144p","Unknown"]` (she's currently 2160p-first; box is 32-bit).
- `enableSeadex` → `false` (anime scraper off).
- `requiredLanguages` → `["English"]`; `preferredLanguages` →
  `["English","Original","Unknown"]` (matches Myles + the app's round-12 logic).

Catalog-row cleanup (needs the recovered config's `catalogModifications` +
`mergedCatalogs` detail — a full-budget pass):
- Keep AIOMetadata as catalog owner; in AIOStreams expose Streaming-Services
  rows only; strip Crunchyroll / Anime Search; confirm no adult catalogs are
  enabled (keyword "adult"/"anime" appears in her JSON but likely as
  option/service *names*, not enabled catalogs — verify before trusting).

### Finish sequence
1. Build `templates/primary.json` from `pulled-myles-primary.json` with the
   above changes (strip anime presets `seadex`,`neko-bt`; apply filters), OR
   edit Rachael's own pulled config directly.
2. `push_aiostreams.py --owner "Myles Manuel" --template templates/primary.json
   --instance primary --user "Rachael" --dry-run` → review → drop `--dry-run`.
3. New manifest URL is written back to users.json → `make_profiles.py` →
   upload → type "rachael" on 192.168.1.231.

Everything else is ready: all her keys are on file (RD, TMDB, shared Torbox),
instances exist, spec signed off. Remaining = the live PUT (one careful pass).

---

## 8. Derives the other 3 templates

Once this is signed off + proven on Rachael's box, the same structure yields:
**Family-Anime** (re-enable anime catalogs + SeaDex/nekoBT scrapers),
**NSFW-no-anime** (`includeAdult=true`, adult catalogs on), and
**NSFW-Anime** (both). Same lean 2-instance shape each time.
