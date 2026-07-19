# STATE — updated 2026-07-19 by session 39

## ⚠️ SESSION 39 (2026-07-19) — Guide APK refreshed: OpenStream alpha.60 live at savoy.click/OpenStreams.apk (NOT OTA-published)
- **Owner asked to update the guide's APK.** Facts established: the picture
  guide at savoy.click/index.html installs via Downloader short code
  **5603325** → https://savoy.click/OpenStreams.apk (note the S — owner
  says "OpenStream.apk" but the hosted file/code use OpenStreams.apk).
  That file was a byte-copy of pre-rebrand sstreams-59.apk.
- **Built alpha.60 (versionCode 60) from feat/openstream-rebrand** (bump
  committed — the rebranded build must outrank installed alpha.59). Gates
  green (assembleDebug + testDebugUnitTest), assembleRelease, badging
  verified: dev.openstream.tv / 60 / 0.3.0-alpha.60 / label "OpenStream".
  scp'd atomically (.tmp → mv) to savoy.click/OpenStreams.apk, chmod 644,
  hosted sha256 == local, HTTPS 200. Dreamhost key auth works from this
  Mac (no password needed — owner pasted one in chat; unused).
- **OTA channel deliberately NOT touched:** setup/app/version.json still
  offers alpha.59, sstreams-latest.apk (savoy.click/app) still = 59. The
  2 live boxes have not been offered the rebrand — publishing alpha.60
  OTA (tools/publish_update.sh after this branch's release build) is the
  owner-blessed next step, not assumed. versionCode 60 is now CONSUMED
  by this build; any future OTA alpha.60 must be built from this same
  branch state or bump to 61.
- **Owner Qs answered from live users.json:** Myles Dad logs in as
  mylesdad@savoy.solutions (personal gmail on file:
  triparishelectrical@gmail.com); Manuel Momma = manuelmomma@gmail.com.
  Passport rename question: NO button propagates a name change (Save Now
  only writes users.json); index.php name→file map + profile slugs + tool
  lookups depend on the name — rename = its own job (session-34 answer
  stands). Their TV-visible rec rows already say "YOU", unaffected.
⏳ **NEXT ACTION:** (a) Owner: test a fresh install via the guide (code
5603325) — should land "OpenStream" 0.3.0-alpha.60. (b) On owner's word:
publish alpha.60 OTA (assembleRelease already done on this branch →
tools/publish_update.sh) so the 2 boxes + savoy.click/app get the rebrand;
consider merging feat/openstream-rebrand → main first. (c) If owner
renames Myles Dad / Manuel Momma in the passport: run the rename job
(index.php map + keep hosted profile filenames stable). (d) Session-38
backlog unchanged.

## ⚠️ SESSION 38 (2026-07-19) — Subtitles fan-out built (§4.1 gap closed for the main video)
- **Owner noticed the auto-picked subtitle track sometimes doesn't match**
  (no one complained; owner's own observation). Already-known gap
  (MASTER_PLAN §4.1): the player only ever used subtitles embedded in the
  chosen stream, never queried installed addons' `subtitles` resource
  (AIOMetadata + AIOStreams both declare it, backed by owner's
  OpenSubtitles + OpenSubtitlesV3+ instances) — more/better-tagged tracks
  in the pool should reduce mismatches from the language-preference picker.
- **Built:** `SubtitleRepository` (mirrors `StreamRepository`), fanned out
  in parallel with the stream fan-out in `StreamListViewModel.init`, merged
  into the chosen stream's own subtitles via `mergeSubtitleTracks` (URL
  dedupe, embedded tracks win ties) — see DECISIONS #66 for the full
  design. Covers: initial playback, "Play with…" external launch, "try
  another server" (all reuse one fetch per video via the new
  `PlaybackRequest.addonSubtitles` field). No UI changes needed — the
  existing `TracksDialog`/ExoPlayer sideload path already renders whatever
  lands in `PlayableSource.subtitles`.
- **Scoped OUT on purpose (not a regression):** the autoplay next-episode
  chain still uses stream-embedded subtitles only — each episode is a
  different video id and prefetching wasn't in scope this session.
- **Verified:** `./gradlew assembleDebug testDebugUnitTest` — BUILD
  SUCCESSFUL, all unit tests pass (added 3 new tests for the merge/mapping
  helpers). **EMULATOR-VERIFIED same session against the REAL addons:**
  the AVD's app DB had lost the owner's addons (only Cinemeta + local test
  addon remained), so adam's LIVE hosted profile (savoy.click/setup, the
  canonical 6-addon bundle) was fetched and its manifests injected into
  the app DB (local test addon kept, now sortOrder 6). Then: Discover →
  "The Invite" (2026 movie) → auto-play → real debrid stream → Audio &
  subtitles dialog shows the merged pool — the stream's own embedded
  tracks (English [SRT]/[SDH], French · Metropolitan, Spanish · Latin
  American, German, Dutch) PLUS the addon-fetched OpenSubtitles tail
  (English, English (2)…(7)+) — picked an addon track and the captions
  RENDERED in sync. Emulator navigation gotcha for future sessions: the
  player control bar auto-hides after 5s and the FIRST press only re-wakes
  it — screenshot-paced key presses each get swallowed; send
  wake→DOWN→LEFT→CENTER as one quick sequence to reach Audio & subtitles.
⏳ **NEXT ACTION:** (a) Nothing owner-blocking from this session — the
fan-out ships with the NEXT alpha whenever one is published (no OTA cut
this session; it's in main). (b) Rest
of session-37 NEXT ACTION below still stands: alpha.58→59 update on the 2
boxes (LEFT then OK); savoy.click/app installs whenever owner visits/ships
a box; backlog: 9s bias knob, #16 skins, adam's streaming-config 401
(cosmetic), optional Downloader code, autoplay next-episode subtitle
fan-out (DECISIONS #66 follow-up).

## ⚠️ SESSION 37 cont. (2026-07-19) — ROLLOUT COMPLETE: Manuel Momma pushed + Myles Dad AIOLists built/pushed/hosted
- **MANUEL MOMMA PUSHED (8 addons, read-back verified).** Owner set her
  passport password to match her metadata passwords — Stremio login now
  works. Her old strem.io Trakt scrobbler dropped with the push (bundle
  excludes it). **STREMIO ROLLOUT = 11/11 DONE** (Rachael excluded by
  standing rule).
- **MYLES DAD AIOLISTS CREATED (owner: "make him one and include it").**
  AIOLists has no accounts — config is gzip+base64url-encoded INSIDE the
  manifest URL (https://aiolists.elfhosted.com/<cfg>/manifest.json).
  Built from Toby's search-only template: apiKey = Myles Dad's OWN
  mdblist key, tmdbBearerToken = his OWN tmdb_read (others share one;
  he has his own), TMDB session/account/username + Trakt username/uuid
  nulled (no SStreams TMDB session for him; Trakt tokens were null in
  template anyway — search needs neither), tmdb_watchlist/favorites
  dropped from listOrder (account-bound), upstash cache = the shared
  dominant-civet pool (4 users already share it; Jody==Mike token
  verified). LIVE manifest verified: exactly 4 search-required catalogs
  (Search Movies/Series/Merged/Anime) + meta — same shape as adam's.
  Written to live users.json (backup: config_backups/2026-07-19/
  users.json.pre-mylesdad-aiolists) → re-pushed his Stremio: 8 addons,
  AIOLists LAST, read back.
- **His hosted profile updated too:** make_profiles.py --users <live>
  regenerated all (diff vs hosting/ = ONLY myles-dad changed, AIOLists
  appended); scp'd his one file to Dreamhost, chmod 644, hosted sha256
  == local, hosting/ copy synced.
- **TRAKT SCROBBLE (owner: "don't keep it") — NOTHING TO DO:** the
  canonical bundle already excludes it and every pushed account had it
  removed (Jacob/Anna-Jay/Manuel Momma had it; rest never did). Owner
  does NOT need to touch anyone's settings. Only Rachael still has hers
  (untouched, live-user rule). trakt_scrobble URLs stay in users.json.
- **Tool fix:** push_stremio_bundle.py backup filename now carries
  date+TIME — today's Myles Dad re-push overwrote the morning backup
  (same-day name collision); can't happen again. (His pre-cleanup addon
  list survives in the dry-run log only; owner wanted those addons gone.)
- **Update-dialog Q answered (owner: "why not auto-accept / just OK?"):**
  Android blocks silent installs for sideloaded apps — only privileged
  store apps (Play) may auto-update; the confirm screen is the OS's, apps
  can't restyle/refocus it, and onn firmware highlights Cancel. LEFT+OK
  coaching stays. (True silent updates would need device-owner
  provisioning = factory reset per box; not worth it.)
⏳ **NEXT ACTION:** (a) Owner leftovers: alpha.58→59 update on the 2
boxes (LEFT then OK). Toggles already approved ("looks ok"); scrobbler
exclusion blessed this session. (b) Per-box app installs via
savoy.click/app whenever owner visits/ships a box. (c) Backlog: 9s bias
knob, #16 skins, adam's streaming-config 401 (cosmetic), optional
Downloader code (aftvnews.com/code → savoy.click/app).

## ⚠️ SESSION 37 (2026-07-19) — STREMIO ROLLOUT: 7 of the remaining 8 pushed; Manuel Momma blocked on password
- **push_stremio_bundle.py RUN (owner: "push to the remaining 8"):** Myles
  Manuel (7 — no Local Files in account), Myles Mobile (7 — same), Myles
  Dad (7 — no aiolists_manifest in his passport, slot skipped as WARNed),
  Jody Miller (8), Mike Miller (8), Jacob Savoy (8), Toby Savoy (8) all
  carry the canonical bundle now, AIOLists LAST, read-back verified by the
  tool. Run from ~/Documents/Claude/StremioSurfer against the LIVE
  users.json (same file server :5000 reads). Stale addons dropped as
  designed (old fortheweak/weebs AIOStreams URLs; Jacob's strem.io Trakt
  scrobbler; Myles Dad's pile of legacy addons — torrentio, comet,
  mediafusion, opensubtitles ×3, usatv, netflix-catalog, imdb-catalogs,
  tmdb, caching.stremio.net). Pre-push backups:
  StremioSurfer/stremio-addons-backup-<slug>-2026-07-19.json (all 7 on
  disk, verified).
- **⚠️ MANUEL MOMMA BLOCKED: Stremio login fails** for
  manuelmomma@gmail.com — API says wrongPass. Her top-level passport
  password is a clean ASCII string (no whitespace), so the account's real
  password simply differs from the passport convention. NOTHING pushed to
  her account. Owner must recover/reset her Stremio password (or supply
  the right one), update her passport entry, then one command finishes
  the rollout: `python3 push_stremio_bundle.py --user "Manuel Momma"`.
- Stremio-account rollout status: 11 of 12 done (adam s29, Anna/Jay +
  Clarence s36, these 7 now); Rachael excluded by standing rule; Manuel
  Momma pending password.
⏳ **NEXT ACTION:** (a) Owner: fix Manuel Momma's Stremio password in the
passport → rerun push_stremio_bundle.py --user "Manuel Momma". (b) Owner
decides: Myles Dad has NO AIOLists account — create one (search-only) or
leave him without merged search. (c) Session-36 leftovers stand: owner
eyeballs passport toggles, alpha.58→59 update on the 2 boxes, blesses/
vetoes the trakt_scrobble exclusion. (d) Per-box app installs via
savoy.click/app whenever owner visits/ships a box.

## ⚠️ SESSION 36 cont. (2026-07-18) — Anna/Jay + Bop Stremio pushed, alpha.59 (install-screen coaching)
- **push_stremio_bundle.py RUN (owner: "Push to anna and bop's Stremio"):**
  Anna/Jay (7 addons — no Local Files in their account) + Clarence Savoy
  (8 incl. Local Files) now carry the canonical bundle, AIOLists LAST,
  read-back verified in exact order. Their stale AIOStreams URLs + the
  strem.io Trakt scrobbler were dropped (expected). Pre-push backups:
  StremioSurfer/stremio-addons-backup-{anna-jay,clarence-savoy}-2026-07-18
  .json. Tool fix en route: backup slug now filesystem-safe ("Anna/Jay"
  used to crash on the '/').
- **alpha.59 PUBLISHED (versionCode 59):** owner asked "why not just put
  ok as default" on the update dialog — OUR dialog already focuses
  Update now; the confusing screen is ANDROID'S system installer (apps
  can't restyle/pre-focus it; highlights Cancel on the onn boxes). Fix =
  the prompt now coaches: "On the next screen, press LEFT to pick
  Update, then OK." Gates green (387/0), published, savoy.click/app
  alias auto-refreshed by the new publish script (verified live).
- Context from owner: ONLY 2 boxes run the app today (his + Rachael's);
  everyone else is Stremio-on-their-own-box → the Stremio bundle pushes
  ARE the rollout for them, box-by-box app installs come later via
  savoy.click/app. Toggles: owner approves ("very nice").
⏳ **NEXT ACTION:** on owner's word, push_stremio_bundle.py for the
REMAINING Stremio users (myles manuel/mobile/dad, jody, mike, jacob,
toby, manuel momma — Rachael excluded by default; adam already done
s29/s36); then per-box app installs via savoy.click/app whenever he
visits/ships a box. Rest of session-36 NEXT ACTION below still stands
(owner eyeballs toggles, alpha.58→59 update on the 2 boxes, trakt_
scrobble bless/veto).

## ⚠️ SESSION 36 (2026-07-18) — THE CUTOVER SHIPPED: toggles + canonical profiles LIVE on Dreamhost + Library + alpha.58 PUBLISHED OTA + savoy.click/app install link
**The whole cutover from session 35's NEXT ACTION, end to end:**
- **#5 PASSPORT PER-ADDON TOGGLES BUILT + verified live on :5000.** Each
  Addon Accounts card (3 AIOStreams, 2 AIOMetadata, AIOLists) wears an
  instant-save "On their TV"/"Left out" switch. Storage:
  `user.disabled_addons` = list of slot keys ("aiostreams.nightly",
  "aiolists", …), absent = everything ships; users.schema.json updated.
  make_profiles.py AND push_stremio_bundle.py both skip toggled-off slots
  (elfhosted/nightly alias accepted). Tested on adam: toggle → users.json
  written → toggle back → key removed clean; zero console errors.
- **make_profiles.py REBUILT to the canonical bundle** (DECISIONS #65):
  Cinemeta → AIOMeta Discover → AIOMeta Streaming → AIOStreams
  Primary/Backup/Elfhosted → **AIOLists LAST** (search-only decision).
  Dropped the live-Stremio-collection union (profile = passport, period)
  and **excluded addons.trakt_scrobble** (strem.io account addon = a 2nd
  scrobbler next to AIOMetadata Discover; owner can veto — URLs still in
  users.json untouched).
- **PROFILES REGENERATED + LIVE ON DREAMHOST.** ⚠️ Found the live
  StremioSurfer/profiles.config.json carried a STRAY token set that was
  never uploaded — reconciled to server truth (ssh ls) so every box's
  stored URL keeps working; backup at config_backups/2026-07-18/
  profiles.config.json.pre-cutover. Uploaded 11 profiles (10 users +
  NEW myles-dad-AJGYmhvwOXE.json; Manuel Momma's file now actually
  hosted) + regenerated index.php (Myles Dad + Manuel Momma in the name
  map now; brand SStreams). Rachael SKIPPED everywhere (live-user rule;
  her files untouched, verified by timestamps). Live .htaccess kept (it
  carries the no-cache rule from the box-.117 incident). VERIFIED:
  hosted-vs-local sha256 match, name-lookup api answers "myles d", and
  ALL 65 unique manifest URLs across the hosted profiles return 200.
  Boxes pick the new bundle up on next app launch (ProfileSync).
- **#2 LIBRARY SCREEN BUILT → alpha.58 PUBLISHED OTA** (versionCode 58,
  readback verified). New rail section (bookmark glyph, between Discover
  and Search): everything watched from local history, one tile per title,
  Stremio's exact filter bar (All / Last Watched / A–Z / Z–A / Most
  Watched / Watched / Not Watched) + All types/Movies/Series lens in the
  header. Series tiles get their show's REAL name/poster from the meta
  cache (progress rows only store the episode label); offline falls back
  to that label. 387 tests green (9 new LibraryModelTest). EMULATOR
  VERIFIED with real playback via tools/test_addon_server.py: watched ✓
  and 20%-ring indicators, Watched/Not-Watched splits, name enrichment —
  plus the round-22 leftovers: **#7 caption** (owner wording + inline
  magnifier, fits with rail open AND closed) and **#3 grid** (no gray
  strips at 6 or 8 columns). Two emulator GPU meltdowns mid-session
  (DECISIONS #64 pattern) — cold-restarts fixed, not app bugs. One
  HomeViewModelTest flake under emulator CPU load — passes clean solo
  and in the full run once the emulator was killed.
- **NEW: https://savoy.click/app — the one install link, forever.**
  302 → setup/app/sstreams-latest.apk; publish_update.sh now refreshes
  the latest alias on every OTA publish, so the link never goes stale.
  For NEW installs/recovery on a box: open Downloader or TVBro, type
  savoy.click/app, install. sha256 of hosted latest == local release.
  Recommendation logged: skip Play/Amazon stores (policy risk for a
  streaming-addon app, and Play App Signing would REKEY the app —
  breaking OTA for every existing box). Downloader code optional later
  (aftvnews.com/code → point it at savoy.click/app).
⏳ **NEXT ACTION:** (a) Owner: hard-refresh passport → Addon Accounts →
eyeball the new toggles (they now really control profile regen + Stremio
pushes). (b) Boxes self-offer alpha.58 — press Update (dialog focuses
Cancel; Update is LEFT). Try the new Library tab. (c) On owner's word:
push_stremio_bundle.py --all (Rachael excluded by default) to align
Stremio accounts with the same canonical bundle. (d) Owner blesses/vetoes
the trakt_scrobble exclusion from profiles (one-scrobbler convention).
(e) Backlog: 9s bias knob, #16 skins; adam's own streaming-config 401
(cosmetic); make_user_configs kit for Myles Dad debridio done in s35.

## ⚠️ SESSION 35 cont.2 (2026-07-18) — THE 8 ELFHOSTED ACCOUNTS CREATED: ACCOUNT SETUP IS COMPLETE FOR EVERYONE
**Owner said "Create the 8" → all 8 remaining elfhosted/nightly AIOStreams
accounts created via push_aiostreams.py POST (each user's own kit
aiostreams-elfhosted.json as --template; live users.json; pre-write backup
config_backups/2026-07-18/users.json.pre-elfhosted-8-s35): Anna/Jay, Myles
Manuel, Myles Mobile, Jody, Mike, Clarence, Jacob, Toby — all first-try.**
- Pre-flight caught nothing to fix: all 8 kits carry the user's OWN debridio
  key in the enabled Watchtower preset; Myles Mobile's kit ships RD
  DISABLED (standing decision holds — verified in the live account too).
- Slot passwords = each user's top-level passport/Stremio password (owner
  confirmed that's the convention; recorded into each nightly slot since
  push_user's return drops plaintext).
- VERIFIED all 8: API 200 with stored uuid:password, RD enabled with own
  key (Myles Mobile: RD disabled as intended), public manifest 200.
- **ROSTER STATUS: every one of the 12 users now has ALL 5 addon accounts
  (3 AIOStreams + 2 AIOMetadata) and every Discover has Trakt connected.
  Nothing account-related remains — the rollout is unblocked.**
⏳ **NEXT ACTION:** the cutover build (owner decisions all in): #5 passport
per-addon toggles → rebuild make_profiles to the canonical bundle with
AIOLists LAST (search-only) → regenerate all hosted profile JSONs → scp to
Dreamhost → optional push_stremio_bundle.py --all (Rachael excluded by
default). Then Round-22 app remainder: #2 Library screen, emulator-verify
#7 caption + #3 Discover grid, gates → OTA alpha.58.

## ⚠️ SESSION 35 cont. (2026-07-18) — OWNER DECISIONS LANDED + Myles Dad debridio everywhere + rollout audit
- **DECISION (owner): AIOLists STAYS in the bundle, search-only, ordered
  LAST.** The 8-slot bundle order for make_profiles/push_stremio_bundle:
  AIOLists moves to the bottom (was Rachael's-order position). Apply when
  building #5/profile regen.
- **DECISION (owner): Myles Dad keeps Myles' debridio ("add it to the
  rest").** Done: passport keys.debridio = Myles Manuel/Mobile's key;
  all 3 of his kit aiostreams files re-enabled per template pattern
  (primary: TMDB/TVDB/Watchtower on; backup/elfhosted: Watchtower on)
  with the key filled; LIVE primary+backup updated via surgical
  GET-modify-PUT and re-GET verified (pre-write copies in
  config_backups/2026-07-18/myles-dad-live-*.pre-debridio.json). Live
  elfhosted already had it.
- **Gooey design: owner approves ("it's cool").** Connectors trimmed by
  owner in the app (ClickUp/Figma/Notion/Gmail MCP now disconnected).
- **ROLLOUT AUDIT (who's left for full build push):** account-complete =
  adam, Rachael, Manuel Momma, Myles Dad. The other 8 (Anna/Jay, Myles
  Manuel, Myles Mobile, Jody, Mike, Clarence, Jacob, Toby) are each
  missing ONLY the elfhosted/nightly AIOStreams account — their
  AIOMetadata pairs all exist (manifest_url+password per slot; NOTE
  AIOMetadata manifests are uuid-only URLs, uuid derivable — session
  31's "uuid backfill" isn't a separate field, don't re-chase). All 8
  have kit configs → one push_aiostreams.py --instance nightly run per
  user creates them on the owner's word.
- **TRAKT SWEEP (read-only, all 12 users × both AIOMetadata configs):
  NOBODY needs a Trakt connect.** Every Discover = CONNECTED
  (apiKeys.traktTokenId set — NOTE it's nested under apiKeys, not
  top-level) with traktWatchTracking=true; every streaming = not
  connected + false. Exactly the one-scrobbler-per-person convention.
  Gotchas learned: config/load needs the user's top-level passport
  password when the aiometadata slot's password field is empty (most
  are). One loose end: adam's OWN streaming config rejects both stored
  passwords (401) — passport password gap on his slot only, cosmetic.

## ⚠️ SESSION 35 (2026-07-18) — 3 MISSING AIOSTREAMS ACCOUNTS CREATED VIA API (owner gave the word)
**Ops session in ~/Documents/Claude/StremioSurfer; no app build. Executed
session 34's NEXT ACTION (b) end-to-end:**
- **Myles Dad nightly uuid BACKFILLED** from his manifest URL (uuid +
  encrypted_password segments); his elfhosted plaintext password probed
  read-only via GET /api/v1/user = his top-level passport password —
  recorded in the slot.
- **3 ACCOUNTS CREATED via push_aiostreams.py POST** (each user's own kit
  config as --template, live users.json as --users; pre-write backup at
  config_backups/2026-07-18/users.json.pre-aiostreams-create-s35):
  · Myles Dad PRIMARY (fortheweak.cloud) — first attempt hit
    USER_INVALID_CONFIG "Debridio addons need an API key": his kit had
    Debridio TMDB/TVDB/Watchtower ENABLED with empty keys (kit-gen bug;
    he has no debridio). Fixed in all 3 of his aiostreams kit files
    (presets disabled; pre-fix copies in config_backups/2026-07-18/).
    Second blocker was the KNOWN transient upstream-manifest 502 → retry
    succeeded.
  · Myles Dad BACKUP (fortheweebs) — created clean.
  · Manuel Momma NIGHTLY (elfhosted) — created clean. (Her nightly slot was
    literally {} — push_user would KeyError; slot skeleton pre-filled, and
    her slot password kept consistent with her other two instances.)
  Plaintext slot passwords re-added after the run (push_user's returned
  slot dict drops them — known tool quirk, worth fixing someday).
- **VERIFIED all 3:** GET /api/v1/user 200 with stored uuid:password; RD
  enabled with each user's OWN passport RD key (no Round-21-style mixup);
  public manifest.json 200. Passport server :5000 reflects it (reads disk).
  BOTH users now have all 3 AIOStreams + both AIOMetadata (Trakt connected).
- **FINDING for owner:** Myles Dad's LIVE elfhosted config carries Myles
  Manuel/Mobile's debridio key (cec32a…) with Watchtower enabled — rode in
  from whatever the owner imported. Passport says Myles Dad has no debridio.
  Left untouched; owner to bless or remove.
- Housekeeping (owner ask): audited local Claude config for token drains;
  disabled the superpowers plugin for this project (.claude/settings.json,
  untracked/local). The heavier per-session cost is the desktop-app MCP
  connectors (ClickUp/Figma/Notion/Gmail/Drive/browser/computer-use) —
  only toggleable in the app's connector settings, not from a session.
⏳ **NEXT ACTION:** (a) Owner: bless/remove Myles' debridio key in Myles
Dad's live elfhosted; hard-refresh passport (Gooey design feedback still
open); DECIDE AIOLists' fate in the final bundle (drop vs keep-for-search)
— this gates (c). (b) Both Myles Dad + Manuel Momma are account-complete →
when owner says go: regenerate their profiles (make_profiles still STALE —
needs the 8-slot/endgame bundle first) + scp to Dreamhost, and/or
push_stremio_bundle.py for their Stremio accounts. (c) Round-22 app
remainder unchanged: #2 Library screen, emulator-verify #7 caption + #3
Discover grid, gates → OTA alpha.58. (d) #5 passport per-addon toggles.

## ⚠️ SESSION 34 (2026-07-18) — BLOB SCENE FINISHED + LIVE AIOMETADATA RENAMES (YOU/Bop) + accounts answered
**Continuation after the prior account hit usage limits mid-turn. All work in
~/Documents/Claude/StremioSurfer + live addon accounts (owner-requested).**
- **passport.html blob scene DONE:** prior session left drawBlob() unwired —
  wired as the 4th BGFX scene (dropdown option, frame dispatch, click →
  burstBlob shard explosion) + new "Gooey" design preset (midnight/blob/
  clear/glow/70). Tuned after live look: core radius .07–.095, alpha cap
  ~.9×(.22+.5a) so mid-screen panels stay readable. The prior session's
  click-offset fix (canvas needed CSS width/height:100% on retina) was
  already in. VERIFIED live on :5000: Gooey preset renders, cursor blob lags
  + gooily merges, click bursts core + ripple lands ON the click point,
  design persists across reload, zero console errors.
- **RENAMES (owner ask) — live AIOMetadata configs edited via the REAL
  AIOMetadata API (v2.8.0, both hosts; discovered from the configure JS):**
  POST /api/config/load/{uuid} {password} → {success,config};
  PUT /api/config/update/{uuid} {config,password}; create = POST
  /api/config/save; manifest URL = {base}/stremio/{uuid}/manifest.json
  (uuid only); requiresAddonPassword=false both hosts; needs browser UA.
  Applied + re-load-verified + public-manifest-verified, pre-write backups
  in StremioSurfer/config_backups/2026-07-18/aiometa-*.pre-rename.json:
  · Myles Dad: addonName → "AIO - Discover"/"AIO - Streaming" (rows were
    ALREADY "Recommended for YOU" — owner had renamed them in the UI).
  · Manuel Momma: rows "Recommended for Manuel" → "Recommended for YOU" ×3,
    addonName → "AIO - Discover"/"AIO - Streaming".
  · Clarence: rows → "Recommended for Bop" ×3, addonName → "AIO - Bop -
    Discover/Streaming".
  Live AIOStreams configs of all 3 swept read-only: ZERO name occurrences.
  Kit wrapper metadata.name ("SFW bundle (primary) — <Name>") left on
  purpose — import-UI label only, never shows on a TV.
- **make_user_configs.py:** RECS_DISPLAY_NAME → {myles dad: YOU, manuel
  momma: YOU, clarence savoy: Bop} + new ADDON_DISPLAY_NAME (None = drop
  name from addonName). Kits regenerated for the 3 users; verified names +
  no leaks. Answer given to owner: passport name changes do NOT propagate
  to live addons (only future kit generation); passport entry stays
  "Clarence Savoy" — renaming it would break the index.php name→file map +
  tool lookups unless done as its own job.
- **Accounts question ANSWERED with live data:** Manuel Momma has AIOStreams
  primary+backup (only elfhosted/nightly missing); Myles Dad has elfhosted
  (manifest in passport, API-verified reachable; slot uuid EMPTY — backfill
  from manifest URL before any push so push_user PUTs instead of POSTing a
  duplicate!) and is missing primary+backup. Both users' AIOMetadata pairs
  EXIST with Trakt CONNECTED (traktTokenId set; watchTracking discover=true
  / streaming=false — convention holds; Clarence same). push_aiostreams.py
  POSTs create accounts → owner needs to create NOTHING; the 3 missing
  AIOStreams accounts can be created via API on his word (point --users at
  the live server's users.json, then backfill new uuids/manifests).
⏳ **NEXT ACTION:** (a) Owner hard-refreshes passport, tries Gooey design
(Theme → Designs) + Blob background, reports tweaks. (b) On owner's word:
backfill Myles Dad nightly uuid, then push_aiostreams.py create Myles Dad
primary+backup + Manuel Momma elfhosted from their kit configs; write new
uuids/manifests to passport. (c) Round-22 app remainder unchanged: #2
Library screen, emulator-verify #7 caption + #3 Discover grid, gates → OTA
alpha.58. (d) #5 passport per-addon toggles → 8-slot profile regen (fix
stale make_profiles) → scp Dreamhost.

## ⚠️ SESSION 33 cont. (2026-07-18) — PASSPORT DESIGN SYSTEM v2 ("themes have no umph" fixed)
Owner liked the redesign but called the theme settings weak (abrupt cycle,
invisible glass/glow, color-swap-only themes). Rebuilt in passport.html:
- **BGFX canvas engine** (fixed layer behind #app, pointer-events:none):
  3 scenes — aurora (drifting additive color clouds), stars (parallax
  starfield + twinkle + shooting stars), horizon (retro sun + rolling
  neon grid). Tinted LIVE from the theme's --accent (hue ±42/±318 palette),
  colors LERP so palette changes glide; mouse parallax; click ripples;
  ~30fps cap + DPR 1.5 + visibilitychange pause. body.bg-on shows canvas,
  .main goes transparent so it breathes through gaps.
- **Panel styles**: solid / panel-frost (blur16 translucent) / panel-clear
  ("Clear glass HD": 22%-opacity panes, top-edge highlight, inner sheen,
  translucent pills). Old psp_eff_glass=1 migrates → frost.
- **Glow rebuilt with umph**: card halos, accent-tinted borders, avatar/
  tab/name glows (body.effect-glow).
- **Smooth theme swap**: .theme-anim (1.05s transitions on everything) is
  applied for 1.2s around EVERY applyTheme — cycle ticks crossfade now;
  suppressed on first paint. Old cycle-active CSS removed.
- **DESIGNS presets** (one-click bundles: palette+bg+panel+glow+intensity):
  Basic / Aurora Glass / Crystal HD / Deep Space / Synthwave / Greenhouse;
  touching any knob flips design → custom. Theme modal is now a "Design
  studio": design cards w/ swatch previews (THEME_SWATCH map must mirror
  the CSS theme blocks), palette grid w/ color chips, background mode +
  intensity slider, panel seg-toggle, glow, cycle (unchanged mechanics).
  Storage: psp_design/psp_bg/psp_bg_int/psp_panel (+ legacy keys).
- VERIFIED live on :5000: all 6 designs render distinctly (screenshots),
  cycle crossfades w/ canvas retint, tabs/edit regression clean, zero
  console errors. users.json untouched.
⏳ NEXT ACTION addition: owner hard-refreshes, opens Theme → Design studio,
tries the designs (esp. Crystal HD + Deep Space), reports tweaks (intensity
default, more scenes, per-design cycle?).

## ⚠️ READ FIRST (session 33 — 2026-07-18 — MYLES DAD KIT GENERATED + PASSPORT REDESIGN (tabs/menus/beauty) — StremioSurfer work, no app build)
**All work in ~/Documents/Claude/StremioSurfer (not this repo). Continuation of
session 32 (2026-07-18, same day, not in this log): that session added the
passport's clickable-empty-pill/✎ edit entry points, billing seg-toggle, the
real trakt_code field (migrate_trakt_codes.py moved codes out of Notes), and a
first "More tools" collapse.** This session:
- **MYLES DAD SET UP (owner re-added him; Round 19's "off the list" is DEAD):**
  his passport entry now carries his own RD (exp 2026-10-17) / gemini / tmdb /
  tvdb / mdblist keys + Myles' torbox + a trakt_code. Removed "myles dad" from
  make_user_configs.py SKIP_USERS and added RECS_DISPLAY_NAME override so his
  rec rows read "Recommended for Myles Dad" (NOT "for Myles" — wrong person).
  Generated setup_kits/myles-dad/configs/ (5 JSONs + MISSING.txt). VERIFIED:
  zero template-owner leaks, his keys in all 3 AIOStreams files (RD enabled),
  sfw=true/includeAdult=false, 186/99 catalogs, no org_trakt, no "Adam"
  strings, required=[English]/preferred=[English,Original,Unknown] everywhere.
  MISSING: debridio key (he has none) + the 2 standing connect-own-Trakt notes.
  AIOLists note absent (Trakt AIO preset disabled in the template — consistent
  with "doing away with aiolists").
- **PASSPORT REDESIGNED (passport.html; backup at config_backups/2026-07-18/
  passport.html.bak2-preredesign):** (a) TABS with a home view — Overview
  (Account, read-only Real-Debrid card [status/expiry/plan/portal/checked,
  honors billing.rd_not_needed], Billing, Notes) / API Keys (3-col field
  grid) / Addon Accounts (5 instance cards + AIOLists); chosen tab persists
  (localStorage psp_tab). (b) SPACE FIX: rigid 4-col grid → per-tab CSS
  masonry columns (break-inside:avoid) — no more row-height holes.
  (c) BUTTONS: everyday actions stay visible (Setup Kit / Check RD / RD Off /
  RD On + Instance/dry-run); the rest branch into pop-UP category menus
  AIOStreams▾ / Stremio▾ / More▾ with per-item descriptions, "All users"
  groups danger-styled; menu per-user items carry .ab-userbtn so the
  disabled logic still works; replaced session-32's More-tools toggle.
  (d) BEAUTY: hero header card w/ accent wash + gradient avatar, card sheen/
  shadows, accent-ticked section titles, sidebar active accent bar, button
  lift — ALL var-driven so every theme still works. (e) NEW editBuffer:
  switching tabs mid-edit never loses typing; commitEdit commits the union
  of all tabs; editField() auto-switches to the field's tab.
  VERIFIED LIVE on :5000 (browser): all 3 tabs render, menus open/close/Esc,
  cross-tab edit buffer keeps values, cancel discards, per-user menu items
  disable with no selection, ocean theme applies, zero console errors,
  users.json untouched (16:02 pre-session mtime).
⏳ **NEXT ACTION:** (a) Owner hard-refreshes the passport and eyeballs the
redesign (tabs, menus, Overview RD card); says what to tweak. (b) Owner does
Myles Dad's manual account creation same flow as Clarence/Anna: create the 2
AIOMetadata accounts (elfhosted Discover + viren070 Streaming) + 3rd
AIOStreams (elfhosted), Import Config File from setup_kits/myles-dad/configs/,
connect HIS Trakt on the Discover configure page, drop links in passport
(uuid backfill on request). (c) Round-22 app remainder still queued (unchanged
from session 31): #2 Library screen, emulator-verify #7 caption + #3 Discover
grid, gates → OTA alpha.58. (d) #5 passport per-addon toggles → 8-slot
profile regen (make_profiles still stale) → scp to Dreamhost.

## ⚠️ READ FIRST (session 31 — 2026-07-16 — ROUND 22 STARTED: questions answered with live data, #7 built; big builds queued)
**Round 22 logged below. This session answered the investigation items and
built #7; items #2 (Library), #3 (Home-row glitch), #4 (AIOMetadata
recommendations rollout), #5 (passport addon toggles) are QUEUED — build
next.** Findings (all verified live, read-only):
- **#7 BUILT (gates green: assembleDebug + testDebugUnitTest exit 0, NOT
  emulator-verified yet):** Search voice caption = owner's wording with the
  rail's own magnifier drawn INLINE (NavRail's SEARCH glyph extracted as
  public `SearchGlyph`); caption weighted so it wraps with the sidebar open.
  Ships with this round's alpha.58 — no OTA publish yet (round incomplete).
- **#1 re-root-caused (owner: blank page on EVERY browser/PC incl. never-
  used ones; normal trakt.tv login works fine):** the Edge popup was a side
  show (a Trakt PWA in Edge). Real cause is TRAKT-SIDE: verified by curl —
  AIOLists' client_id is valid and /oauth/authorize (both hosts) 200s into
  Trakt's NEW htmx auth page with signed ba_/PKCE params, but the post-login
  handoff back to the oob/PIN authorization page is what dies (blank page).
  Trakt overhauled auth ~May 2026 and broke third-party flows again in
  July (their forums); AIOLists (v1.2.7, repo dormant since Feb 2026) still
  uses the oob flow. NOT fixable client-side. Straw to grasp: the blank
  page's ADDRESS BAR (or view-source) may still carry `code=` — paste that
  into AIOLists if present. Mitigation: AIOLists is search-only for adam
  (see #6) and AIOMetadata's own Trakt connect evidently works (his
  "Recommended for Adam" catalogs exist) → per-user Trakt connects for the
  rollout are NOT blocked; AIOLists' Trakt can stay unconnected.
- **#6 ANSWERED with live manifest:** Adam's AIOLists serves ONLY 4
  search catalogs (Search Movies / Search Series / Merged Search / Anime
  Search; all required=search) + meta — ZERO Home/Discover rows (his Trakt
  was never connected inside AIOLists). Safe for him to test without it;
  other users (e.g. jody) may have real Trakt rows in theirs.
- **#5 ANSWERED (mechanics):** two surfaces. (a) SStreams boxes follow the
  HOSTED PROFILE JSON (savoy.click/setup) via ProfileSync on EVERY app
  launch (15-min throttle): installs in profile order, REMOVES managed
  addons dropped from the file, re-fetches manifests. So remove-AIOLists =
  edit his hosted profile JSON + reopen the app — NO data clearing (that
  nukes the profile link). (b) Stremio accounts: push_stremio_bundle.py
  rebuilds the whole collection from passport fields and SKIPS empty slots
  — blanking aiolists_manifest in the passport + rerunning REMOVES it, no
  error. The asked-for per-addon passport toggles + profile regen/upload =
  build item (note make_profiles is STALE — 1 meta + 1 stream — needs the
  8-slot canonical bundle first).
- **#4 groundwork:** owner's AIOMetadata Discover instance verified live —
  55 catalogs incl. NEW "Recommended for Adam" in movie/series/Trending
  types, required=[] (Home-eligible). Rollout = pull Adam's Discover config
  as the new template in make_user_configs.py, rename catalog per person
  ("Recommended for <First>"), blank trakt tokens (recs are personal — each
  user's own Trakt connect required for them to work). ⚠️ He also gets a
  "Trakt Recommendations" catalog from AIOStreams primary — once the
  AIOMetadata one is in, that's a duplicate row; suggest disabling it there.
- **#3 CORRECTED + FIXED (owner clarified: not a missing row — gray strips
  beside the artwork on Discover cards, absent on Home):** root cause =
  `GridCells.Adaptive` stretches every cell with the row's leftover width
  and grid cells measure items with EXACT width, so the Card surface grew
  wider than its poster → the card's gray background showed as a right-side
  strip. Home rows measure loosely → never affected. Fix:
  `GridCells.FixedSize(posterWidth)` (also now matches the skeleton grid's
  geometry). Gates green; NOT yet visually verified — do it in this round's
  emulator pass. No other Adaptive grids exist in the app (grepped).
- **#4 STEP 1 DONE (2026-07-16, owner said "go"):** make_user_configs.py now
  grafts the owner's recommendation arrangement into every Discover config —
  3 merged rows "Recommended for <First>" (movie/series/all=Trending) over
  the GENERIC trakt.recommendations.* sources, inserted at the template's
  old "Trakt Recommendations" position, raw sources disabled+mergedInto.
  Owner's own Discover was NOT made the wholesale template on purpose (his
  is sfw=false/includeAdult=true + adult rows + personal MAL prefs — the
  SFW base stays Rachael's). NEW `strip_personal_trakt()`: the
  custom.org_trakt_* catalogs (account-bound www.strem.io/trakt sourceUrl)
  had ridden along in EVERY generated kit since Round 18 — now stripped
  from all AIOMetadata builds. Regenerated all 45 files; verified all 9
  kits: personalized rows present/enabled, sfw=true/includeAdult=false, no
  org_trakt, no "Adam" strings, no template-owner key leaks (rpdb is the
  known all-10-shared key, exempt). MISSING.txt = the 2 standing
  connect-own-Trakt notes. Owner decision same message: AIOLists' Trakt
  connect is ABANDONED (Trakt-side breakage) — scrobbling = AIOMetadata
  Discover (traktWatchTracking=true, verified on owner's live instance) +
  the app's own check-in; AIOLists stays in the bundle for search only.
  **Addendum (owner): everyone family-safe EXCEPT Jacob — "NSFW Anime,
  replace my info with his"** → ANIME_USERS' Discover template is now the
  owner's Discover WHOLESALE (sfw=false, includeAdult=true, Top Adult
  Animation, "Recommended for Jacob", keys/tokens swapped/blanked, personal
  Trakt stripped); Jacob's Streaming was already Adam's anime. Regenerated +
  re-verified all 9 kits (jacob 188 catalogs NSFW, others 186 SFW, zero
  leaks). Kits live at ~/Documents/Claude/StremioSurfer/setup_kits/<slug>/
  configs/ (5 JSONs + MISSING.txt each).
**#8 SUPERSEDED (owner, same day — "basically a 360"):** a parallel session
("Trakt in the claude folder") FIXED AIOMetadata's Trakt auth → AIOLists is
back OUT; recs come from AIOMetadata everywhere, as the kits already do.
The #8 app auto-rename is MOOT (AIOMetadata rows are pre-named per person
and the app already tops "recommend" rows) — dropped from alpha.58. Owner
set up Clarence + Anna/Jay: BOTH instances each, VERIFIED complete this
session (read-only): correct addonNames, 186/99 catalogs, 3 enabled
"Recommended for <Name>" rows, sfw=true/includeAdult=false, Trakt CONNECTED
on Discover, traktWatchTracking true(discover)/false(streaming), no
org_trakt, zero template-owner leaks. Their 4 passport aiometadata slots
lacked uuid → backfilled from the manifest URLs via the :5000 API
(auto-.bak taken; re-GET verified). OPEN QUESTION for cutover: AIOLists'
final fate in the 8-slot bundle — owner says "doing away with aiolists";
it still provides merged search — confirm drop vs keep-for-search before
push_stremio_bundle runs.
⏳ **NEXT ACTION:** build the Round-22 remainder as alpha.58: (a) #2 Library
screen (rail entry; everything watched from local history + Trakt,
Stremio-like filters: Last Watched / A-Z / Z-A / Most Watched / Watched /
Not Watched), (b) emulator-verify #7's caption (ON state, sidebar open) AND
#3's Discover grid (no gray strips at 6 and 8 columns), then gates → OTA
publish alpha.58. Meanwhile owner continues per-person AIOMetadata setup
(remaining 7: Toby, Jody, Mike, Jacob, both Myles, Manuel Momma — same flow
as Clarence/Anna incl. connecting each person's Trakt on Discover); verify
each pair on request (uuid backfill included). Tooling
(StremioSurfer, no gates): (d) #4 make_user_configs.py template switch to
Adam's Discover + per-person recommendation rename; (e) #5 passport
per-addon toggles → regenerate profile JSON (8-slot bundle, fix stale
make_profiles) → scp to Dreamhost + optional push_stremio_bundle. Owner
actions were given in chat (Trakt PIN flow via Cancel, export nothing —
config pulled live).

## ⚠️ OWNER ROUND 22 (2026-07-16, session 31) — FULL LIST, logged before building
🚨 Log first, build second. Owner supplied 3 screenshots: (1) Chrome popup
"Open Microsoft Edge.app?" when clicking Connect to Trakt from AIOLists
(URL = api.trakt.tv/oauth/authorize…redirect_uri=urn:ietf…oob — the PIN
flow); (2) Stremio's Library filter bar (All / Last Watched / A-Z / Z-A /
Most Watched / Watched / Not Watched); (3) SStreams Discover showing the
"Trakt Recommendations" movie catalog rendering fine.
1. **Trakt connect from AIOLists** pops "Open Microsoft Edge.app?" (pic 1) —
   explain/fix. (Follow-on from the Round-21-era Trakt outage discussion.)
2. **App: Library section** — "a library section with better addons than in
   the second pic? but it would show everything you've watched" (a
   Stremio-Library-like screen: everything watched, with filters).
3. **APP GLITCH (pic 3):** owner clarified (follow-up message): "gray lines
   on the side of the artwork, like it's set to fit height instead of fill"
   — on Discover cards only, never on Home. (First read of "doesn't show in
   Home when it's showing in Discover" = the glitch itself doesn't show on
   Home.)
4. **AIOMetadata: Trakt recommendation catalogs in Movies, Series and
   Trending types.** Owner ALREADY set up his AIOMetadata 1st instance
   (Discover) the way he wants — "{First Name}'s Recommendations" — and asks:
   replicate that setup in everyone's configs "and/or when it's
   pushed/created if possible". (Q he raised en route: unhide the catalogs
   behind the merged catalogs, or make 2nd/3rd merged catalogs for
   movies/series separately — his own setup supersedes the question.)
5. **Addon management ask:** wanted to test without AIOLists; only removal
   path was in the app; refreshing lists/catalogs = close app + clear data
   (which re-syncs and brings AIOLists back). Asks: an on-computer portal to
   see/add/move/delete people's addons? Or does the passport already do it —
   "if I removed my AIOLists link from passport and ran the push to SStreams
   account (do I have that? lol), would it remove AIOLists? Or throw an
   error? Maybe a toggle on each addon link in the passport would work?"
6. **Q: what does AIOLists actually give?** which catalogs, anything
   important — owner notes he gets Trakt recommendations from AIOStreams
   1st instance at least.
7. **Search screen wording:** the voice-toggle caption — final wording:
   "Selecting <icon>Search from the left panel will activate the mic
   automatically. Keep this ON if you prefer using voice to search." (icon
   inline before "Search"); must fit even with the sidebar open.
8. **(Follow-up message) AIOLists recs via the app:** Trakt auth is now
   broken in AIOMetadata too; new connects impossible. But existing tokens
   still work — owner's own AIOMetadata Trakt is fine, and several users'
   AIOLists still carry live Trakt connections. Ask: surface AIOLists'
   "Recommended ..." rows AT THE TOP of Home (like the owner's AIOMetadata
   rows, right after Cinemeta), renamed "Recommended for <FirstName>", and
   present in Movies + Series as well as Trending types — "maybe the app
   can do some catalog sorting for us?". Owner also confirmed his own
   remaining manual step: create the 2 AIOMetadata accounts per person
   (elfhosted + viren070 hosts), import configs, drop links in passport.
   FINDINGS (2026-07-16): app ALREADY tops recommendation rows
   (HomeRowPrefs.withRecommendationsFirst matches title contains
   "recommend"), so AIOLists rec rows sort first today. AIOLists manifest
   inventory: ONLY 4 of 10 users' AIOLists serve rec rows (Myles Manuel,
   Myles Mobile, Manuel Momma both types; Anna/Jay movies-only); Jody/Mike/
   Jacob/Toby = search-only, Clarence/Rachael rows-but-no-recs — enabling
   recs there would need the broken Trakt auth, so those users' recs wait
   for the AIOMetadata rows to light up post-Trakt-fix. BUILD (alpha.58):
   auto display-rename rec rows to carry the profile's first name (append
   " for <First>"; manual renames in the row manager always win; name from
   ProfileSync's profileName — VERIFY what the hosted profiles carry as
   name first). Discover already lists AIOLists recs under Movie/Series
   types; a synthesized all/Trending rec catalog for AIOLists users =
   decided AGAINST for now (app-side merged catalogs are a big feature;
   AIOMetadata configs already provide all three types once Trakt works).

## ⚠️ READ FIRST (session 30 — 2026-07-15 — ROUND 21 (passport/AIOStreams round): RD-key mixup audited + fixed, English required everywhere, toggle_rd.py + RD On/Off buttons)
**All work in ~/Documents/Claude/StremioSurfer (NOT this repo; not a git repo).
Owner found ANOTHER USER'S Real-Debrid key inside his own live primary
AIOStreams config (and a foreign MediaFusion URL). Root cause: saved directly
on the AIOStreams site (likely wrong-tab save) — NOT our tooling
(push_aiostreams.py skips the owner by default and was never run at him; the
Round-20 Stremio push only swaps addon URLs, never config contents).**
- **Full read-only key sweep of every user × instance via GET /api/v1/user
  (Basic uuid:password):** everyone carries their own RD key. Owner fixed his
  primary himself; his backup/elfhosted were already right. Mike Miller's
  stale (expired) key + Clarence's wrong AIOStreams password → owner fixed
  both, re-verified clean this session.
- **Required Languages rollout (owner ask):** every PRIMARY already had
  required=[English]. Every BACKUP (weebs instance) config had required ↔
  preferred TRANSPOSED (template bug) — fixed on all 9 non-Rachael users to
  match adam (required=[English], preferred=[English,Original,Unknown]);
  verified by re-GET. Pre-write backups: StremioSurfer/config_backups/
  2026-07-15/. Owner's stray MediaFusion URL lived in "[BAK]AIOStreams" on
  the weebs instance — blanked. Rachael: required=[] on all 3 instances →
  owner gave explicit per-request permission ("Rachael's should be the same,
  English required") → set required=[English] on primary/backup/nightly,
  ONLY that field touched, verified by re-GET (her preferred already
  matched). Pre-write backups in config_backups/2026-07-15/.
- **⚠️ AIOStreams re-validates configs on save:** a stored deprecated preset
  ("USA TV") blocks ANY save with USER_INVALID_CONFIG. Writers must drop the
  named preset and retry (harmless — it's dead server-side). Also transient
  "manifest timed out" errors → retry. Both handled in toggle_rd.py.
- **NEW toggle_rd.py + passport "RD Off"/"RD On" buttons + /api/toggle_rd:**
  surgically flips services.realdebrid.enabled across a user's instances
  (GET-modify-PUT; catalogs/addons preserved). --enable refreshes the apiKey
  from the passport. Refuses Rachael without --include-rachael (UI can't pass
  it). Tested: dry-run + REAL disable on Myles Mobile (his RD expired
  07-13, Round-19 decision = Torbox/Debridio only) — verified on both his
  instances. Server restarted via autostart.sh (it is NOT under launchd;
  plain nohup process — kill + autostart.sh to restart).
- Owner Q&A answered: expired RD key left enabled = dead RD links that fail
  on click → disable is correct. Owner flipped precacheNextEpisode ON on his
  own account to test (it was off everywhere = AIOStreams default, never a
  deliberate choice).
⏳ **NEXT ACTION:** (a) Owner reloads the passport (hard refresh) and eyeballs
the new RD Off/RD On buttons in the Subscriptions row. (b) Owner says the
word on: Rachael required=[English] (needs per-request permission), and the
big rollout (push_aiostreams.py primary/backup — NOTE this REPLACES whole
configs incl. Mike's custom catalogs/links, by design streams-only + create
elfhosted; AIOMetadata accounts by hand + Import Config File). (c) Owner
reports how precache-next-episode feels. (d) alpha.57 retests from Round 20
still pending (episode-focus, search back-focus, CW first, poster fallback).

## ⚠️ READ FIRST (session 29 — 2026-07-15 — ROUND 20 COMPLETE: alpha.57 PUBLISHED OTA + Adam's Stremio account fixed + push_stremio_bundle.py)
**alpha.57 (versionCode 57) BUILT — gates green (378 tests, 0 failures; 5 new
corpus tests) + assembleRelease clean — PUBLISHED to the update server
(version.json readback 200, sstreams-57.apk). Boxes self-offer on next app
launch (⚠️ .117 AND .171 were on the network — both untouched; only
emulator-5554 was driven).** Commit `c31e5b2`, DECISIONS #64. Round 20 built:
- **#2 STREMIO ACCOUNT PUSH DONE (Adam's account, verified):** his Stremio
  now carries the canonical 8-addon bundle in Rachael's order — Cinemeta
  (was uninstalled), Local Files, AIOMeta discover+streaming, AIOStreams
  primary/backup/elfhosted (primary URL was stale — same uuid, new enc
  segment), AIOLists (was missing). One atomic addonCollectionSet (no
  empty-collection moment — the remove>add>remove>add worry is moot).
  Backup taken first (stremio-addons-backup-adam-2026-07-15.json).
  **NEW TOOL `push_stremio_bundle.py`** (StremioSurfer): --user/--all/
  --dry-run, per-user backups, skips missing slots, REFUSES Rachael
  without --include-rachael. Everyone-else rollout = one command, later.
- **#3 episode focus:** back from a stream list lands on the episode the
  user CLICKED (saveable openedVideoId beats resumeVideoId as entry anchor).
- **#5 Search back-focus:** back from Details restores the opened result
  card (openedRowKey + gated probe), not the search bar. Standing rule
  honored on all browse surfaces now (Home/Discover already had it).
- **#8 Continue Watching above EVERYTHING on Home** — pinnedRowCount and
  the round-14 pinned/CW split deleted; recs still sort first among
  catalog rows.
- **#4 Discover covers:** failed poster → backdrop → always-visible title
  (no more anonymous dark boxes). Upstream cause noted: owner's rpdb key
  is free-tier (t0-free-rpdb) which rate-limits artwork.
- **#6+#7 Settings:** "Reset this TV to Factory Defaults" (entry+dialog);
  Poster size = NEW settings/poster-size SCREEN with a live miniature
  poster wall that re-lays per focused option (emulator-verified, looks
  great); "Search by talking" REMOVED from Settings → pill on the Search
  screen itself, and the mic pill physically swaps sides of the text box
  with the toggle; "Skip intros automatically" + "Play the next episode
  automatically" (+ amber BETA chips); Auto-play first stream wears an
  accent RECOMMENDED ON chip.
- **#9 bug-check:** StreamLabelCorpusTest — ~30 real-world label shapes
  (pennants/bracket listings/HDR tags/codec zoo/emoji/CJK/garbage) swept
  through every StreamCascade parser entry point, plus targeted
  expectations. All green.
- **#1 rollout answer given to owner** (see Round-19 notes + summary):
  AIOStreams push/create via push_aiostreams.py on his word; AIOMetadata
  accounts by hand + Import Config File; then push_stremio_bundle.py --all.
- ⚠️ **EMULATOR HOST MELTDOWN (90 min lost — do not chase as app bug):**
  GPU context died (black screencaps, launcher ANR-killed too), then QEMU
  CPU threads hung outright. ALL of today's ANRs were the host. Fallback
  that worked: cold-restart emulator with `-gpu swiftshader_indirect`
  (slow but renders). Visual drive passed: Home, Settings list, Poster
  size + live preview, Anime drawer badges. NOT visually verified (code +
  gates only): Search toggle/mic-swap, episode-focus fix, CW order (no
  watch history on emulator), poster fallback.
⏳ **NEXT ACTION:** (a) Boxes self-offer alpha.57 — owner presses Update
(dialog focuses Cancel, Update is LEFT). (b) Owner retests: episode → back
lands on the SAME episode; search → click card → back lands on the card;
Continue Watching is Home's first row; Settings → Poster size screen;
Search screen's "Talk to search" pill swaps the mic's side; Discover covers
that used to be blank now show backdrop or title. (c) On owner's word:
push_aiostreams.py (push primary/backup + create elfhosted), owner makes
AIOMetadata accounts + imports configs, then push_stremio_bundle.py --all
(Rachael excluded by default). (d) Consider upgrading rpdb from t0-free
(rate-limited artwork = the blank covers). (e) Backlog: 9s bias knob, #16
skins.

## ⚠️ OWNER ROUND 20 (2026-07-15, session 29) — FULL LIST, logged before building
🚨 Log first, build second. Owner also confirmed: **adding English to "Required
Languages" in AIOStreams on his account fixed Naruto auto-pick** (Round-19 #7
follow-up — config-side remedy works alongside the alpha.56 parser fix).
1. **Q: "What do I need to do (again) to get everyone set up for us to push
   the addon configuration to them?"** → answer from the Round-19 rollout
   notes (push_aiostreams.py for AIOStreams; AIOMetadata accounts by hand +
   Import Config File).
2. **Stremio account addon push:** owner noticed HIS Stremio account is
   missing Cinemeta (he uninstalled it a while back) and AIOLists, and still
   carries the OLD AIOStreams instances. Ask: push the correct addon set to
   Stremio accounts via API — remove current addons while adding the correct
   ones. May need add-new-first (or remove>add>remove>add) in case the
   account rejects an empty collection. **Try on Adam's account first.**
   (Rachael's remains untouchable without per-request permission.)
3. **APP BUG: episode selection loses place** — click a series episode, back
   out → the selection lands 3-4 episodes EARLIER than the one clicked.
4. **APP BUG: some movie covers don't load on Discover.**
5. **APP BUG/RULE: Search → click a result card → BACK returns focus to the
   search bar, not the card.** New standing rule: when backing out to a
   screen the user was already on (since program open), ALWAYS return focus
   to where the pointer last was.
6. **Rename "Reset this TV" → "Reset this TV to Factory Defaults".**
7. **Settings sub-screens with visuals:** move confusing settings into their
   own "screen" (like Home screen rows) with a photo/illustration showing
   what the toggle does. Owner offered to upload ON/OFF photos if we can't
   render the difference. Per-setting guidance:
   - Home screen rows: could have it. Poster size: same.
   - Hide watched shows: doesn't need one. Menu sounds: doesn't.
   - "Search by talking": REMOVE from Settings; surface it above the search
     bar somewhere, with wording/illustration showing the mic swaps sides.
   - Auto play first stream: sub-screen, or just say "RECOMMENDED ON".
   - "Skip intros by themselves" → rename "Skip intros automatically" +
     "Beta" warning; same treatment for "Play the next episode by itself"
     (automatic wording + Beta warning).
8. **Move Continue Watching ABOVE Trakt Recommendations on Home.**
9. **Bug check pass:** owner has no testers besides himself — do whatever
   automated bug checking possible (e.g. exercising visual tags/encodings).
10. Tooling license: "Use any extensions/skills/plugins/etc you'd like."

## ⚠️ READ FIRST (session 28 cont. — 2026-07-12 — ROUND 19 COMPLETE: alpha.56 PUBLISHED OTA + passport sharing marks, all-instance configs)
**alpha.56 (versionCode 56) BUILT — gates green (374 tests, 0 failures; 5 new)
+ assembleRelease clean — emulator smoke passed (installed, MainActivity
RESUMED, crash buffer empty, versionName 0.3.0-alpha.56) — PUBLISHED to the
update server (verify readback 200). Boxes self-offer on next app launch
(⚠️ .117 was on the network this session — untouched; only emulator-5554 was
driven).** Commit `cb784c9`. Everything in Round 19 built:
- **#7 Italian auto-pick ROOT-CAUSED + FIXED — NOT a race.** The auto-pick
  already waits for every addon (bestPlayableWhenSettled returns Waiting
  while any group loads — owner's 5s theory disproven, no waiting change
  needed). Real cause: the Italian WIKIRIP Naruto releases (S1 pack + per-
  episode twins) carry AIOStreams' pennant `⛿ ᴇɴ·ᴊᴀ` even though their own
  filename says `[EAC3 JPN ITA Sub ITA ENG]` (audio JPN+ITA, subs ITA+ENG) —
  AIOSTREAMS' OWN LABEL LIES, our parser trusted it, and 1080p-cached-HEVC
  beat the true English duals (480p SeaDex / 720p iVy). Fix: an explicit
  audio/sub language listing in the release FILENAME (bracket groups or
  dotted names, with sub-marker/2-langs/audio-codec anchors so title words
  like "The.Spa" never match) now overrides the pennant in both directions.
  Verified against the live S1E13/14/15 labels. **Owner retest: Naruto →
  auto-pick should land on an English dual (SeaDex/iVy), never WIKIRIP.**
- **#8 "Try a different stream" now means DIFFERENT:** the manual button
  skips candidates whose release pattern matches the abandoned stream
  (StreamAlternatives.advance(preferDifferentFrom), Jaccard ≥ 0.5 = same
  family — the WIKIRIP pack vs its episode twin measures exactly 0.5),
  falling back to a similar one only when nothing else remains. The quiet
  on-error auto-skip keeps the plain ranked order. (The #7 fix also demotes
  both WIKIRIPs below every English stream, so the glitched pair now sits
  at the list's bottom anyway.)
- **#1 Passport sidebar sharing marks:** colored shape glyphs (triangle/
  circle/square/diamond/star × 7 colors) next to sidebar names for every
  share group (same RD key, debridio pairs, Myles' torbox, shared gmail/
  gemini); values shared by >half the users (rpdb, group torbox) are
  excluded as noise. Collapsible "Sharing ▸" legend bar above the client
  list names each group's members. Verified in the browser on :5000.
- **#4 Gmail field:** new Account row in the passport; gmails written via
  the server API for Toby/Myles Manuel/Myles Mobile/Jody/Jacob/Clarence/
  Anna-Jay (Rachael untouched — hers already her email). Myles Manuel +
  Mobile share one gmail → the share-glyph + "shared with" notes show it.
- **#2 Myles Mobile no-RD:** billing.rd_not_needed=yes set via API;
  rd_reminders.py skips ALL RD nags for flagged users (dry-run verified:
  his expired-RD alert is gone); his 3 AIOStreams configs ship with
  Real-Debrid DISABLED, Torbox+Debridio carrying streams.
- **#3+#5 config maker now covers ALL 5 instances** (aiostreams-primary/
  backup/elfhosted + aiometadata-discover/streaming): Rachael's live
  primary/backup configs pulled as SFW templates too. Rerun with the new
  gemini keys → 45 files, MISSING down to just "connect their own Trakt"
  notes. Myles Dad dropped from targets (owner: off the list; passport
  entry + stale configs dir removed). Jacob's streaming = Adam's anime.
  Leak scan across all 45: CLEAN.
- **Rollout answers for the owner:** everyone already HAS AIOStreams #1+#2
  accounts (only #3 elfhosted + both AIOMetadata are new); templates indeed
  don't cross instances — that's why there's one config file PER instance
  per person, keys pre-filled. push_aiostreams.py can PUSH the primary/
  backup configs to existing accounts and CREATE the missing elfhosted ones
  via API (earlier sessions did exactly that) — offered, awaiting owner
  go-ahead. AIOMetadata accounts have no create-API confirmed yet: make
  each account in the UI, then Import Config File.
⏳ **NEXT ACTION:** (a) Boxes self-offer alpha.56 — owner presses Update
(dialog focuses Cancel, Update is LEFT). (b) Owner retests: Naruto auto-pick
= English dual audio; "Try a different stream" from a glitched stream lands
on a genuinely different release. (c) Owner eyeballs the passport sidebar
glyphs + "Sharing" legend, Gmail rows. (d) On owner's word: push configs to
existing AIOStreams accounts / create elfhosted ones via push_aiostreams.py;
AIOMetadata accounts by hand + import. (e) Backlog: 9s bias knob, #16 skins.

## ⚠️ OWNER ROUND 19 (2026-07-12, session 28) — logged before building
🚨 Log first, build second. Owner confirmed Round-18 checklist items 1-4 good.
1. **Sidebar sharing marks (passport UI):** color-coded dots/shapes next to
   names showing who shares what with whom (e.g. same green triangle next to
   Myles + Myles Mobile if they share Real-Debrid). "If it's too much or too
   cluttered make it expandable."
2. **Myles Mobile does NOT need RD renewed** ("second account — Torbox and
   debridio should be enough"): stop the expired-RD nags for him; his configs
   should lean on Torbox+Debridio, not RD.
3. **Gemini keys are ALL in the passport now** → re-run the config maker.
4. **Gmails created for almost everyone** (store in passport): Toby=
   tobysstreams, Myles Manuel + Myles Mobile = mylessstreams, Jody=
   jodysstreams, Jacob=christmasbabyjesus12, Clarence=bopsstreams,
   Anna/Jay=adamtpelectric (all @gmail.com); passwords = same as passport.
   Rachael's is already her email field (no write — standing rule).
   **Myles Dad: "take him off the list, let's not worry about him"** → drop
   him from config generation + rollout lists (passport entry stays — it
   holds his contract note + keys).
5. **Rollout questions answered + asked for:** users DO have AIOStreams #1+#2
   accounts already; owner wants configs for ALL 5 instances per person
   (anime + non-anime variants) since templates don't cross instances and
   he'll redo the existing ones anyway. (Earlier sessions created AIOStreams
   accounts via API — offer to push/create via API instead of manual UI.)
6. Round-18 oddity fixed by owner: jody's Trakt link was Myles' — also
   fixed inside jody's AIOLists (owner updated that link).
7. **APP BUG (alpha.55): auto-pick STILL chooses a no-English-audio stream**
   (the Italian Naruto one; it has English SUBS but not audio). Owner's
   theory: the app decides before all addons respond (AIOStreams capped at
   5s; waiting up to 5s "I guess not horrible"). Investigate the race AND
   the ranking for this exact episode.
8. **APP BUG: "Try a different stream" landed on the colorful glitched
   stream** (the blocky/corrupt-decode one) — the fallback should avoid or
   fix that (software decoder path exists under Expert mode).

## ⚠️ READ FIRST (session 28 — 2026-07-12 — ROUND 18 COMPLETE: passport round finished across two sessions)
**The prior session (cut off on usage limits) had already built more than it
logged: passport UI items 1/6/8 are live in StremioSurfer/passport.html
(Addon Links trimmed to AIOLists; RD days-left chips + billing.owe_half_year
field; amber "shared with <name>" notes on duplicate values), item 7's
rd_reminders.py is wired into launchd (com.adamsavoy.rd-reminders: 10:00 /
12:30 / 17:00; the 17:00 run fired a real "Myles Mobile expires TODAY" alert),
and item 9's Myles Dad is re-added to the live passport (RD key active to
2026-10-17, Myles' torbox, notes list what he still needs).** This session
finished the rest — all work in ~/Documents/Claude/StremioSurfer (not this
repo), nothing pushed to any user account, zero writes to Rachael's anything:
- **Item 2/5 VERIFIED clean:** Mike + Anna/Jay + Clarence share ONE active RD
  key (expiry 2026-12-07 on all three — the owner's UI edit took). Jacob/
  Toby/Clarence/Mike all have debridio + RD set. Debridio sharing: Rachael+
  Clarence+Jacob on one key, Mike+Toby on another. rd_checked_at is fresh
  everywhere now (the reminder run refreshes it — the stale-checker audit
  finding is dead).
- **Items 3+4 BUILT — `make_user_configs.py`:** pulls the SFW templates LIVE
  (read-only: Rachael's Discover 186 cats / Streaming 99 / elfhosted
  AIOStreams; Adam's anime Streaming 96 for Jacob), swaps in each user's own
  keys, blanks per-account fields (trakt/simkl tokens, sessionId), sweeps for
  template-owner value leaks (shared keys exempt). Wrote 30 import-ready
  files: setup_kits/<slug>/configs/{aiometadata-discover,aiometadata-
  streaming,aiostreams-elfhosted}.json + MISSING.txt for all 10 non-template
  users. Verified: fingerprint spot-checks, cross-user leak scan CLEAN, all
  wrappers match AIOMetadata's import format / AIOStreams' template format.
  `--report` prints the per-user missing-components list (item 3): all 9
  regular users lack exactly the 2 AIOMetadata accounts + 3rd AIOStreams +
  gemini key + their own Trakt connect; Myles Dad lacks everything (by
  design, Stremio-only).
- **Item 10 BUILT — `make_master_csv.py`:** every non-Rachael Chrome export
  is a ~1,100-row synced copy of Adam's vault + a few person rows. Wrote
  ~/Documents/Chrome/MASTER_passport_logins.csv (141 service logins,
  person-attributed via passport UUID/email/name matching; 19 legacy rows
  left "?") and Adam_IMPORT_missing.csv (31 rows others have that Adam's
  vault lacks — incl. Rachael's whole 10-row kit) for chrome://password-
  manager import. Oddity flagged: jody's export holds a trakt.tv login
  under Myles@savoy.solutions; "Jamie" accounts exist but aren't in the
  passport.
⏳ **NEXT ACTION:** (a) Owner reloads the passport UI and eyeballs items
1/6/8 (AIOLists-only links, days-left + owe-½-yr chips, shared-with notes).
(b) Owner imports Adam_IMPORT_missing.csv into his Chrome, then archives
the per-person exports. (c) When Gemini keys arrive: paste into passport →
rerun `python3 make_user_configs.py` (10s) → fresh configs. (d) Rollout per
endgame plan: create the 2 AIOMetadata + 1 elfhosted AIOStreams accounts per
user with the pre-filled config files, then per-person profiles. (e) Myles
Mobile's RD EXPIRED 2026-07-13 — renew or let lapse (Myles Manuel's is
active to 2027-05; same person?). (f) App backlog unchanged (alpha.55
retests, 9s bias knob, user skins).

## ⚠️ OWNER ROUND 18 (2026-07-12, session 27 cont.) — PASSPORT ROUND, logged before building
🚨 All passport/StremioSurfer work (~/Documents/Claude/StremioSurfer, live
server :5000 authoritative). NO writes to Rachael's accounts/configs; her
passport entry is the TEMPLATE, not a target. Owner's asks:
1. Passport UI: remove the "Addon links" section EXCEPT the AIOLists link.
2. Mike + Anna/Jay + Clarence share one RD key ("they barely ever use it") —
   owner already set Mike's via the passport UI (screenshot); verify.
3. Target setups: EVERYONE = Rachael's "Movies & TV SFW" bundle; Jacob =
   Anime like Adam's. Generate a per-user list of missing components for the
   7-addon bundle.
4. Per-user CONFIG FILES with services + API keys already in place (for
   Rachael the owner had to type keys in by hand after getting the config).
5. Owner is getting Gemini APIs for everyone; AIOLists manifests already in
   passport get reused. Owner just shared debridio + RD APIs with Jacob,
   Toby, Clarence, Mike (via passport UI — screenshot).
6. Passport shows DAYS LEFT on each RD subscription + a per-user "owe half
   year" yes/no field (RD now only sells half-years; two half-years do NOT
   stack — owner tested).
7. Reminders: 1 week before + 1 day before an RD expiry (around 12–1pm), and
   day-of ~3 pings; 1-week one may be turned off later.
8. Duplicate values in the passport get highlighted with "shared with <name>"
   above the field (sharing is fine, visibility is the point).
9. Re-add "Myles Dad" to the passport (stub exists in
   removed-users-2026-07-07.json, email mylesdad@savoy.solutions): honor his
   contract; share Myles' torbox; owner supplied his RD API; needs gemini
   later; Stremio-only for now. List what else he needs.
10. Chrome CSVs (~/Documents/Chrome/): master CSV of everyone's
    passport-related service logins; add to Adam anything he's missing that
    others have.

## ⚠️ READ FIRST (session 27 — 2026-07-12 — alpha.55 PUBLISHED OTA: Round 17 built — per-type skip bias, mockup skip/next UI, series amber, lit mic)
**alpha.55 (versionCode 55) BUILT — gates green (assembleDebug +
testDebugUnitTest: 369 tests, 0 failures; assembleRelease clean) — emulator
smoke passed (installed, MainActivity RESUMED, crash buffer empty, Home
renders) — and PUBLISHED to the update server (version.json readback
verified). Neither box driven: BOTH self-offer alpha.55 on next app launch
(one OK each — dialog focuses Cancel, Update is LEFT).** DECISIONS #63.
Everything in Round 17 built:
- **Q answered + fixed — intro vs next-episode bias:** the old 9s bias
  trimmed every window's END; that's right for intros (skip seeks there) but
  did NOTHING for the credits' early-appearance problem — the credits START
  was raw community data. `withSkipBias` now: intro end −9s (unchanged),
  credits start +10s (prompt may appear late, never early over the ending).
- **Skip/next UI = the owner's mockup:** Skip Intro is a near-black capsule
  with » that FADES after 20s (OK intercept stands down with it); credits
  countdown is the shared NextEpisodeCard (thumbnail, "Up next"/episode
  line, draining ring, accent "Play now [OK]" + see-through "Cancel [BACK]");
  the autoplay Up Next countdown wears the same card. **The control bar now
  PUSHES the skip/next corner above itself** (measured bar height, animated
  padding) — no more covering the Next Episode button on DPAD-down.
- **Series-level watched (amber):** poster tiles show a 3dp SeriesAmber
  bottom bar + "N of M episodes" in the focus reveal — series completion in
  a DIFFERENT color from the blue episode ring. Totals cache into a
  DataStore when a series' Details opens (`SeriesEpisodeCounts`); watched
  counts derive from progress rows. ⚠️ Not emulator-verifiable (no watch
  history without firing the owner's Trakt check-in).
- **Search mic (emulator-verified before/after):** voice entry now lands
  focus ON the mic pill (was stuck on the rail); the pill fills solid accent
  the instant listening starts and dims back when the recognizer returns.
- **Passport audited read-only** (live :5000 users.json, 11 users) — clean
  overall; findings for the owner: (1) Anna/Jay + Clarence share the same
  email AND the same Real-Debrid key (both expired/free) — duplicate person
  or needs its own creds; (2) every rd_checked_at is 2026-06-10 (stale a
  month) and Mike Miller reads "active" but his RD expiration (2026-07-04)
  has since passed — re-run check_subscriptions.py; (3) only adam + Rachael
  have the new 2-instance AIOMetadata (discover+streaming) — the other 9
  users' aiometadata is EMPTY, so per-person profile generation à la the
  endgame plan can't run for them yet; (4) Rachael: subscription=null and 3
  of 4 addon URL slots empty (aiolists only) — NO edits made (standing
  rule); (5) Myles Mobile's RD is expired while Myles Manuel's is active
  (same person? maybe reuse); shared Torbox key everywhere except Myles
  personal — matches the known tier design.
⏳ **NEXT ACTION:** (a) Boxes self-offer alpha.55 on next app open — owner
presses Update. (b) Owner retests on alpha.55: next-episode prompt should no
longer beat the credits (was ~10s early); Skip Intro pill (new look) fades
~20s in; DPAD-down during credits lifts the Up next card above the control
bar; Search mic lights while listening; posters of shows he's watched gain
the amber series bar after opening their Details once. (c) Owner decides on
the passport findings above (esp. Anna/Jay-vs-Clarence and the stale RD
checker) before pushing profiles to other users' Stremio accounts. (d)
Backlog: 9s intro-bias knob if it overshoots elsewhere, player UI beauty
pass (partly delivered via mockup), #16 user skins (future).

## ⚠️ OWNER ROUND 17 (2026-07-12, session 27) — FULL LIST, logged before building
🚨 Log first, build second. Owner supplied a player-UI mockup image (Skip Intro
pill + "Up next" card) to match.
1. **Voice search focus + lit mic:** "when you search, the pointer/mouse/
   selection stays in the sidebar over the magnifying glass. please make it go
   to the magnifying glass [the mic button on the Search screen] and make it
   light up instantly and darken back to normal when it's done listening."
2. **Q: intro vs next-episode bias.** "The intro finally skipped to right
   before the end of the intro (good) but the next episode came about 10
   seconds early on this episode." → the 9s early-end bias trims every
   window's END; the credits window START was never biased — raw community
   data. Fix: bias the credits start LATER (safe direction: a late prompt is
   invisible, an early one covers the ending).
3. **Series-level watched display on cards:** "a different colored display for
   how much the user has watched out of ALL of the series — not just the
   episode, on the cards/art."
4. **Skip Intro / Next Episode UI per the mockup:** pill with »; "Up next"
   card with Play now + Cancel + countdown ring; control bar ("scrobble UI")
   must PUSH the skip/next UI UP when raised instead of covering it (owner hit
   this last night: tapped down, the bar covered the Next Episode button).
   Mockup notes: Skip Intro fades after ~20s, no cancel needed; second
   button (Cancel) a little more see-through. Owner likes the design overall
   (knows "Try a different stream" reads oddly there — it predates the pill).
5. **"Continue doing what you do best, making it look and feel great"** —
   general polish license around these areas.
6. **Passport audit before rollout:** "We can probably start pushing these
   changes to the other users' Stremio accounts — is there any info I need to
   look over in the passport (duplicated info, wrong info, etc)?" (Read-only
   audit; NO writes to Rachael's anything without per-request permission.)

## ⚠️ READ FIRST (session 26 — 2026-07-12 — alpha.54 PUBLISHED OTA: logo redo — banner/icon scaled into the launcher safe zone)
**alpha.54 (versionCode 54) BUILT — gates green (assembleDebug +
testDebugUnitTest, exit 0) + assembleRelease clean — and PUBLISHED to the
update server. Neither box driven this session: BOTH will offer alpha.54 on
their next app launch (one OK each; .117 skips straight from .52, .196 from
.53 — versionCode compare, nothing cumulative to worry about).**
Owner ask: "logo redo, it's way too large." The logo = the TV-launcher
banner + launcher icon (no other logo surface exists in the app — Home's
header is plain text). All three drawables drew their art near full-bleed:
- `tv_banner_streams.xml` (owner brand): wordmark ink spanned x8–312 of 320
  (2.5% margins). Now a `<group>` scales it 0.72 about the ink centre →
  ~68% banner width, centred, Netflix-like breathing room.
- `tv_banner.xml` (repo-neutral): the S filled 91% of banner height → 0.55
  scale, ~50% height, centred.
- `ic_launcher.xml`: S eased 0.85 → ~57% of the tile (was 67%).
Same paths, no redraw — comments in each file say to redraw rather than
stack transforms if the art ever changes. Verified: SVG before/after render
+ ON THE EMULATOR'S LAUNCHER Apps row (apps-view screenshot shows the new
proportioned tile).
⚠️ Emulator gotcha (cost 10 min): the TV launcher CACHES banners + labels —
after installing, the Home favorites row still showed the old tile/label;
`pm clear com.google.android.tvlauncher` busted it. If a real box still
shows the old banner after updating, a box reboot refreshes the launcher
row (don't chase it as an app bug).
⏳ **NEXT ACTION:** (a) Boxes self-offer alpha.54 on next app open — owner
presses Update (dialog focuses Cancel; Update is LEFT). This carries all of
Round 16 to .117 too. (b) Owner's Round-16 retests (below) now happen on
alpha.54: Naruto auto-pick = English dual audio; skip ~9s earlier; pause
works with Skip button up; Settings Anime drawer; Search mic re-fires.
(c) After updating, owner eyeballs the new launcher banner size (reboot the
box if the launcher still shows the old one). (d) Backlog unchanged: 9s
skip bias knob if it overshoots (Round-17 candidate), player UI beauty
pass (owner wish, future), #16 user skins (future).

## ⚠️ READ FIRST (session 25 cont. 3 — 2026-07-12 — alpha.53 BUILT + OTA'd to .196: Round 16 fully built — ⚠️ .117 NOT updated on purpose)
**alpha.53 (versionCode 53) BUILT — gates green (364 tests, 0 failures; both
documented HomeViewModelTest flakes cleared on rerun) + assembleRelease clean
+ emulator-verified by screenshot. Published to the update server and
DELIVERED OVER THE AIR to .196 (verified 0.3.0-alpha.53, app resumed, no
crashes). ⚠️ .117 deliberately untouched (owner watching a movie) — it's on
alpha.52 and will OFFER alpha.53 itself on its next app launch (one OK).**
DECISIONS #62. Every Round-16 item built:
- **#6 Japanese-stream bug FIXED (the big one):** AIOStreams' current labels
  mark languages as `⛿` + Unicode small-caps (`⛿ ᴇɴ · ᴊᴀ`); the alpha.37
  parser looked for "Audio:" and matched NOTHING → everything ranked English
  → resolution won → 1080p ᴊᴀ-only beat the real ᴇɴ·ᴊᴀ duals. Parser now
  normalizes small caps, reads the pennant, and ignores the sub(…) half
  (English SUBS ≠ English audio). Verified against the live labels for
  Naruto S1E12 on all 3 instances. **Owner retest: Naruto should now
  auto-pick the English dual-audio release.**
- **Skip re-tuning:** end bias 9s (was 2s); BOTH auto-skips now DEFAULT OFF;
  auto-advance = 10s grace + 8s countdown; skip button no longer hijacks OK
  while the control bar is up (pause works during intros again).
- **Settings:** ANIME is a collapsed drawer BELOW PLAYBACK on a darker slab
  (drawn caret flips open; Episode numbers inside); entry text one size up.
- **Search:** every deliberate click into Search re-fires the mic (new
  VoiceSearchTrigger — rail/pill bumps a counter; BACK-returns don't);
  keyboard hidden before + after the voice overlay and the field skips its
  focus-grab when a voice fire is pending → no lingering keyboard.
- **Player:** "Software video" pill gone; "Try a different stream" leftmost
  with an ACCENT label (reads primary without saying "try first").
- Q&A answered: skip is anime-only (AniSkip = community MAL-keyed DB; no
  equivalent data exists for general TV).
⏳ **NEXT ACTION:** (a) **Owner tests on .196 tonight** (it has alpha.53):
Naruto → auto-pick should be English dual-audio; skip lands ~9s earlier;
pause works while the Skip button shows; Settings → Anime drawer + bigger
text; Search mic re-fires on every magnifying-glass click, no stuck
keyboard. (b) **.117 self-offers alpha.53 on next app open** — just press
Update. (c) If 9s early-bias overshoots on other anime, make the bias a
per-show or Settings knob (logged as a possible Round-17 item). (d) Player
UI beauty pass = owner wish, future round.

## ⚠️ OWNER ROUND 16 (2026-07-12, session 25 cont. 3) — FULL LIST, logged before building
🚨 Log first, build second. 🚨 **TEST ONLY on the emulator + .196 — do NOT
touch .117 (owner is watching something on it).** OTA publish is fine (the
prompt only appears on .117's next app launch — one OK, owner's choice).
1. **Settings: ANIME section moves BELOW PLAYBACK**, ships COLLAPSED by
   default, with a darker grouped background — "like an app drawer that
   slides out" / anything that shows the rows belong together.
2. **Settings text bigger** — titles at least, maybe everything.
3. **Voice-first search: the keyboard pops open during the mic flow and
   stays open after the result lands.** Close it or never show it.
4. **Voice-first search only fires ONCE:** search by mic, go Home, click the
   magnifying glass again → keyboard, no mic. Every deliberate click into
   Search should start the mic again.
5. Q answered in-chat: skip is ANIME-ONLY (AniSkip = community MAL-keyed DB;
   no such data exists for general TV).
6. **Naruto S1E12: first auto-picked stream was JAPANESE audio, only other
   option Italian** ("I thought we fixed that" — alpha.37 English-audio-first
   ranking). Investigate the actual AIOStreams labels for that episode; the
   audio parse or the ranking is missing something (or the results are just
   thin for old anime and BOTH streams were non-English).
7. **Auto-skip defaults BOTH OFF** ("skip intros by themselves" already off —
   also default off "play the next episode by itself").
8. **Auto-advance timing:** the next-episode countdown should start 10
   seconds AFTER the credits window opens, and count down from 8 (was 5).
9. **Skip button hijacks OK:** while "Skip Intro" is on-screen NOTHING else
   is selectable — every OK skips. Only intercept when the control bar is
   hidden; with the bar up, OK must act on the focused control.
10. **Intro skip still lands late: subtract 7 MORE seconds** (total early
    bias 2s → 9s).
11. **Player "Having trouble?" panel:** REMOVE the "Software video" toggle
    pill; move "Try a different stream" to the LEFT and make it read as the
    first thing to try (visual emphasis, not literal words). Owner wishes the
    player UI looked better overall (bigger redesign = future).
12. Process reminder: never build partway and stop without logging.
Priorities: log → #6 investigation → all app items as alpha.53 → gates →
emulator → OTA publish → drive .196 ONLY → checkpoint.

## ⚠️ READ FIRST (session 25 cont. 2 — 2026-07-12 — alpha.52 LIVE ON BOTH BOXES via OTA: Round 15 CLOSED — Naruto photos root-caused + fallback, voice-first search, Settings rework)
**alpha.52 (versionCode 52) BUILT — gates green (362 tests, 0 failures; the
known HomeViewModelTest flake cleared on rerun) + assembleRelease clean +
emulator-verified by screenshot — and DELIVERED TO BOTH BOXES OVER THE AIR
(publish → box prompt → confirm; .196 and .117 both verified 0.3.0-alpha.52,
apps resumed, zero FATALs).** DECISIONS #61. Round 15 is now fully closed:
- **#8 Naruto photos — ROOT-CAUSED, ecosystem gap, graceful fallback built.**
  Every meta source (Cinemeta + owner's AIOMetadata) points Naruto episode
  stills at metahub, and metahub simply has no images past ~absolute ep 52
  (S2+ = HTTP 404; probed). Same on Stremio — nothing config-side can fix it.
  The app now swaps a failed still to the SHOW'S BACKDROP (EpisodeRow onError
  retry), so no show ever renders blank gray boxes again.
- **#9 voice-first search:** mic button now LEFT of the text field; opening
  Search fresh auto-starts the mic (say the title, no typing) — once per
  arrival, only when blank, only if a recognizer exists. Settings → SEARCH →
  "Search by talking" (default ON) turns it off.
- **#10 Settings rework:** flat captioned sections, visual-first — HOW THINGS
  LOOK / SOUND / SEARCH / ANIME (Episode numbers moved in) / PLAYBACK / THIS
  TV. Home gained the same "View ⚙" pill as Discover (poster size in place,
  live). Player → Expert mode. "Connect this TV" REMOVED (twin of "Reset this
  TV"). NEW "Reset settings to default" just before Expert — resets view +
  playback prefs, keeps profile/addons/history (no sign-out). Discover's
  hide-watched also surfaced in Settings.
- ⚠️ Emulator gotchas hit this session: uiautomator dump can CRASH ITSELF on
  Compose trees (an NPE in the tool — check `Process: dev.openstream.tv`
  before assuming an app crash); repeated BACK presses trip the
  press-BACK-again-to-exit flow (drive Settings via the header pills instead).
⏳ **NEXT ACTION:** (a) **Owner eyeballs alpha.52** (both boxes already have
it): Search — mic is left of the box and opening Search starts listening
right away (toggle: Settings → SEARCH); Settings — sectioned layout, Home
"View ⚙" pill, Reset settings entry; Naruto — S2+ episode rows show the
show's backdrop instead of blank boxes (community has no per-episode photos
for those — that part is unfixable upstream). (b) Rachael's box leaves today:
DONE — it's on alpha.52 with the OTA updater; future fixes just get
published. (c) Backlog now: #16 user skins (future), any new owner rounds.

## ⚠️ READ FIRST (session 25 — 2026-07-11 — alpha.50 OTA UPDATER + alpha.51 skip cluster: BOTH LIVE ON BOTH BOXES, alpha.51 delivered over the internet)
**The two headline items are DONE and deployed:**
- **alpha.50 — in-app OTA updater** (DECISIONS #59). The app checks
  `savoy.click/setup/app/version.json` on every launch; newer build → "Streams
  has an update → Update now" → Android's one-OK confirm. Publish a release:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease && tools/publish_update.sh`
  ⚠️ **Signing = THIS Mac's `~/.android/debug.keystore`** (backed up to
  `~/Documents/Claude/stremio-automation/debug.keystore.backup-2026-07-11`) —
  OTA updates only install when signatures match; never build a release
  elsewhere. Both boxes have the install-permission appops grant. Dreamhost
  access log = ground truth for box checks/downloads.
- **alpha.51 — Round-15 skip cluster** (DECISIONS #60): skip windows end 2s
  early (his streams ran ahead of community timestamps); credits button =
  **"Next Episode"** (marks episode watched, then the ⏭ path); auto-skip
  toggles under a new Settings "ANIME" group — "Skip intros by themselves"
  (default OFF), "Play the next episode by itself" (default ON, 5s countdown,
  BACK cancels, OK advances now); pills translucent (0xA8) + lower (96dp).
- **Deployment proof:** gates green (362 tests, 0 failures) + emulator smoke.
  alpha.50 went to both boxes via adb (+ appops grant); **alpha.51 was then
  delivered to BOTH boxes entirely over the internet through the new updater**
  (publish → box prompt → confirm; .196 and .117 both verified running
  0.3.0-alpha.51, crash buffers clean). Rachael's box is OTA-ready to leave.
- adb-driving the confirm dialog: focus starts on **Cancel**, Update is
  LEFT → OK · wait · DPAD_LEFT · OK. Asleep display eats the first key.
⏳ **NEXT ACTION:** (a) **Owner eyeballs alpha.51 tonight** on an anime with
a timed intro: intro button is translucent + lower; press OK → lands ~2s
before the intro's true end (early, never late); at the ending the button
says "Next Episode"; leave it alone → "Next episode in 5…" countdown → next
episode plays + the finished one gets its ✓ (BACK during countdown keeps
watching). Settings → ANIME group reads clearly. (b) **Round-15 still open:**
#8 Naruto episode photos past S1 (data-side, investigate the meta addon's
seasonal↔absolute artwork lookup), #9 search mic-left + voice-first search
toggle, #10 the big Settings rework (Views category, Home view ⚙ like
Discover, plain wording, Player→Expert, REMOVE "Connect this TV", add "Reset
settings to default" before Expert that does NOT sign out, discoverability
hints). (c) Future updates to Rachael's box: just publish — she gets the
prompt on her next app open.

## ⚠️ OWNER ROUND 15 (2026-07-11, session 25) — FULL LIST, logged before building
🚨 Log first, build second. Owner is HOME (boxes reachable). **HARD DEADLINE:
tonight is the last night with Rachael's box (.196) — she picks it up
tomorrow. The OTA updater (#6) MUST be built + on her box tonight.**
Owner confirmed alpha.49's Skip Intro GREEN on the box ("it's great!").
1. **Skip button transparency** — make the button (or at least its background)
   slightly see-through; "not too much, just enough to see a little through it."
2. **Skip lands a few seconds LATE** — after pressing Skip Intro you land a few
   seconds past the intro's actual end. Better to end a little EARLY than late;
   bias the skip target earlier.
3. **Credits should be "Next Episode", not "Skip Intro"** — the credits/outro
   window must (a) be labeled "Next Episode", (b) jump to the next episode, and
   (c) mark the current episode watched/finished first (credits are past 90% so
   Trakt scrobble is already safe — the mark is for looks + safety).
4. **Auto-skip toggles in Settings:** "Skip credits & continue to next episode
   automatically" (leaning default ON) + "Skip intros automatically" (default
   OFF until proven perfect). Auto-credits shows "Continuing to next episode
   in 5…" countdown with a Cancel option — small, not covering the video.
5. **Move skip/next-episode UI a little LOWER on screen**, same transparency
   as #1; countdown prompt likewise small + low.
6. 🚨 **OTA updater — TONIGHT.** A way to update Rachael's box (.196) over the
   internet without her involvement once it leaves. Use whatever tools needed.
7. **Anime settings section** — the skip features are anime-only; group them
   under an "Anime" section with obvious plain-language explanations. Everyone
   gets the section (the "no-anime" profiles still surface anime titles, so
   non-anime users may still use skip if they wander into anime).
8. **Naruto episode photos STILL blank past season 1** (Round-14 #4). Owner
   toggled "Episode numbering" + fully closed the app — no change. Data-side
   (meta addon artwork lookup); investigate for real this time.
9. **Search: mic button moves to the LEFT of the text field.** Side-panel
   magnifying glass should open Search AND activate the microphone in one
   click (the Google voice overlay already reads "Speak now" — no extra label
   needed). Add a Settings toggle for voice-first search.
10. **Settings rework — sectioned but clear, visual-first:** UI/sound at top.
    New "Views" category holding Home-view + Discover-view sub-sections (Home
    gets its own "view ⚙" entry point like Discover's). No deep expanding —
    easy to digest at a glance. Plain 8th-grade wording everywhere. Move
    Player under Expert mode. REMOVE "Connect this TV" (superseded by "Reset
    this TV"). Add "Reset settings to default" placed just BEFORE Expert mode
    — resets app settings (posters per row etc.) WITHOUT signing out. Find a
    non-intrusive way to hint what's changeable (e.g., search hinting
    mic-vs-typing) for tech-illiterate users.
Priorities: #6 tonight (deadline) → deploy alpha.49 to both boxes → skip
cluster #1-#5+#7 (ships as the OTA updater's first over-the-air payload) →
#9/#10 settings+search rework → #8 investigation.

## ⚠️ READ FIRST (session 24 cont. 6 — 2026-07-11 — alpha.49 BUILT: anime IMDb→MAL bridge — AniSkip now works for the owner's IMDb-keyed anime; ✅ owner-confirmed GREEN on the box (session 25); deploy block below is now DONE)
**alpha.49 (versionCode 49) BUILT — gates green (353 tests, 0 failures; 15 new)
+ assembleRelease clean + emulator smoke passed (installed, MainActivity
resumed, crash buffer empty). ⚠️ NOT deployed to EITHER box — the owner is at
his parents' house, both boxes unreachable by design this session.**
DECISIONS #58. This is Round-14 **#3** (the big backlog item): AniSkip never
fired because the owner's anime is IMDb-keyed (box log `malId(tt…) →
unresolved scheme=`) and AniSkip needs MAL id + MAL episode.
- **Bundled IMDb→MAL map**: `tools/build_anime_map.py` merges Fribb/anime-lists
  (imdb↔mal + TVDB season per MAL entry) with ScudLee's anime-list.xml
  (absolute-numbered shows + split-cour episode offsets) →
  `app/src/main/assets/anime_imdb_mal.json` (~110 KB, 3,870 shows). Verified
  entries: Naruto absolute → MAL 20, Shippuden → 1735, One Piece → 21, AoT all
  4 seasons INCLUDING Final-Season part offsets 16/28, DBS 8 entries, JJK 3.
  Rerun the script any time to refresh the asset (it self-refuses tiny maps).
- **`ImdbMalBridge.kt`** (pure, 14 tests incl. a committed-asset gate test):
  seasonal entries by largest-offset-below-episode (split cours), absolute
  shows translate via the episode's position in the app's own episode list
  (specials excluded). Naruto S2E5 → MAL 20 **ep 40** — the resolver now
  returns the TRANSLATED episode (`MalEpisode`), the wrong-window trap.
- **Resolver/repository/PlayerViewModel** now pass season + absolute episode;
  kitsu:/mal: paths unchanged (episode passes through). The skip diagnostics
  line logs `s=… e=… abs=… → mal=… ep=…` — the box App log will show exactly
  what each anime episode resolves to.
- ⚠️ NOT verifiable end-to-end without a box: playing a stream on the emulator
  would fire the alpha.35 Trakt check-in on the owner's account. **Owner test
  on the box: play an IMDb-keyed anime episode with a timed intro (Naruto,
  AoT, JJK) → "Skip Intro" should appear; if not, App log → the `skip` lines.**
- #4 (blank Naruto episode photos after S1) is NOT this — that's the meta
  addon's seasonal↔absolute artwork lookup, data-side, still open.
⏳ **DEPLOY alpha.49 to BOTH boxes when the owner is home** (carries alpha.48's
polish out too; .117 is on alpha.47, .196 on alpha.45):
`adb connect 192.168.1.117:5555 && adb -s 192.168.1.117:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
(same for .196; then leanback relaunch on both.)

## ⚠️ READ FIRST (session 24 cont. 5c — 2026-07-11 — alpha.48 BUILT: consistency polish batch; ⚠️ NOT DEPLOYED — network dropped)
**alpha.48 (versionCode 48) BUILT — gates green (340 tests, 0 failures) +
assembleRelease clean + emulator-verified. ⚠️ NOT deployed to EITHER box:
.117 became unreachable mid-session ("Network is unreachable" — this Mac's
hostname flipped .lan → .local, so the MAC likely changed Wi-Fi networks;
the box is probably fine). .196 offline all session.** DECISIONS #57.
Commit `74b833d`. Owner confirmed alpha.47's drift fix GREEN on .117
("feels better and the row shift is gone") and asked for Stremio/Netflix
consistency polish:
- **RowEntryMemory now app-wide** (`ui/components/RowEntryMemory.kt`):
  Search result rows + the Discover grid joined Home on the index-based
  entry memory; the last `focusRestorer` call sites are gone. Discover's
  memory keys on (catalog, genre) so a new filter never inherits the old
  grid's position. Emulator-verified: Home drift probe still clean; the
  Discover grid enters fresh at card 1 and re-enters at the card you left.
- **Skeleton tile loading** (`ui/components/Skeletons.kt`): Home loading
  rows, Search "Searching…" rows and Discover's initial load now paint
  full-size STATIC tile silhouettes (same geometry incl. focusHeadroom →
  zero reflow when content lands). Round-14 #9's "half skeleton, half
  blank" visual is gone; no shimmer on purpose (DECISIONS #22).
- **Home LazyColumn contentType hints** (header/hero/catalog-row/cw-row) —
  like-for-like node recycling while hold-scrolling.
⏳ **DEPLOY alpha.48 to BOTH boxes when reachable:**
`adb connect 192.168.1.117:5555 && adb -s 192.168.1.117:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
(same for .196; then leanback relaunch. .117 is on alpha.47, .196 on
alpha.45 — this carries everything forward.)

## ⚠️ READ FIRST (session 24 cont. 5b — 2026-07-11 — alpha.47 DEPLOYED to .117: Home row drift FIXED (emulator-proven) + BACK opens the rail)
**alpha.47 (versionCode 47) BUILT — gates green (340 tests; the known
HomeViewModelTest dispatcher flake cleared on a fresh full run) — DEPLOYED
to .117 + smoke-launched (versionCode 47, MainActivity resumed, crash buffer
empty). .196 STILL OFFLINE.** DECISIONS #56. Commit `4d0a0cb`.
- **Round-14 #7 "rows shift on their own" ROOT-CAUSED + FIXED + emulator-
  proven.** Owner's new clue ("shifted rows have the selection in the third
  spot") + a DISCRETE-press repro (no key-repeat needed — the old "needs the
  owner's remote" assumption was wrong for THIS bug): alpha.46 down5/up5 put
  focus at x=685, off the card grid, row dragged sideways. Cause:
  `focusRestorer` remembers the focused child NODE; lazy rows recycle nodes
  across items, so re-entry landed on whatever card the recycled node now
  showed. Plus the per-row restore-scroll effect re-snapped the last-opened
  row on EVERY recomposition. Fix: `RowEntryMemory` (saveable last-focused
  INDEX + `focusProperties { onEnter }` redirect) + a `restorePending` gate.
  alpha.47 measured: down5/up5 = x=252 every step; leave a row at card 3,
  come back → card 3, same bounds; back-from-Details tile restore intact.
  ⚠️ Discover grid / Search rows still use focusRestorer (no drift reported
  there); swap to RowEntryMemory if the owner ever sees it.
  ⚠️ #9 hold-lag: NOT separately addressed — the fix removes the restore-
  snap/focus-restorer churn which should help; skeleton unevenness remains.
- **BACK opens the rail (new owner ask, this session).** On Home/Discover/
  Search/Settings: BACK from content focuses the NavRail with the selector
  on the CURRENT section (deep-in-a-grid → one BACK, not 20 LEFTs); BACK on
  the rail = "Press BACK again to exit" toast, second BACK within 3s exits.
  Handlers composed AFTER the NavHost (later registration outranks its
  back-pop — otherwise Discover popped to Home); disabled off-section so
  player/details BACK flows untouched. Emulator-verified on Home + Discover.

## ⚠️ READ FIRST (session 24 cont. 5 — 2026-07-11 — alpha.46 DEPLOYED to .117: the WATCHED SYSTEM from the owner's design handoff)
**alpha.46 (versionCode 46) BUILT — assembleDebug + testDebugUnitTest GREEN
(340 tests, 8 new) + assembleRelease clean. DEPLOYED to .117 + smoke-launched
(versionCode 46 confirmed; MainActivity resumed, crash buffer empty). .196
OFFLINE (connect timed out).** DECISIONS #55. Commit `cac216c`.
Owner dropped `design_handoff_watched_system/` (repo root, gitignored — an
HTML prototype + README spec) and said "use it as the watched UI for the app".
Implemented as a REPLACEMENT for the alpha.45 poster bar/badge + alpha.25
green episode checks — one three-state language everywhere:
- **Posters (Home/Discover/Search):** unwatched artwork stays PRISTINE;
  in-progress = 26dp progress ring top-right with the percent inside;
  watched = accent check disc + the artwork dimmed so unwatched pops. The
  focus title-reveal now also shows a thin progress bar + "N min left".
  New `ui/components/WatchIndicators.kt` — all Canvas geometry (no font
  glyphs, DECISIONS #54 rule), all static (no animation, box-safe).
- **Details episode view:** every row has a fixed trailing status column —
  dashed circle + NEW / ring-with-percent + RESUME / check disc + WATCHED;
  watched rows recede (62% content alpha + thumbnail dim); in-progress rows
  say "N min left". Season pills: mini check disc when the season is fully
  watched, "3 / 14" count on the selected one; show header gets a small ring
  + "31 of 98 episodes watched" (pure `ui/details/WatchStats.kt`, tested).
  The old green WatchedBadge/WatchedGreen are GONE — watched is accent now.
- **Discover:** new "Hide watched" toggle pill in the filter bar (selected =
  ON), persisted (`DiscoverViewPrefs.hideWatched`); drops FINISHED titles
  only (in-progress/untouched always stay); a page emptied by the filter
  explains itself. `DiscoverSort.hideWatched`, tested.
- **Colors:** handoff's #3E8BFF blue family collapsed onto the app's single
  Accent (#4DA3FF, house one-accent rule); the handoff's neutral label tints
  + rgba(4,8,14,0.48) artwork dim taken literally.
- **NOT built (follow-ups if the owner asks):** click-to-unwatch, long-press
  "mark season watched" (both need a progress write path for never-played
  episodes — durations unknown), and the dim/percent/time-left tweak
  toggles (shipped always-on).
⚠️ NOT visually verified — the emulator has no watch history, and playing a
stream to create one would fire the alpha.35 Trakt check-in on the owner's
account. **Owner eyeballs on the box:** tiles he's mid-way through show a
ring with a percent; finished ones dim with a check; Details shows the
NEW/RESUME/WATCHED rail + season roll-ups; Discover's "Hide watched" pill.
⏳ **NEXT ACTION:** (a) **.196 still offline** — install alpha.47 when it pings:
`adb connect 192.168.1.196:5555 && adb -s 192.168.1.196:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
(then leanback relaunch — carries alpha.46+47 out together). (b) **Owner to
eyeball alpha.47 on .117:** the watched system (rings/checks/dim, episode
NEW/RESUME/WATCHED rail, season roll-ups, Discover "Hide watched"), the
Home drift fix (down/up over rows — no sideways shift, selector stays in
your column's card), and BACK-opens-rail (BACK from a grid → rail with
current section selected; BACK BACK → exit). Report whether hold-scroll
lag (#9) feels better after the churn removal or still needs a perf pass.
(c) Remaining backlog: **#3/#4 anime IMDb→MAL bridge + numbering** (big app
build — start from the box log's `malId(tt…) → unresolved scheme=` line),
**#8 hold-UP hero skip + #9 residual scroll perf** (still remote-dependent),
#16 user skins (future). Watched-system follow-ups if asked: DECISIONS #55.

## ⚠️ OWNER ROUND 14 (2026-07-11, session 24 cont. 2) — FULL LIST, logged before building
🚨 Log first, build second (usage-cutoff lesson). Config notes: **keep "Skip filler" +
"Skip recap" + "Allow users to mark" ENABLED on both of his streams — do NOT disable.**
1. **Exit app must keep place** — leaving the app (HOME key) and coming back should land
   exactly where you were, playback paused (pause already works). Currently returns to Home.
2. **Scrub acceleration cut in half** — alpha.40 ramp (10s→30s→60s→120s) too fast.
3. **Skip Intro/credits never appears** ("auto or manual"); next-episode prompt only at very
   end. AniSkip is MAL-keyed; his anime is IMDb-only (box log: `malId(tt…) → unresolved
   scheme=`). Needs an IMDb→MAL bridge + the anime absolute-vs-seasonal numbering story.
4. **Naruto episode photos blank after season 1** — owner recalls the same on Stremio when
   switching anime numbering seasonal↔absolute (TMDB/IMDb couldn't map). Same numbering story.
5. **Watched-progress indicators on poster artwork** (Home tiles etc.) — none show today.
6. (config, done/keep) skip-filler settings stay on; **TorrentsDB is back online → re-enable**
   on primary+backup AIOStreams.
7. **Home rows shift left on their own** (Home only, NOT Discover). Exact repro: down 4 rows,
   up 3 → one row shifted by 1, another by 2. Some rows only. (R13-1, now with repro.)
8. **Hold-UP sometimes skips past the hero to the top pills** — hero is double-height, "it's
   like it only moves half as much as it needs for that last row. Only sometimes."
9. **Hold up/down on Home lags badly, not premium** — skeletons load unevenly (half skeleton,
   half unallocated blank), plus the shifted rows compound it.
10. **Discover top bar**: use the Discover "settings-next-to-View"-style icon for the sidebar
    Settings; swap the cog and "View" order in Discover.
11. **Home top pills**: make Settings look different / farther from Discover+Search; owner may
    later ask to remove the top pills entirely (they're the #33 headerFocus anchor — re-anchor
    first).
12. **Discover filters** should look clickable/filter-like, hint their options, stay clean.
13. **Play/pause control redesign** — "the pause is totally out of the ballpark".
14. **Trakt Recommendations row at the TOP of Home; Continue Watching near top too.**
15. **NavRail nav glitch, LIVE on .117 right now**: Home click does nothing; from Search,
    clicking Home lands on Discover. (alpha.38 popUpTo/saveState/restoreState suspect.)
16. Future/cosmetic: user-pickable colors/skins.
17. **NavRail focus emphasis** — highlight the item you're hovering (focused), not just the
    current section.
18. **Move "Connect this TV" + "Prefer software video decoder" under Expert mode.**
Priorities this session: #15 (broken now) → #1/#2/#17/#18 (contained) → TorrentsDB config →
then the hard clusters #7/#8/#9 (focus+perf) and #3/#4 (anime numbering) as budget allows.
✅ **#15, #1, #2, #17, #18 all BUILT in alpha.44** (cont. 3, below) — owner CONFIRMED GREEN
2026-07-11 ("all is green on my end"). ✅ **#5, #10, #11, #12, #13, #14 all BUILT + DEPLOYED
in alpha.45** (cont. 4, below). ✅ **#6 DONE (cont. 4): TorrentsDB re-enabled + LIVE-VERIFIED
on Adam's primary + backup AIOStreams** (primary was already on; backup flipped via API PUT,
fresh GET confirms enabled=true on both; same UUIDs → manifest URLs unchanged, nothing to
re-add). STILL OPEN in Round 14: #3/#4 (anime IMDb→MAL + numbering), #7/#8/#9 (Home focus
drift + hold-UP + scroll perf — need the owner's remote), #16 (user skins, future).

## ⚠️ READ FIRST (session 24 cont. 4 — 2026-07-11 — alpha.45 DEPLOYED to .117: Round-14 polish batch — pinned recs, poster indicators, drawn glyphs, filter pills)
**alpha.45 (versionCode 45) BUILT — assembleDebug + testDebugUnitTest GREEN (332
tests, 12 new; the known HomeViewModelTest real-threads prefetch test flaked
once and cleared on rerun) + assembleRelease clean. DEPLOYED to .117 +
smoke-launched (versionCode 45 confirmed; MainActivity resumed, no FATAL).
NOT visually verified — needs owner eyes.** DECISIONS #54. Commit `ca7bd6e`.
Six Round-14 items in one commit:
- **#14 Trakt Recs to top of Home.** `withRecommendationsFirst` (HomeRowPrefs.kt)
  pins rows whose TITLE contains "recommend" first; Home renders header → hero →
  pinned recs → Continue Watching → rest. Suppressed the moment the user sets a
  row-manager order (that IS the override tool). `homeRestoreIndex` grew a
  `pinnedRowCount` param (back-from-Details restore still lands right).
- **#5 Poster watch indicators** on Home/Discover/Search tiles: CW-style 7dp
  accent bar for in-progress, ✓ badge for watched. `posterIndicatorFor` uses the
  60s CW floor (not the 15s resume floor) so accidental clicks don't stamp Home;
  a series tile speaks with its latest-watched episode
  (`ProgressRepository.latestByMetaKey`, keyed "metaType/metaId").
- **#13 Play/pause redesign.** Root cause: ⏸/▶/⏮/⏭ were FONT glyphs — the box's
  emoji fallback rendered the pause "out of the ballpark". New `DrawnIcons.kt`
  (Canvas geometry: PlayerGlyph, GearIcon, CaretDownIcon). Play/pause is a
  38dp circular chip that flips solid-Accent while paused; prev/next pills draw
  their glyphs. Rule extended: no font symbols in controls, ever ("✓" is fine —
  it already ships in Details and renders properly).
- **#10** One shared GearIcon for NavRail Settings, Discover's View pill and
  Home's Settings pill; Discover's View pill is now "View ⚙" (cog AFTER, drawn).
- **#11** Home header: Discover+Search grouped; Settings 26dp apart with gear +
  muted text. headerFocus anchor (the #33 hold-UP fix) untouched, still on
  Discover.
- **#12** Discover pickers are now `FilterPill`s: muted dimension label ("Type")
  + current value + drawn ▾ — they read as openable filters.
⚠️ Commit hygiene: `git add -A` initially swept in `.claude/` + the owner's
"SStreams Loader.dc_files/" page — amended out before push, both now in
.gitignore.
⏳ **NEXT ACTION:** (a) **.196 still offline** — install alpha.45 when it pings:
`adb connect 192.168.1.196:5555 && adb -s 192.168.1.196:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
(then leanback relaunch). (b) **Owner to eyeball alpha.45 on .117:** Trakt Recs
row first + CW right under it; progress bars/✓ on tiles he's watched; the new
round play/pause chip (accent when paused); prev/next glyphs; Discover filter
pills + "View ⚙"; Home's Settings pill moved apart with the gear. (c) ✅ #6
DONE this session: TorrentsDB re-enabled on primary+backup AIOStreams via the
API (GET userData → flip preset.enabled → PUT → verify GET; the config lives
at `data.userData`, NOT `data.config` — scratchpad `enable_torrentsdb.py`
pattern). (d) Then the remaining backlog: **#3/#4 anime IMDb→MAL bridge +
numbering** (app build, big — start from the box log's `malId(tt…) →
unresolved scheme=` line), **#7/#8/#9 Home focus/perf** (need the owner's
remote at the TV — adb can't fake key-repeat, DECISIONS #33).

## ⚠️ READ FIRST (session 24 cont. 3 — 2026-07-11 — alpha.44 DEPLOYED to .117: return-to-your-place + NavRail glitch + Round 14 batch)
**alpha.44 (versionCode 44) BUILT — assembleDebug + testDebugUnitTest GREEN (320
tests; known HomeViewModelTest Main-dispatcher flake cleared on rerun) +
assembleRelease clean. DEPLOYED to .117 + smoke-launched (versionCode 44
confirmed installed; MainActivity resumed, no FATAL). NOT visually/interaction-
verified (needs a human at the TV).** DECISIONS #53.
Five Round-14 items landed in one commit (818b377):
- **#15 NavRail glitch (was LIVE on .117) — FIXED.** `restoreState = route !=
  Routes.HOME` (HOME is the popUpTo anchor, so its saved segment was whatever
  section sat on top → Search→Home landed on Discover, Home click "did
  nothing"). Home's header pills now route through `goSection`, not a bare
  `navigate()` (a plain push got pop-and-saved and then shadowed Home forever).
- **#1 Exit-app-keeps-place — BUILT.** PlayerViewModel stashes the playing
  video's identity in SavedStateHandle; on process-death restore, PlayerScreen
  re-enters the video via the stream flow (fresh link + resume prompt) instead
  of dumping to Home. Lands paused at the saved MediaRef position.
- **#2 Scrub acceleration halved.** `Scrubbing.stepMs` thresholds doubled
  (5/12/20 → 10/24/40); ~2s hold to reach 60s steps, first taps stay 10s. Test
  updated.
- **#17 NavRail focus emphasis.** Hovered item = solid accent pill + dark glyph;
  selected-but-unfocused = the quiet tinted pill as before.
- **#18 Expert-mode gating.** "Prefer software video decoder" + "Connect this
  TV" moved under Expert mode (the in-player "Fix blocky video" covers the
  everyday decoder case).
⚠️ **Only #2/#17/#18 are safely emulator/reason-verifiable; #1 and #15 need a
real box** — #1 requires forcing process death mid-playback, #15 needs genuine
d-pad section switching. Both were reasoned from the alpha.38 back-stack model
and the SavedStateHandle lifecycle, not measured.
⏳ **NEXT ACTION:** .117 is done. **.196 still pending** — install alpha.44 when
it pings →
`adb connect 192.168.1.196:5555 && adb -s 192.168.1.196:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
then relaunch (leanback launcher). **Owner to verify on .117:** (a) NavRail — from Search, click Home →
lands on Home (not Discover); Home click when already on Home does nothing bad;
(b) start a show, HOME key out, reopen the app → back in the video, paused, at
your spot; (c) the hovered rail item is the bright accent pill; (d) Settings →
decoder + Connect are gone until Expert mode is on. Then resume the Round-14
backlog (priorities updated in the OWNER ROUND 14 block above): the remaining
easy config item is #6 (re-enable TorrentsDB on primary+backup AIOStreams), then
the hard clusters #7/#8/#9 (Home focus+perf, need the remote) and #3/#4 (anime
IMDb→MAL numbering).

## ⚠️ READ FIRST (session 24 cont. — 2026-07-11 — alpha.43 DEPLOYED to .117: owner's wave-dots loader; Trakt-history no-code answer)
- **alpha.43 (versionCode 43) BUILT + DEPLOYED to .117, smoke-launched** (gates
  green). The player load spinner is now the owner's own "SStreams Loader"
  wave-dots design (saved Claude Design page in repo root, gitignored-ish —
  untracked). DECISIONS #52: pure unit-tested keyframe math in
  `ui/components/WaveLoader.kt`; frame-clock + draw-phase only (R13-8 + #22
  lessons); dots are brand periwinkle #A8CBE8, not accent blue. ⚠️ NOT
  visually verified — seeing it requires starting a stream, which fires the
  alpha.35 Trakt check-in on the owner's account (and it was ~2am — no TV
  audio). **Owner eyeballs it on his next stream load.**
- **"Trakt history works in Stremio but not the app" — ANSWERED, no code bug.**
  The check-in fires off the subtitles/{type}/{id}.json ping (alpha.35), and
  the ONLY addon declaring `subtitles` (= the Trakt check-in instance,
  "AIO - Adam - Movies & Series") was MISSING from .117 from Jul 9 until the
  alpha.42 profile-cache fix installed it tonight. No target → no ping → no
  history; his Stremio account always had the addon → worked there. Verified:
  WatchTrackingPing targets enabled installed subtitles-declaring addons;
  adam-A + rachael-A live manifests declare subtitles (B instances don't —
  correct, exactly one check-in source per person). **Owner: play anything on
  the box, then check trakt.tv history** — should appear now. If it still
  doesn't: App log → look for "watch-tracking: check-in ping failed".

## ⚠️ READ FIRST (session 24 — 2026-07-11 — alpha.42 DEPLOYED to .117: manual-list Trakt rows gone everywhere; profile-cache root cause)
Finished the cut-off "remove Trakt collection rows" session. The rows came from
TWO places, and neither was the live configs (already clean — audited all 4
AIOMetadata instances, Adam's + Rachael's: zero manual-curation catalogs live).
1. **"Trakt Integration" addon hand-installed on .117** (official strem.io Trakt
   addon — every row is add-it-yourself). Not profile-managed, so sync
   (correctly) never touched it. **Removed via the box's addon manager.**
2. **alpha.42 (versionCode 42) BUILT + DEPLOYED to .117 + box-proven** — gates
   green (315 tests; known HomeViewModelTest flake cleared on rerun). Two
   cache layers had hidden every owner config edit from boxes (DECISIONS #51):
   (a) sync never re-fetched manifests of already-installed addons → new
   `SyncPlan.refresh` re-upserts them each due sync; (b) **the profile JSON
   itself was disk-cached for 2 days** (Dreamhost `max-age=172800` + OkHttp
   cache) → .117 synced "successfully" with ZERO network since Jul 9 and was
   stuck on a pre-5-instance 3-addon profile. `SetupProfileClient` now sends
   `no-cache` (ETag → 304), and `/setup/.htaccess` serves `no-cache` for
   `*.json`. **Box-proven:** profile GET hits the server on launch, all 7
   profile addons installed (2 AIOMetadata + 3 AIOStreams + Cinemeta +
   AIOLists), Home rows = Featured/Popular/Trending/Trakt Recommendations/
   Trakt's Popular only. Diagnosis trick: Dreamhost access log = ground truth
   for "did the box actually fetch".
3. **Both Stremio accounts re-synced to live manifests** (stremio_api
   set_addons; backups in StremioSurfer/stremio-addons-backup-*-2026-07-11.json).
   Adam's elfhosted AIOMetadata snapshot was a config-generation behind
   ("AIO - Friends Anime" 66 cats → "AIO - Adam - Movies & Series" 59) and his
   primary AIOStreams snapshot still declared 48 junk catalogs (Live TV/
   sports/Hindi) → now 0. Rachael's (explicit owner permission this request):
   same refresh, minor version diffs only. Her AIOLists = search-only, her
   AIOStreams library rows auto-fill — nothing manual anywhere.
⚠️ During box UI driving, a BACK press surfaced a background STREMIO task that
resumed mid-playback ("Hachi: A Dog's Tale", ~1 min) — may have stamped Adam's
Trakt history/Continue Watching; owner can remove it in Trakt if it shows.
⏳ **.196 still OFFLINE** — install alpha.42 when it pings:
`adb connect 192.168.1.196:5555 && adb -s 192.168.1.196:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
(then relaunch; its profile sync will also self-heal the addon list).
Rachael's box: her profile + configs are clean; if her Home ever shows
add-it-yourself rows, check HER box for a hand-installed Trakt addon too.
Player verification (Z1, alpha.40 work) still pending on alpha.42.

## ⚠️ READ FIRST (session 23 cont. — 2026-07-10 — alpha.41 HOTFIX DEPLOYED to .117: duplicate catalog ids crashed Home)
Owner installed alpha.40 on .117 and hit an instant crash "when I go down quickly". Pulled the
stack over adb: `Key "tt33332385" was already used` — duplicate LazyRow keys. A live catalog now
serves the same id twice; Home rows never deduped (Discover did, with a comment naming this exact
failure). NOT the player work — the keys date to alpha.19; the DATA changed (5-instance stack live).
**Fix (DECISIONS #50): `CatalogRepository.fetch` + `MetaRepository.resolveMeta` now distinctBy id**
(covers Home/Discover/Search rows + Details episode rows). TDD'd, gates green.
**Box-proven before/after on .117:** alpha.40 + 14 rapid DOWNs = same FATAL on demand; alpha.41 +
38 DOWN/UPs = zero crashes. **alpha.41 DEPLOYED + verified on .117. ⏳ .196 was OFFLINE — install
alpha.41 there when it pings** (same install -r command). Player verification (Z1 below) now
happens on alpha.41.

## ⚠️ READ FIRST (session 23 — 2026-07-10 — alpha.40: player picks its decoder PER STREAM; fluid scrubbing)
🚨 **BOXES STILL ON alpha.30.** alpha.31–.40 are ALL undeployed. Deploy before triaging any owner
bug report. 🚨 **DO NOT EDIT RACHAEL'S ACCOUNTS** without explicit per-request permission.

**alpha.40 (versionCode 40) BUILT — assembleDebug + testDebugUnitTest GREEN; emulator smoke passed
(install, MainActivity resumed, PlaybackService built the new engine + MediaSession, no crash).
NOT deployed.** Owner ask this session: "main player is kinda trash — rainbow artifacts on some
streams; premium, responsive but fluid feel." DECISIONS #49; design note in
docs/superpowers/specs/2026-07-10-player-quality-design.md.
- **Rainbow artifacts (silent hw macroblocking, R11 N1) now handled AUTOMATICALLY.** The engine
  decides software-vs-hardware PER STREAM at play(): session toggle > Settings pref > automatic
  (software when the label codec — `PlayableSource.videoCodec`, stamped in StreamMapping — is not in
  `DecoderCapabilities.hardwareVideoCodecs`). Mechanism = a DELEGATING `MediaCodecSelector`
  (PREFER_SOFTWARE vs DEFAULT off a var, consulted at every codec init) — no engine rebuild.
  play() stop()s first so codec REUSE can't carry a garbage decoder across streams.
  `VideoCodec` moved `StreamCascade` → `domain` (layering; StreamCascade re-exports the heuristics).
- **Decode-error safety net:** decoder-class errors (`isDecodeErrorCode`, pure) get ONE same-stream
  software retry at the same position before the try-another-stream walk.
- **"Software video" toggle applies IN PLACE now** (session override + replay at position; still
  persists the box pref) — no more stream-list bounce. Its ON/OFF mirrors the engine's per-stream
  truth (`usingSoftwareDecoder`), so auto-engaged software honestly reads ON.
- **Fluid controls:** d-pad scrubbing moves a PREVIEW target instantly (accelerating 10s→30s→60s→120s
  with press streak, pure `Scrubbing` helpers) and commits ONE real seek 350ms after the last press;
  "+2:30" delta chip during the gesture; OK mid-scrub commits; `SeekParameters.CLOSEST_SYNC`.
  Control bar fades/slides (degrades to instant pop when the box zeroes animator scale); paused
  keeps the bar up; a >400ms mid-playback rebuffer shows a small NO-SCRIM ring (keys keep working).
- 4 new test files: VideoCodecTest, ScrubbingTest, DecodeErrorTest, + codec-stamp in StreamMappingTest.
⚠️ Emulator playback was deliberately NOT exercised — starting a stream fires the alpha.35 Trakt
check-in ping on the owner's account. **Box verification = the real test:** play the anime/HEVC titles
that macroblocked; they should now open clean with "Software video: ON" showing automatically.
⏳ Deploy target **alpha.40**. Everything still open from session 22 (R13-1 drift, B2 hold-UP, Home's
redundant pills, NavRail focus verify, S3 Trakt scrobble, S4 profile builder) is unchanged below.

## ⚠️ READ FIRST (session 22 cont. 6 — 2026-07-10 — alpha.39: R13-4 + R13-5 FIXED *and emulator-proven*)
🚨 **BOXES STILL ON alpha.30.** alpha.31–.39 are ALL undeployed. **Deploy before triaging any owner bug
report** — every "still broken" report so far was tested against alpha.30.
🚨 **DO NOT EDIT RACHAEL'S ACCOUNTS** without explicit per-request permission.

**alpha.39 (versionCode 39) BUILT — assembleDebug + testDebugUnitTest GREEN. NOT deployed.**
Unlike every prior focus fix in this project, these two were **reproduced on the emulator against
alpha.38 and re-verified fixed against alpha.39** (before/after, same content). DECISIONS #48.
- **R13-4 Home-returns-to-top-on-Back — FIXED.** Two effects re-fired when Home came back off the nav
  back stack (the ViewModel is retained, so rows are already loaded on the first composition):
  `LaunchedEffect(featured != null){scrollToItem(0)}` re-snapped to the top, and
  `LaunchedEffect(showingRows){headerFocus.requestFocus()}` dragged the restored scroll up behind it
  (the header IS list item 0 — that's the #33 hold-UP fix, so it stays). Home now remembers WHICH tile
  was opened (`rememberSaveable` → outlives the back stack; the focused node and every focusRestorer's
  memory die when the screen is disposed), then on return scrolls the column to that row, the row to
  that card, and focuses it — probing a few frames because both lazy lists compose late. Falls back to
  the header when the row is gone. New pure `homeRestoreIndex()` + 4 unit tests (the hero and Continue
  Watching rows are conditional, so a catalog row's index shifts under it).
  ⚠️ The target is **never cleared** on purpose — the last tile opened stays the anchor for every later
  return to Home. Clearing it would send the next return to the header → back to the top.
  MEASURED: alpha.38 Back → Home at top, focus on the NavRail. alpha.39 Back → identical screen coords
  as before opening (same tile, same column AND row scroll).
- **R13-5 season selector jumped 1→3→5→7 — FIXED.** Coming UP from an episode row left the chip to
  Compose's geometric focus search; episode rows span the full width, so it picks whichever chip is
  nearest the row's centre, drifting right each trip. `focusRestorer(selectedChipFocus)` on the season
  LazyRow pins re-entry to the chip you left, falling back to the SELECTED season — whose chip is now
  scrolled into view at entry, since an unattached FocusRequester is one the restorer cannot focus.
  MEASURED (Dark Side of the Ring, 7 seasons): alpha.38 chip x = 703 → 930 → 930. alpha.39 x = 112,
  stable across 4 round trips.
- Also: touched call sites moved to the non-deprecated `focusRestorer(FocusRequester)` (stable) →
  3 `ExperimentalComposeUiApi` opt-ins dropped. Discover/Search still use the old lambda form.

**Emulator harness notes (save the next session an hour):**
- The AVD's screen sleeps and **silently eats d-pad keys** → run `adb shell svc power stayon true`
  first, or your key presses drive the Google TV launcher and every reading is garbage.
- `uiautomator dump` **lags a step behind** the UI; re-dump after ~5s before trusting it. Always assert
  `dev.openstream.tv` appears in the dump — otherwise you are measuring the launcher.
- `am force-stop` + `am start` **restores the nav back stack** (you can land back on Details, not Home).
- Compare focus by `bounds=` on the `focused="true"` node — identical bounds before/after = same tile,
  same scroll. That is the whole test.

⏳ Deploy target **alpha.39**. Still open from Round 13: **R13-1 focus drift on vertical scroll**
(down 9 / up 6 / down 6 shifts rows horizontally) and **B2 hold-UP scroll** — both need the owner's
remote (adb cannot simulate genuine key-repeat, proven in #33). Also open: Home still renders its old
Discover/Search/Settings pills next to the NavRail (they carry `headerFocus`, the #33 anchor —
removing them needs an entry-focus re-anchor first), and NavRail LEFT-from-content focus is still
not emulator-verified.

## ⚠️ READ FIRST (session 22 cont. 5 — 2026-07-10 — alpha.36/.37/.38; Adam's 5-instance stack LIVE)
🚨 **BOXES STILL ON alpha.30** (adb-verified). alpha.31–.38 are ALL undeployed. Deploy before triaging
any owner bug report. 🚨 **DO NOT EDIT RACHAEL'S ACCOUNTS** without explicit per-request permission.

**App (all build + testDebugUnitTest GREEN, none deployed):**
- alpha.36 — search "Go" now dismisses the on-screen keyboard (supplying KeyboardActions REPLACES the
  IME default action, so it never hid); player BACK closes the control bar first (second BACK exits);
  UP from the scrub bar escapes the control UI instead of trapping focus.
- alpha.37 — **audio-language ranking**: `StreamCascade.hasEnglishAudio` reads the label's *Audio*
  section only (the release tag lies — a stream tagged English can carry Italian/Japanese audio, which
  is why AIOStreams `requiredLanguages=English` never caught it). mergeForDisplay order is now
  **cached → English audio → hardware-decodable → resolution → source**. Conservative: unknown audio =
  English. 21 tests in StreamCascadeTest. Also: `ProfileLink.profileName` (captured on install,
  refreshed each sync) → Settings header shows "<name> · <brand> <version>" (blank until one sync on
  .37+ since existing installs never stored it).
- alpha.38 — **persistent left NavRail** (`ui/components/NavRail.kt`): Home/Discover/Search/Settings are
  now SIBLINGS, not a stack (navigate with popUpTo(HOME){saveState} + launchSingleTop + restoreState),
  so the back stack never grows. Rail collapses to Canvas-drawn icons (no font/emoji dependency),
  expands on focus, highlights the current section. Removed the on-screen BackButton from those 4
  screens; sub-screens (HomeRows/AppLog/Addons/Details/Streams) keep theirs.
  ⚠️ **Home still renders its old Discover/Search/Settings pills** — they carry `headerFocus`, the
  anchor for the hold-UP scroll fix (DECISIONS #33). Removing them needs an emulator pass to re-anchor
  entry focus first. Redundant-but-harmless until then.
  ⚠️ **NavRail focus is NOT emulator-verified** — LEFT-from-content → rail, and rail→content, need a
  real focus pass.

**Config — Adam's 5-instance stack is LIVE and complete:**
- AIOMetadata A `aiometadata.elfhosted.com` = "AIO - Adam - Movies & Series" (meta authority, the ONLY
  Trakt check-in; it is also the only one declaring `subtitles`, so the alpha.35 ping can't double-fire).
  AIOMetadata B `aiometadata.fortheweak.cloud` = "AIO - Adam - Anime & Streaming".
- All 3 AIOStreams pushed: meta OFF, catalogs OFF, excludeUncached=true, requiredLanguages→preference.
  **TorrentsDB auto-disabled on primary+backup (host 502)** — re-enable when it recovers.
  Removed a deprecated `usa-tv` preset that blocked every save on fortheweebs.
- ⚠️ **AIOStreams trap:** `resources: []` means ALL resources, not none. Stripping "meta" off a
  catalog-only preset leaves [] and it silently serves catalog+meta again. DISABLE catalog-only addons
  (tmdb-addon, debridio-tmdb/tvdb, streaming-catalogs) instead.
- Box profile deployed (7 addons) to `savoy.click/setup/adam-savoy-cYoj-ZKYTwQ.json`; Stremio account
  updated via `stremio_api.set_addons` (only Anime & Streaming was missing).
- The base AIOMetadata export carries a stray instance UUID in `regexExclusionFilter` — junk, now
  zeroed in both builder scripts.
⏳ Deploy target **alpha.38**. Still open: Round-13 focus drift, Home-back-to-tile, season-selector jump.

## ⚠️ READ FIRST (session 22 cont. 4 — 2026-07-09 — alpha.34/.35: scrub fix, ⏭, Trakt check-in ROOT-CAUSED)
🚨 **BOXES ARE ON alpha.30** (verified via adb on .117; .196 offline). alpha.31–.35 were NEVER
deployed — every "still broken" owner report was tested against alpha.30. Deploy before triaging.
🚨 **DO NOT EDIT RACHAEL'S ACCOUNTS** without explicit per-request permission (owner 2026-07-09);
she is a live user on Stremio + her box. Adam's account IS editable.
- **alpha.34**: (a) loading scrim now only on the INITIAL load — it was true for ANY non-READY
  state, so every seek re-buffered → blocking scrim → keys swallowed → held-scrub impossible, and
  the spinner flashed per skipped section (which is why it looked like a still image).
  (b) ⏭ shows for EVERY series episode (UiState.isSeries); resolves the episode list on demand,
  opens the next episode's stream list (auto-picks best; no Up Next countdown), else ends the video.
- **alpha.35 — Trakt check-in ROOT CAUSE.** AIOMetadata DOES have Watch Tracking → Trakt Check-in.
  It fires on the `subtitles/{type}/{id}.json` request a Stremio client sends at playback start —
  the ONLY playback-time signal a catalog/meta addon gets. We had `AddonClient.subtitles()` and
  NEVER called it → check-ins worked in Stremio, never in SStreams. New `addon/WatchTrackingPing`
  pings every enabled subtitles-declaring addon on playback start (fire-and-forget, guarded per
  videoId so a stream-swap doesn't double check in). ⚠️ Only ONE AIOMetadata instance may have
  traktWatchTracking=true or one ping checks in twice.
- **Adam's 2 AIOMetadata configs BUILT** (`Projects/AIOMetadata/build_adam_aiometadata.py` → `adam/`):
  A "Movies & Series" 51 catalogs, meta authority, **the only Trakt check-in**; B "Anime & Streaming"
  54 catalogs (MAL tab + anime + 30 streaming incl. Crunchyroll/HIDIVE + 18 networks). Anime ON,
  NSFW ON. Keys stripped. **Owner must import them → send the 2 URLs** (no AIOMetadata push tool).
- **Adam's AIOStreams:** nightly/elfhosted PUSHED (meta off, library off, excludeUncached=true,
  requiredLanguages []→preference). **primary BLOCKED** — AIOStreams validates every preset manifest
  and `TorrentsDB` returns 502; left unchanged (safe). **backup BLOCKED** — stale stored password.
- ⏳ NEXT: audio-language ranking in the app (parse the stream's "Audio" field — releases tagged
  English carry Italian/Japanese audio; requiredLanguages=English never caught it). Then adam's
  profile deploy (scp, no owner upload needed) + optional `push_stremio.py` into his Stremio.
⏳ Deploy target **alpha.35**.

## ⚠️ READ FIRST (session 22 cont. 3 — 2026-07-09 — alpha.33 BUILT: codec-aware / hardware-informed stream ranking)
**alpha.33 (versionCode 33) BUILT — assembleDebug + testDebugUnitTest GREEN (flaky HomeViewModelTest
Main-dispatcher test cleared on rerun). NOT deployed.** THE SOFTWARE-PLAYER-KILLER (owner's repeated
wish). Can't be emulator-verified — codec detection is per-box; needs a real box.
- New `player/DecoderCapabilities.kt` (@Singleton): reads the box's HARDWARE video decoders once from
  `MediaCodecList` → `Set<StreamCascade.VideoCodec>` {H264, HEVC, HEVC_10BIT, AV1, VP9}. Best-effort:
  query wrapped in runCatching → empty set on failure (and on the JVM in unit tests) → no-op ranking.
- `StreamCascade`: new `VideoCodec` enum + `videoCodecOf(stream)` (parses codec + 10-bit/HDR from the
  release label) + `canHardwareDecode(stream, hw)` (unknown codec OR empty caps → true; only DEMOTES
  codecs positively known-undecodable). `mergeForDisplay` now takes `hardwareCodecs` and ranks
  **cached → hardware-decodable → resolution → source order**. Does NOT reorder by language (owner
  2026-07-08 anime-dub rule preserved; source order is the finest tiebreaker).
- `AutoStartSelection`: `firstPlayableWhenSettled` → **`bestPlayableWhenSettled`** — the auto-pick now
  waits for ALL sources to settle then takes the merged-ranked TOP (was: first playable in addon order).
  `orderedAlternatives` ("Try a different stream") now walks the same merged/ranked/deduped order.
  `StreamListViewModel` injects `DecoderCapabilities`, exposes `hardwareCodecs` to the screen.
- Tests: StreamCascadeTest 19 (codec + merge), AutoStartSelectionTest 11, StreamListViewModelTest 6.
⏳ **Owner verify on a box:** does auto-play now pick a stream that plays CLEAN (no macroblocking, no
software-decoder trip) on an anime/HEVC title? If the box still macroblocks, read the App log — may need
the codec label heuristics widened. ⏳ Deploy target **alpha.33**. Still open in Round 13: focus drift,
Home-back-to-tile, season selector, next-episode, scroll indicator, de-emphasize help buttons.

## ⚠️ READ FIRST (session 22 cont. 2 — 2026-07-09 — alpha.32 BUILT: interwoven stream list)
**alpha.32 (versionCode 32) BUILT — assembleDebug + testDebugUnitTest GREEN. NOT deployed.**
On top of alpha.31: **the stream list is now ONE interwoven, ranked, de-duplicated list**
instead of per-addon blocks (owner 2026-07-09 "interweave the sources"). New pure
`StreamCascade.mergeForDisplay(groups)` (+3 tests, 16 total in that file): flattens every
addon's playable streams, de-dupes across sources (same release from all 3 AIOStreams →
one row, keyed on infoHash → filename → label, keeping the cached copy from the earliest
addon), then orders **cached-first → resolution (4k>1080p>720p) → addon/server order**.
`StreamListScreen` now renders that flat list (headers gone), first row takes entry focus,
"Finding more streams…" while sources load, failed sources collapse to a bottom note. This
supersedes the old "visible list keeps addon order (§4.1.7)" decision. **Next: make the
ranking codec-aware** (query `MediaCodecList` → prefer hardware-decodable streams so the
software player is rarely needed — the flagship item) — `mergeForDisplay` is the seam for it.
⏳ Deploy target now **alpha.32**. Still open: R13-1 focus drift, R13-4 Home-back-to-tile,
R13-5 season selector, R13-6 next-episode, codec-aware ranking, scroll indicator, help buttons.

## ⚠️ READ FIRST (session 22 cont. — 2026-07-09 — alpha.31 BUILT: Round 13 first wave (4 items) + Gemini/passport)
**alpha.31 (versionCode 31) BUILT — assembleDebug + testDebugUnitTest GREEN. NOT deployed.**
First wave of owner app-bug Round 13 (below), the contained/testable ones:
- **R13-3 Continue Watching dedupe** — `ProgressRepository.continueWatching` now
  `.distinctBy { metaType to metaId }` after the recency sort → ONE tile per show
  (latest episode), movies unaffected. +2 unit tests (15 green in that file).
- **R13-8 Loading spinner actually animates** — `LoadingAnimation` now drives its
  angle off `withFrameNanos` (frame clock) instead of `rememberInfiniteTransition`,
  which was frozen by the TV box's animator duration scale (reduced/off animations).
- **R13-2 Progress bar + ✓ bolder** — CW bar 4→7dp + Accent over near-opaque track;
  Details resume bar 4→6dp; watched ✓ badge 22→28dp + white ring, bigger glyph.
- **R13-7 Fading stream numbers** — global 1..N badge on each stream row (spot when
  auto-retry loops), fades out ~5s after the list shows (draw-phase alpha, no recompose).
Config side (also this session): Gemini key added to Rachael's passport (users.json)
+ a **Gemini API spot** in passport.html; owner pasted Gemini into both live
AIOMetadata configure pages + saved — **URLs unchanged**, so her deployed box profile
is still valid. (AIOMetadata edits in place, no password: GET `/api/config?id={uuid}`
reads it, values are redacted in that read.) make_profiles 5-instance + passport 2
AIOMetadata cards were the prior sub-session.
⏳ **STILL OPEN in Round 13 (need a build + a box, not emulator):** R13-1 focus drift,
R13-4 Home-returns-to-top-on-Back (restore focus to origin tile), R13-5 season-selector
jumps 1→3→5→7, R13-6 "Next episode" missing on some items. **PLUS the big one:**
codec-aware/hardware-informed autoselect ranking (query MediaCodecList → prefer streams
the box can hardware-decode, so the software player + help buttons are rarely needed —
owner's repeated wish); a Stremio-style scroll indicator; de-emphasize the help buttons.
⏳ Deploy target now **alpha.31**.

## ⚠️ READ FIRST (session 22 — 2026-07-09 — Rachael 5-instance addon stack LIVE + owner app-bug ROUND 13 logged)
Config/provisioning session (NO app build). All LIVE + verified:
- **Rachael's full 5-instance stack is live** ([[aiometadata-aiostreams-5-instance-architecture]]):
  2 AIOMetadata (Discover=meta+Trakt-scrobbler ~50 cats; Streaming=by-service+networks
  ~50) she imported + gave URLs; 3 AIOStreams (fortheweak/fortheweebs/elfhosted) I
  built+pushed — **meta OFF** on all (AIOMetadata owns meta), catalogs only on primary
  (RD library + Debridio; TorBox library REMOVED = shared history), **Debridio wired**
  (watchtower streams), **excludeUncached=true + language-first sort** (Futurama fix).
- **Box profile DEPLOYED** to `savoy.click/setup/rachael-wv_EExTgN6I.json` (7 addons) via
  scp to Dreamhost — see [[setup-site-hosting-deploy]]. Setup URL is `savoy.click/setup/`
  (NOT /subs/). Box loads on relaunch / type "rachael".
- **`tools/make_profiles.py` UPDATED** to emit the 5-instance layout (2 AIOMetadata via
  `aiometadata.discover/streaming`, all 3 AIOStreams). Uncommitted. ⚠️ profiles.config.json
  needs Rachael→`rachael-wv_EExTgN6I.json` seeded before a family-wide regen or she gets a
  new filename (404s her box).
- **passport.html**: AIOMetadata now has TWO cards (Discovery + Streaming, uuid/pw/manifest).
- Answered: Trakt Recommendations IS first in her Discover Home; the row is empty because a
  fresh Trakt account has no watch/rating history to generate recs — it fills after scrobbling.

**OWNER APP-BUG ROUND 13 (2026-07-09) — NOT yet built, needs an app build+emulator pass:**
1. **Focus drift on vertical scroll** — down 9 / up 6 / down 6 leaves rows horizontally
   shifted, as if focus entered a row and moved right. (Related to the round-11 §10 item.)
2. **Progress bar + "watched" ✓ hard to see** — increase contrast/size (alpha.25 feature).
3. **Continue Watching shows one row PER EPISODE** — watched 3 eps → 3 tiles of same show.
   Collapse to ONE entry per series (latest position).
4. ✅ **DONE (alpha.39)** — Home scroll position lost on Back. Emulator-proven before/after.
5. ✅ **DONE (alpha.39)** — Season selector jumps. Emulator-proven before/after.
6. **"Next episode" missing on some streams** — the prev/next-episode control doesn't appear
   for every item that has another episode. Make it appear whenever a neighbour exists.
7. **Number/label streams in the stream list** — so the user can tell they're looping back
   through the same streams while trying them.
8. **Loading animation is a still image** — the alpha.30 loading spinner isn't animating.
Config Q answered (stream sorting) — Rachael now cached-only; if still thin, revisit filters.

## ⚠️ READ FIRST (session 21 cont. 6 — 2026-07-08 — alpha.30: in-player resume prompt over a looping loading animation)
Owner batch (3 asks about resume + loading). **alpha.30 (versionCode 30) BUILT —
assembleDebug + testDebugUnitTest GREEN (283) + assembleRelease (R8) clean. NOT
deployed, NOT device-verified.** (DECISIONS #47.)
1. **Resume "no matter what stream/link" — already true, confirmed no code.**
   Progress is keyed by `MediaRef` (video id / meta id), never the stream URL
   (§8.4), so reopening from any stream resumes at the same spot.
2. **New `LoadingAnimation`** (spinning accent arc via `graphicsLayer` rotation —
   layer-phase only, safe on the onn boxes; degrades to a static ring) replaces
   the black buffer screen in the player.
3. **Resume question moved INTO the internal player, over the loader.** During the
   load/test phase (buffering + debrid "resolving" clips) the spinner shows; if
   there's saved progress a **"Resume from X / Start from the beginning"** prompt
   sits over it and **playback is held paused** (owner's pick — no surprise audio;
   stream still buffers so bad links fail fast). Resume holds focus (one OK to go).
   The old pre-launch `ResumeDialog` is kept ONLY for external players (VLC/MX).
⏳ **Owner to do:** deploy alpha.30 to both boxes (S2); eyeball on a real box —
open a partly-watched show, confirm the spinner + the resume prompt over it, that
Resume returns to the same spot and Start-over goes to 0, and that a fresh (never-
watched) item just shows the spinner then plays. (Compose overlays draw above the
video surface, so a screencap during loading should capture them even on the box.)

## ⚠️ READ FIRST (session 21 cont. 5 — 2026-07-08 — USAGE-LIMIT CHECKPOINT: new asks LOGGED, not built)
Owner hit **11% weekly usage** and said "only log this, the next session handles
it." So this is a pure logging checkpoint — nothing built this turn. NEW requests
captured in NEXT ACTION:
- **S6 (app): first-name greeting** — "Hello Rachael, getting your things ready…"
  on load + "Hello Rachael" somewhere visible, so you can tell whose account it
  is. Name already comes from the setup flow.
- **S4 (profile builder): full recommended-addon spec** — Tam-Taro's template +
  Vidhin's regex (from https://docs.aiostreams.viren070.me/configuration/setup/),
  the scraper list from the owner's screenshot (Knaben, Zilean, AnimeTosho,
  Torrent Galaxy, Easynews, SeaDex, NekoBT, EZTV, Bitmagnet, Jackett, Prowlarr,
  NZBHydra2, Newznab, Torznab, Library — as many as feasible), OpenSubtitles V3+
  (Pro), and add any NON-anime addon Adam has that Rachael lacks. ✅ RESOLVED:
  REMOVE anime scrapers (SeaDex, NekoBT, AnimeTosho) — Rachael stays no-anime.
  Details in S4.
Nothing else changed. Below (cont. 4) is the last real build (alpha.29).

## ⚠️ READ FIRST (session 21 cont. 4 — 2026-07-08 — alpha.29: SW toggle shows ON/OFF; English-audio-first live; Rachael provisioning root-caused)
Owner batch. **alpha.29 (versionCode 29) BUILT — assembleDebug + testDebugUnitTest
GREEN (283) + assembleRelease (R8) clean. NOT deployed.** (DECISIONS #46.)
1. **"Software video" toggle now shows ON/OFF** and flips both ways in the
   player's "Having trouble?" panel (was a one-way "Fix blocky video" with no
   state). PlayerViewModel `softwareDecoderOn` + `toggleSoftwareDecoder()`.
2. **English audio first (LIVE on Adam's primary AIOStreams, verified).** The
   `language` sort key was 7th in the sort; moved it to the FRONT of cached +
   uncached so English streams rank first (preferredLanguages already
   English-first). Owner to confirm on the box.
3. **Rachael provisioning — ROOT-CAUSED, NOT applied.** Her thin "~3 addons"
   instance is because `templates/primary.json` has **0 presets** (empty). The
   real fix = build a proper recommended-addons template from Adam's live rich
   config (Comet/MediaFusion/StremThru/Torrentio/Debridio), strip anime + strip
   Adam's keys (leak-safe), then POST-create her backup+nightly AIOStreams.
   **AIOMetadata has NO tooling** — her 2nd AIOMetadata needs new tooling. Did
   NOT blind-apply (empty template = more thin instances; key-leak risk).
⏳ **Owner to do:** deploy alpha.29; confirm English-dub-first on an anime + the
Software-video ON/OFF toggle. Provisioning is the next focused build (S4).

## ⚠️ READ FIRST (session 21 cont. 3 — 2026-07-08 — alpha.28 BUILT: decoder OFF + Having-trouble panel + resume-to-episode; TC pushed live)
Big owner batch. **alpha.28 (versionCode 28) BUILT — assembleDebug +
testDebugUnitTest GREEN (283 tests) + assembleRelease (R8) clean. NOT deployed,
NOT device-verified.** (DECISIONS #45.)
App changes:
1. **Software decoder default OFF again** (reverses the alpha.25 default). Owner:
   software-ON's only downside was a brief self-healing start-up stutter, so
   hardware is default and software is an on-demand in-player fix now.
2. **"Having trouble?" player panel** — the 3 escapes (Try a different stream ·
   Play in another app · **Fix blocky video**) grouped in a captioned accent ring
   + a **Learn more** help dialog (owner's design). "Fix blocky video" = the
   on-demand software-decoder switch: persists the setting then reloads the
   current video via the stream list (a fresh engine applies the decoder — it
   can't be flipped live).
3. **Resume-to-last-episode** — opening a series with history lands on the episode
   you stopped on (or the next one if it's finished): `DetailsViewModel.resumeTarget`
   + `DetailsScreen` scroll-then-focus. Movie Play button now shows "Resume"/"Play
   again" + a progress bar.
   (episode ✓/progress bar + AniSkip already shipped in alpha.25/.26.)
Config (LIVE, verified): **TC added to Adam's PRIMARY AIOStreams** (had to remove a
deprecated `usa-tv` preset that was blocking every save). `excludedQualities` now
`[CAM, TS, SCR, TC]`. **nightly** already had TC; **backup** (weebs) has a stale
stored password — untouched (fix its password or re-create → new manifest URL).
Did NOT disable his Live TV/Events catalogs (stayed surgical; offered as
follow-up). Scratchpad scripts + `primary_backup.json` restore point exist this
session only.
⏳ **Owner to do:** deploy alpha.28 to both boxes; check resume-to-episode +
the Having-trouble panel + "Fix blocky video". Decide priority for the two big
NEXT builds (S3 Trakt scrobble, S4 profile builder — see NEXT ACTION).

## ⚠️ READ FIRST (session 21 cont. 2 — 2026-07-08 — alpha.27 DEPLOYED: fluffy logo fix)
Owner: the Streams "S" looked hard-edged/faceted, not fluffy. **Root cause was
a real bug in the logo generator** (scratchpad `gen_logo.py`): the glyph
extractor's quad→cubic step silently fell through to a LINE approximation, so
every shipped drawable was a ~123-line polygon (7 curves). **FIXED** — the
generator now emits real `Q` (quadratic) pathData (Android VectorDrawable
supports it); the S is 86 curves / 9 lines, genuinely smooth. All 3 drawables
(`ic_launcher`, `tv_banner`, `tv_banner_streams`) regenerated. **alpha.27
(versionCode 27) BUILT + DEPLOYED to .117 + .196** (both confirmed alpha.27) —
this also carried the previously-undeployed alpha.26 (AniSkip) out to the
boxes. Committed + pushed. Desktop preview refreshed
(`~/Desktop/streams-logo-preview.png`).
**BOX ROSTER (owner 2026-07-08): there are only TWO boxes — `.117` (pro) and
`.196`. The old `.231` (non-pro) is NOW `.196` (new DHCP lease); there is no
separate offline box.** Update older STATE mentions of `.231` accordingly.
⚠️ **TV-BANNER CACHE GOTCHA (owner hit this):** the Google TV launcher
(`com.google.android.apps.tv.launcherx`) caches the app banner tile to disk and
does NOT refresh it on `install -r` — the owner kept seeing the OLD faceted S.
FIX = **reboot the box** after any logo/banner change (`adb -s <box> reboot`);
force-stop/`pm clear` of the launcher is unreliable. Did both boxes this
session. NOTE for next session: if the logo is regenerated, `gen_logo.py` must
emit Q/C curves NOT lines (verify the committed drawable is curve-heavy:
`grep -o Q tv_banner_streams.xml | wc -l` should dwarf the L count), AND reboot
the boxes so the new banner actually shows.

## ⚠️ READ FIRST (session 21 cont. — 2026-07-08 — alpha.26 BUILT: AniSkip anime skip + config audit)
Owner replied: **use AniSkip for anime** (not the manual button); plus config
questions (TV/Events, CAM/TS/TC, Rachael); plus "can SW decoder be anime-only?".

**alpha.26 (versionCode 26) BUILT — assembleDebug + testDebugUnitTest GREEN (283
tests, 0 fail) + assembleRelease (R8) clean. NOT deployed, NOT device-verified**
(the AVD can't play their real streams, so AniSkip is unverifiable here — the box
is the test rig). Adds on top of alpha.25 (DECISIONS #44):
- **AniSkip intro/credits skip** — a one-press "Skip Intro"/"Skip Credits" button
  during anime OP/ED. New `player/skip/*` (AniSkip client + Kitsu→MAL resolver +
  SkipTimesRepository), wired into PlayerViewModel (500ms position poll → active
  window) and PlayerScreen (non-focusable hint; OK intercepted globally seeks
  past it). Setting "Skip anime intros & credits" (default ON). Self-limits to
  anime because the data source is MAL-keyed — no genre guessing. **IMDb-only
  anime won't resolve to a MAL id → no button**; every resolution is logged
  ("skip" tag) so adam's box App log will reveal the id format their anime uses.
- (already in alpha.25: SW decoder default ON + episode watch marks.)

**Config audit (INVESTIGATION ONLY — no live push; owner-gated).** Read the
gitignored exports in docs/reference/:
1. **Why TV/Events is on Adam's profile:** his AIOStreams config has `Live TV`,
   `Live Sport Events`, `Other Sports` catalogs ENABLED (empty MediaFusion junk).
   Fix = disable those 3 in the AIOStreams UI. Config, not app.
2. **CAM/TS/TC:** Adam's `excludedQualities` = [CAM, TS, SCR] — **missing TC**.
   Rachael's = [CAM, SCR, TS, TC] (complete). Add TC to Adam's. No in-theaters
   catalog enabled, so TC is the only cinema-junk gap.
3. **Rachael confirmed clean** (Cinemeta + AIOMetadata + AIOStreams + AIOLists;
   all 4 cam qualities excluded; no live catalogs) — she's the model.
**SW-decoder-only-for-anime:** answered NO in chat (unreliable to detect "anime"
per-stream; his symptom is just a brief self-healing startup stutter, the normal
SW warmup). Recommended leaving it ON globally; offered a buffer tweak if the
startup blip annoys him. Not built.
⏳ **Owner to do:** deploy alpha.26 to both boxes; play an anime episode and watch
for the Skip button (+ read adam's box log if it doesn't appear); disable the 3
live catalogs + add TC in his AIOStreams UI (or ask me to prep a gated push).

## ⚠️ READ FIRST (session 21 — 2026-07-08 — alpha.25 BUILT: SW decoder default ON + episode watch marks)
Owner batch of three. **BOX ROSTER CORRECTION (owner): the old `.231` non-pro
box is now `.196`** — same box, new DHCP lease. So there are TWO boxes:
`192.168.1.117` (pro) and `192.168.1.196` (non-pro, formerly .231). Ignore the
older STATE lines that treat .196 as a separate "onn 4K Plus" and .231 as
offline — it's one box that moved. Both already ran alpha.24.

**alpha.25 (versionCode 25) BUILT — assembleDebug + testDebugUnitTest GREEN (272
tests, 0 fail) + assembleRelease (R8) clean. NOT yet deployed, NOT device-
verified.** Shipped (DECISIONS #43):
1. **Software decoder is now DEFAULT ON** (was opt-in). Owner confirmed the
   toggle fixes the anime macroblocking, so it's the out-of-the-box behavior. A
   box that stutters on 4K (the pro box) can turn it OFF in Settings → "Prefer
   software video decoder". One-line default flip in `DataStorePlaybackPrefs`;
   a box that already toggled it keeps its own choice.
2. **Episode watch marks in Details** — a green ✓ badge on finished episodes and
   an accent resume bar (thumbnail bottom edge, or a thin line under the text
   when there's no thumbnail) showing how far you got. Non-obvious: a finished
   episode used to be DELETED from the progress table on end, leaving no ✓ — now
   it's stored at position==duration instead, and the existing 95% line splits
   "resume bar" from "✓" (no Room migration). Updates live when you back out of
   the player.
3. **Intro/credits skipper — ANSWERED, not built.** No free universal timestamp
   source for general movies/TV. For ANIME, the AniSkip API gives real OP/ED
   windows (a true "Skip Intro" button) but is anime-only + its own mini-project
   (MAL-id mapping, network client, position overlay, setting, tests). Left the
   approach choice to the owner (AniSkip-anime vs a manual skip button vs both)
   rather than half-build it. See NEXT ACTION S1.
⏳ **Owner to do:** deploy alpha.25 to BOTH boxes (`install -r` the release APK),
then eyeball — clean anime decode by default (no Settings trip), and the ✓/bar on
a series you've partly watched. Decide the intro-skip approach.

## ⚠️ READ FIRST (session 20 cont. 2 — 2026-07-08 — alpha.24 DEPLOYED + owner batch)
Owner confirmed the **software-decoder toggle FIXES the macroblocking** (Naruto
clean). Then a big feedback batch — some SHIPPED in **alpha.24 (versionCode 24,
deployed to .117 + .196, .231 still offline)**, some ANSWERED, some left as
detailed NEXT-ACTION notes below (ran low on budget). SHIPPED alpha.24:
1. **Default audio = English** (`PlayerViewModel` `languages.audio ?: "en"`).
   Owner hit a dual-audio anime that opened in Italian (first track in the
   file). Preference not filter; a saved language pick still wins; foreign-only
   still plays. Applies to ALL content (English-speaking household).
2. **Player control bar reorder + DOWN focus.** New order L→R: `[⏮ ⏭]`
   icon-only in one compact slot · Audio & subtitles · **Try a different
   stream** (now the DOWN default via `focusProperties{down=...}`) · Play in
   another app. Previously DOWN landed on "Play in another app" (owner bug).
ANSWERED (no code): decoder toggle works → could make it default ON later if
the family wants (kept OFF so 4K doesn't stutter). Curated auto-updating lists
question → see the owner reply / R1: Popular/Trending catalogs from
AIOMetadata/AIOStreams auto-update off TMDB already; Trakt-list curation needs
the AIOList manifest finished (still pending, R1/R3).
⏳ **NOT device-verified**: the audio-default and the new bar layout/focus —
owner to eyeball on the boxes (both on alpha.24). Deploy .231 when it pings.

## ⚠️ READ FIRST (session 20 cont. — 2026-07-08 — alpha.23 DEPLOYED: macroblocking fix + English revert)
Owner reported the real "anime bugs out": a screenshot of heavy colored
**macroblocking** during Naruto playback (NOT the English-audio logic — that
was a mis-diagnosis). This is **N1** — the boxes' hw decoder emits garbage
frames; MX Player (software) is clean. **alpha.23 (versionCode 23) BUILT +
gates GREEN + DEPLOYED** to both ONLINE boxes via `install -r`:
`192.168.1.117` (pro) and `192.168.1.196` ("onn 4K Plus" — a box not in prior
STATE; may be the non-pro on a new DHCP lease or a 3rd box). Both confirmed
0.3.0-alpha.23, smoke-launched (MainActivity resumed, no crash). **`.231`
(non-pro) was OFFLINE (no ping)** — deploy alpha.23 when back:
`adb connect 192.168.1.231:5555 && adb -s 192.168.1.231:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`.
Shipped (DECISIONS #42):
1. **Macroblocking fix.** ExoPlayer now uses `DefaultRenderersFactory` with
   `setEnableDecoderFallback(true)` (always) + a Settings toggle **"Prefer
   software video decoder"** (`PlaybackPrefs.preferSoftwareDecoder`, default
   OFF) that swaps in `MediaCodecSelector.PREFER_SOFTWARE`. Default OFF because
   4K would stutter in software — it's opt-in per box. ⚠️ **The owner must flip
   the toggle ON on the glitchy box (Settings → "Prefer software video
   decoder") and replay the anime — decoder-fallback alone won't fix SILENT
   corruption.** This is the pending verification; MX-parity is the bar. If SW
   still glitches a codec, escalate to proactively surfacing "Play in another
   app".
2. **English auto-play revert (undoes #40a).** Owner: "make any changes that
   wouldn't make the first one to be english." Removed the label-based English
   preference from `rank`/`firstPlayableWhenSettled`/`orderedAlternatives` +
   deleted `isNonEnglishAudio`. Auto-play's "first stream" is the addon-order
   pick again.
3. Also live on these boxes now: **logo v3 "Streams"** + round-12 episode nav.
NEXT verification is owner-eyes on the real TVs (toggle + logo). Both boxes
also got the new launcher tile/banner.

## ⚠️ READ FIRST (session 20 — 2026-07-07 — logo v3 "Streams" rebrand)
Owner: the v2 shadow-S "SStreams" logo is only "decent" → replaced. **Logo v3
BUILT + both gates GREEN (assembleDebug + testDebugUnitTest), committed
`4772832`, NOT versionCode-bumped, NOT deployed.** It reads **"Streams"**: one
bold rounded periwinkle S (`#C6D4F2`) with a royal-blue (`#3B5CA6`) 3D
drop-shadow flowing into white "treams" — a genuine rebrand SStreams →
Streams. Non-obvious (DECISIONS #41): glyphs are now EXACT SF Rounded outlines
(system variable font @ wght 820) extracted via fontTools into vector
`pathData` — the old hand-authored bezier/TextPath pipeline (#38/#39) is gone.
Verified before wiring by rasterizing the composed paths (counters render as
holes). Files: `ic_launcher.xml` (S mark on navy tile), `tv_banner_streams.xml`
(full lockup), `tv_banner.xml` (neutral = S mark only); deleted
`tv_banner_sstreams.xml`; `build.gradle.kts` banner select is now
`brand == "Streams"`; owner-private `local.properties` `setup.brand=Streams`
(so in-app title + launcher label read "Streams"). Preview PNG for the owner
at `~/Desktop/streams-logo-preview.png`. ⚠️ Not device/emulator-verified
visually — the even-odd raster proof + a clean aapt2 compile stand in; eyeball
the launcher tile + banner on the AVD or a box at deploy. **This bundles into
the SAME next deploy as round-12 (English audio + episode nav, still at
alpha.22 undeployed): bump versionCode → alpha.23, then `install -r` to BOTH
boxes** (a bump is MANDATORY or Android blocks the reinstall — session-16
lesson).

## ⚠️ READ FIRST (session 19 — 2026-07-07 — Rachael R1 prep + user cleanup)
Owner batch. **All in the LIVE passport
(`~/Documents/Claude/StremioSurfer/users.json` via the :5000 server, which
auto-writes `users.json.bak`) — NOT the repo:**
1. **Removed Jamie + Myles Dad** (owner: "take him and jamie out, save for my
   records"). 13→11 users. Full records saved to the passport dir
   `removed-users-2026-07-07.json`. They had NO hosted profile files (skip-list
   stubs) — nothing to un-upload; their names still sit in the local
   `profiles.config.json` skip-list (harmless).
2. **Rachael's keys completed:** Real-Debrid added; TMDB api+read already
   matched what the owner sent; Torbox set to the **shared** key. Trakt app
   "Claude" creds (Client ID/Secret, Google-auth note) saved to the passport
   dir `trakt-app-claude.json` (app-level, not per-user).
3. **Family-no-anime template SIGNED OFF** — `docs/TEMPLATE_FAMILY_NO_ANIME.md`
   finalized + committed `418c03f` (no secrets, placeholders only). Decisions:
   shared Torbox, Tamtaro dropped (a person who builds AIOStreams presets, not
   an addon), Trakt deferred to v2.
✅✅ **RACHAEL FAMILY-NO-ANIME CONFIG PUSHED + VERIFIED LIVE (session 19).**
Real exports found in `docs/reference/{Adam,myles}/*-template.json` (owner
created them today). Built `~/Documents/Claude/StremioSurfer/templates/primary.json`
from Adam's main template (stripped anime seadex/neko-bt + debridio + MediaFusion
[leak] + deprecated usa-tv presets; per-user keys→placeholder; enableSeadex=false),
leak-scanned the exact payload, then `push_aiostreams.py --user Rachael --strict`
→ Done. Pulled back: 12 scrapers, no anime, 1080p-first, English, RD=her key,
manifest URL unchanged (box needs no re-add). All 11 users intact; .bak written.
Restored the slot password the tool drops on update. Trakt creds + AIOList URL
are in her record (notes + addons.aiolists_manifest). REMAINING owner-side:
refresh the passport UI (don't Save a stale form or it overwrites the push);
optionally regen her profile (make_profiles) to add the AIOList row + upload;
point her box at it. Also the earlier fact:

✅ **SESSION-16 LOSS SOLVED (session 19):** the 0-byte `templates/*.json` no
longer blocks us — live AIOStreams configs are pullable via the instance API
(`GET {base}/api/v1/user`, HTTP Basic `uuid:password`, `stremio_api.SSL_CONTEXT`).
Pulled both to the PRIVATE passport dir (secrets, not repo):
`templates/pulled-myles-primary.json` (73 KB reference) +
`templates/pulled-rachael-primary.json` (8 KB, her current = backup). Config
lives at `data.userData`. Her current config is NOT family-tuned (2160p-first;
should be 1080p; her presets already carry no anime scrapers).
⏳ **REMAINING = one live PUT** to Rachael's instance via `push_aiostreams.py`
(`PUT {base}/api/v1/user`, body `{uuid,password,config:<userData>}`; `--dry-run`
first). Exact schema-grounded field changes + finish sequence in TEMPLATE §7a.
Deferred the write itself: owner at ~6% weekly usage — do it in a full-budget
pass so the PUT can be verified, not gambled. Nothing else for her is pending.
AIOList: her `addons` holds only short labels, NOT a real manifest URL — she
just signed into her Trakt on Chrome, so the aiolists URL still needs
generating + pasting. Editor for users = http://127.0.0.1:5000/ (passport.html).

## ⚠️ READ FIRST (session 18 — 2026-07-07 — round-12: English audio + player episode nav)
Owner round-12 batch (DECISIONS #40). Two app features BUILT + unit-tested
(`assembleDebug` + `testDebugUnitTest` GREEN), **NOT yet emulator/device-
verified, versionCode NOT bumped (still alpha.22)**:
1. **English audio unless foreign-only.** `StreamCascade.isNonEnglishAudio`
   (conservative label heuristic: flags a stream non-English only when it
   names another language AND gives no English/dual/multi signal — untagged
   stays English-friendly). Wired into what AUTO-PLAYS + the "Try a different
   stream" walk, NOT the visible list (owner chose "auto-play + try-another
   only"; visible list keeps addon order, §4.1.7): new top tier in
   `StreamCascade.rank`, English-preferring `firstPlayableWhenSettled`,
   English-first `orderedAlternatives`. Foreign-only fallback everywhere.
   Anime = no special case (owner: "prefer English dub").
2. **Player ⏮ Previous / Next ⏭ episode buttons.** `NextEpisode.previousBefore`
   + PlayerViewModel resolves the episode list (AutoplayGateway.resolveMeta)
   and opens the neighbour's stream list via the existing `onOpenStreams`
   path. Shown only when a neighbour exists.
Owner still owns two non-code deliverables from this batch (answered in chat,
nothing to build): (a) a PROMPT to hand another AI to design AIOStreams +
AIOMetadata templates (Standard/NSFW/Anime); (b) the "add a person without
AI" flow = add to the passport/users.json → `tools/make_hosting_bundle.py`
→ upload to /setup → type their name on the box.
⚠️ NEXT deploy should bundle these two features + the still-open round-11
items (below) and bump versionCode. Emulator-verify the English auto-pick
and the ⏮/⏭ buttons on a real Cinemeta series first.

# STATE — updated 2026-07-07 by session 17 (afternoon — HANDOFF TO NEXT MODEL)

## ⚠️ READ FIRST (session 17 cont. — 2026-07-07 — ROUND 11 logged, alpha.22 logo v2)
**This is a HANDOFF checkpoint: the owner expects the next session (likely
Opus 4.8) to execute the round-11 backlog below with no re-planning.**
Facts:
1. **alpha.22 (versionCode 22) = logo v2** — owner disliked v1 ("SS
   SStreams"); rebuilt per his words: S's much closer (teal S reads as the
   blue S's SHADOW, almost one letter) and the mark flows into "treams" so
   the banner lockup itself reads "SStreams". Icon = tight shadow-S.
   DEPLOYED + smoke-passed on the PRO box (.117, versionName confirmed
   0.3.0-alpha.22). ⚠️ **.231 (non-pro) was OFFLINE (no ping) — still on
   alpha.21.** When it's back:
   `adb connect 192.168.1.231:5555 && adb -s 192.168.1.231:5555 install -r
   app/build.nosync/outputs/apk/release/app-release.apk` (rebuild release
   first if the tree moved past alpha.22).
2. **Round-11 owner feedback is FULLY LOGGED in MASTER_PLAN §10 ("Owner
   feedback round 11")** — that list IS the work queue: Discover focus
   drift on up-scroll, poster art re-loading on scroll-back (Coil memory
   cache), held-d-pad scroll perf, **video macroblocking artifacts that MX
   Player doesn't show (owner screenshot; MX-parity is the bar)**,
   resume-to-last-watched-episode in Details, hold-to-accelerate
   scrubbing, prev/next-episode player buttons, and the migration-ready
   per-person profiles project (investigate AIOStreams instances, curate
   catalogs, 4 templates — owner approves before applying; NEVER touch the
   family's current Stremio configs).
3. **Interface-language switcher: owner said skip** ("all english for
   now") — polish/beauty/efficiency/stability instead.
4. Hosting: owner uploaded `setup-upload-trim/` (the correct, newest
   bundle — confirmed to him). The passport server (127.0.0.1:5000) was
   returning HTTP 500 on every route this day — restart/debug it before
   trusting reads; gitignored hosting profiles are a fallback for manifest
   URLs.
5. Owner said Fable's usage window ends today; effort was set medium for
   the last stretch. Everything below was left committed, pushed, green.

## ⚠️ (prior) READ FIRST (session 17 — 2026-07-07 — alpha.21 DEPLOYED to both boxes)
Round-10 wave 4, all built+deployed (DECISIONS #38, TESTLOG 2026-07-07):
(1) **Ambient per-section backgrounds** — deep-tint washes (Home blue /
Discover teal / Search violet / Settings slate / Connect warm), draw-phase
only; media surfaces stay flat by design. Emulator-verified per section.
(2) **Interface sounds** — soft focus tick + select dink (generated WAVs,
SoundPool), key-down driven via MainActivity.dispatchKeyEvent, held-repeat
throttled 90ms, suppressed while the player is up; Settings > "Interface
sounds" toggle default ON. ⚠️ Audibility itself is owner-ears-only — adb
can't hear; if the family hates them, one Settings click kills them.
(3) **Dual-S brand art** — the owner's spoon-nested SS concept as vector
drawables: launcher icon (mark) + TV banner; new `appBanner` manifest
placeholder picks the SStreams wordmark banner when setup.brand=SStreams,
neutral mark otherwise (repo unbranded). Verified rendering in the Google
TV launcher Apps row. (4) **R4 Live TV/Events CLOSED, no app bug** — the
owner's MediaFusion instance serves `metas: []` for live_tv +
live_sport_events (tested every genre/skip variant, direct fetch);
AIOStreams' entries wrap that same empty source; football-under-Movies is
MediaFusion declaring "Other Sports" as type=movie. Fix rides the addon
trim (#37) + R1 templates. ALSO: R2(b) confirmed closed — PosterCard is
shared by Home/Discover/Search, so the alpha.19/20 title reveal covers
Discover. 262 tests green. Both boxes CONFIRMED versionCode 21 /
0.3.0-alpha.21, smoke-launched, sent back HOME. Owner to eyeball: section
colors, sound feel/volume, the new launcher tile. Box log pipeline
LIVE-CONFIRMED: adam's box log fetched from the setup site (two
AIOMetadata 504s in it, harmless).

## ⚠️ (prior) READ FIRST (session 16 latest — 2026-07-07 — alpha.20 DEPLOYED to both boxes)
Third fix wave, DEPLOYED to both onn boxes (.231, .117 both confirmed
versionCode 20): (1) **Back from the player now lands on Details/episode,
not the stream list** — the remote BACK was only intercepted during the Up
Next countdown (`BackHandler(enabled = isCancellable)`), so a normal press
fell through to the nav default (single pop onto Streams); BACK now always
routes through `onExit` (the pop-THROUGH-streams logic on-screen buttons
already used). (2) **Going Home pauses playback** — `ON_STOP` lifecycle
hook pauses ExoPlayer when the app backgrounds (media service otherwise
keeps it alive); in-app nav doesn't fire ON_STOP; default on, no toggle.
(3) **Held up/down no longer glitches poster titles** — reveal now waits
for focus to REST ~120ms (fast d-pad hold flies past without firing) + fades
via `graphicsLayer` draw-phase (no recomposition during scroll), DECISIONS
#22. 256 tests green (first run hit the known HomeViewModelTest Main-
dispatcher flake; cleared on rerun). Smoke-passed on .231 (no crash,
MainActivity resumed). versionCode/Name = 20 / 0.3.0-alpha.20. Commit
`af4083a`. NOT emulator/visually verified beyond the smoke launch.
**Skip intro/credits: explained, NOT built** — recommended AniSkip API for
anime (needs MAL-id resolution + timed overlay + setting; its own session)
or a crude manual "+85s" button as a stopgap; owner to choose.

## ⚠️ READ FIRST (session 16 late — 2026-07-07 — alpha.19 DEPLOYED to both boxes)
Second app-fix wave of the night, DEPLOYED (not just committed) to both onn
boxes: poster/Continue-Watching title reveal-on-focus (was: artwork covering
titles), Settings > Expert mode > "Reset this TV" (confirm dialog; clears
addons + saved setup link, back to name-setup), Connect screen "Skip for
now" replaced with a submit-only "Continue" button, player "Try a different
stream" made always-available + explicitly focused (was: vanishing +
DOWN-navigation landing on "Play in another app" instead), and
`tools/make_profiles.py` addon trim + reorder (MediaFusion/TMDB dropped as
redundant with what AIOStreams wraps internally, AIOMetadata+AIOStreams
moved right after Cinemeta). Full detail: DECISIONS #37, MASTER_PLAN §10
"Owner asks 2026-07-07". 256 tests green both builds. **versionCode 19 /
0.3.0-alpha.19 — both boxes (.231, .117) confirmed on this version, smoke-
tested (no crash, MainActivity resumed).** NOT emulator/visually verified —
owner to eyeball on the real TVs.
Also regenerated + staged (not yet uploaded) a trimmed hosting bundle at
`~/Desktop/setup-upload-trim/` — all 11 existing profile filenames
preserved (verified against the live `profiles.config.json`), so uploading
it will NOT break any box's saved setup link.
**Three items are genuinely blocked on the owner, not code:** (1) the 4
saved AIOStreams templates (`templates/*.json`) are 0 bytes everywhere on
this Mac — real content never saved or lost, stray macOS alias files next
to them resolve back to the same empty files; (2) comparing Rachael's vs
the owner's live AIOStreams service/addon config needs eyes inside both
UIs, not file/API access; (3) AIOMetadata is still empty for everyone but
Rachael (pre-existing, needs the owner's per-person accounts).

## ⚠️ READ FIRST (session 16 — 2026-07-07 — round-10 R2 wave BUILT + landed, build+tests GREEN)
Session 16 (Fable 5) spawned a background coder that built the first wave of
round-10 app fixes; Fable 5 merged them to `main` and pushed, then hit its
usage limit DURING the merge/verify step — before updating this file. This
session picked up: re-ran the build, confirmed green, and wrote this
checkpoint. The 5 landed commits (`8393b01..321f794`) are **build-verified
(`assembleDebug`) + unit-tests GREEN (`testDebugUnitTest`) this session, but
NOT yet emulator/visually verified:**
1. `379c0fe` — search mic: crisp vector `MicIcon` replaces the emoji glyph.
2. `9fda76a` — **R2(a) DONE:** DOWN from the hero/a row lands on that row's
   FIRST item, not mid-row (Home + Discover + ContinueWatchingCard).
3. `481f4a2` — **R2(c) DONE:** filter-bar chips — selected state now clearly
   distinct from focus + backglow (Surfaces.kt + DiscoverScreen).
4. `a8be757` — **DONE (owner's stated #1):** Details gets a sticky season
   indicator while scrolling episodes + per-episode thumbnails & synopses.
5. `321f794` — **DONE (easy-mode):** a movie **Info screen** before playback
   for movies / no-episode items only; Back from a playing video now returns
   to the Info/episode screen instead of the raw stream list.
STILL OPEN in R2: **R2(b)** the focused-CARD redesign (poster art must not
cover the title — title pushed down inside a border extending from the
artwork, silky under fast d-pad travel). NOT in these commits.
⚠️ NEXT deploy is gated on an emulator/visual pass of these 5 (especially
easy-mode nav + the Info screen + episode thumbnails). versionCode NOT
bumped; boxes still target alpha.17.
Dev-tooling note (NOT app code): this session installed 4 Claude Code
plugins/skills globally in `~/.claude` — superpowers (enabled for MastaP),
ui-ux-pro-max, frontend-slides, web-asset-generator. web-asset-generator is
earmarked for R3 (SStreams launcher-icon / favicon art).

## ⚠️ READ FIRST (session 15 — round-10 feedback LOGGED, phase 3 CLOSED, site LIVE)
Session 14 ran out of usage MID-CHECKPOINT while logging owner feedback
round 10; session 15 (new account) completed the checkpoint. **No round-10
work is built yet** — it is fully logged in MASTER_PLAN §10 ("Owner feedback
round 10") and prioritized in NEXT ACTION below. Facts established:
1. **Phase 3 is CLOSED.** Owner declared gate D PASS ("Mark D as done") →
   §7.2 gate ticked, tag `phase-3-done` created + pushed.
2. **The setup site is LIVE.** Owner uploaded the regenerated `api=1`
   index.php and created `logs/`. LIVE-VERIFIED this session: POST api=1
   "adam" → correct link; "myles" → two choices. Name-setup on real boxes
   AND the alpha.17 daily log upload are now fully unblocked — the
   long-standing "owner must upload index.php" item is DONE.
3. **Rachael is NOT in users.json** — her round-9 dashboard add never
   saved. Her onboarding (Family-no-anime template test, non-pro onn box,
   test creds) is in the gitignored
   docs/reference/StremioSurfer/rachael-onboarding.md. NEVER put her creds
   in tracked files.
4. **Myles rename has a data conflict**: users.json already holds a skipped
   `Myles Dad` stub next to the live `Myles Manuel` (active RD premium) —
   owner must confirm the merge before renaming (MASTER_PLAN §10 round 10).
5. Ambiguous-name UX already exists ("Which one are you?" chooser, session
   13) — round 10's "ask kindly to specify" is already satisfied; only the
   rename remains.
6. Boxes are on **alpha.16**; deploy target is **alpha.17** (adds the daily
   log upload). Owner-side hardware steps: deploy alpha.17, reconnect each
   box once (Settings → Connect this TV), confirm hold-UP with the remote.

## (prior) READ FIRST (session 14 day 2 cont. — alpha.17: daily log upload SHIPPED)
Owner round 9 (2026-07-06): (a) "weebs addon missing" DIAGNOSED, no app bug —
the live profile HAS it (named "[BAK]AIOStreams"), servers up, boxes already
on alpha.16 (owner deployed!), but the box predates the addon + has no saved
setup link (pre-alpha.10 install) → ProfileSync idle. FIX = owner reconnects
each box once (Settings → Connect this TV). (b) **Daily App-log upload
SHIPPED (alpha.17, DECISIONS #35)**: DiagnosticsUpload posts each box's
sanitized log to index.php api=log once/day → site stores
logs/<profile-stem>.log; VERIFIED end-to-end against a contract mock
(TESTLOG). Hosting bundle REGENERATED with api=log — the owner's ONE pending
index.php upload now enables BOTH the name-setup api and log receiving.
252 tests, R8 smoke PASS, versionCode 17. Deploy target:
`app-release.apk` **alpha.17**. AVD now holds a mock ProfileLink (harmless,
see TESTLOG); CW rows were reset.

## (prior) READ FIRST (session 14 day 2 cont. — alpha.16: error log SHIPPED)
Three arcs closed this stretch, all emulator-verified on a cold-booted AVD:
(1) round-7 Details VISUALLY VERIFIED (+ "Episode 1 · Episode 1" dedupe);
(2) the owner's **Home hold-UP stick** root-caused + fixed structurally —
header is now the LazyColumn's item 0 (DECISIONS #33); ⚠️ genuine remote
key-repeat is NOT simulatable via adb (proven, see #33) — this fix needs the
OWNER'S REMOTE for final confirmation. (3) **Phase 4 owner directive
SHIPPED: on-device error log (DECISIONS #34)** — `DiagnosticsLog` (URL-
sanitized file, trimmed) + Settings → Expert mode → **App log** viewer;
catalog/stream/meta/player/profile-sync failures recorded with addon
context; ALSO fixed a Phase-1-era token leak (AddonClient/SetupProfile
logged full request URLs to logcat). Player errors now log their raw
codec story — the box's "Naruto won't play internally" (NEXT ACTION 1b)
will self-document in App log after the next deploy, no adb needed.
Current build **alpha.16** (versionCode 16), 243 tests green, R8 release
smoke PASS. Deploy target: `app-release.apk` **alpha.16**. The setup name
flow on real boxes is STILL gated on the owner re-uploading the `api=1`
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
**Phase 3 COMPLETE + tagged `phase-3-done`** (A PASS 07-04, B PASS 07-05,
C on sentiment, D owner-declared PASS 07-06 round 10). Phase 4 in
progress: units 1–4 + error log + daily log upload shipped (sessions
11–14); the active backlog is owner feedback ROUND 10 (MASTER_PLAN §10) —
Discover focus/filter fixes, SStreams rebrand, ambient background, UI
sounds, live-TV/events cleanup, 4 addon templates + Rachael onboarding.

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv)

## Just finished
- **Session 16 cont. (2026-07-07) — DEPLOYED alpha.18 (versionCode 18) to BOTH
  boxes (192.168.1.231 non-pro + 192.168.1.117 pro) via adb `install -r`.**
  This is the FIRST build carrying the round-10 wave + anime numbering +
  SStreams rename to real hardware. ROOT CAUSE of "the easy-mode back-fix and
  episode photos aren't working": the boxes ran an OLD alpha.17 — versionCode
  was never bumped past 17, so Android blocked every `install -r` and the new
  code never reached them. Verified in source this session that the fixes ARE
  correct (AppNavHost easy-mode Back pops THROUGH Streams to Details/episode;
  DetailsScreen renders episode thumbnail+synopsis; streams stay in AIO order,
  autoplay picks first-playable in that order). R8 release built clean,
  smoke-passed on .231 (no crash, MainActivity resumed, process alive; TV
  `screencap` returns black = hardware-overlay limitation, NOT a render bug).
  **Owner to eyeball on the real TVs:** easy-mode Back from a playing movie →
  Info screen (not the stream list); episode thumbnails/synopses on a
  Cinemeta-sourced series; the SStreams name. versionCode/Name = 18 /
  0.3.0-alpha.18.
- **Session 16 cont. (2026-07-07) — anime episode-numbering toggle + SStreams
  rename (DECISIONS #36). Built, 256 tests green, pushed `a4b09b8`.** Settings
  > "Episode numbering": per-season vs straight-through absolute (Ep 115),
  computed client-side (`absoluteEpisodeNumbers`, specials excluded) — verified
  on real Naruto data (S3E32 = abs 115). Details episode list only
  (player/UpNext titles unchanged — follow-up for full consistency). App
  renamed **SStreams**: launcher label is now a `${appLabel}`
  manifestPlaceholder from local.properties `setup.brand` (repo default stays
  "OpenStream TV"); in-app title already used it. NOT emulator-verified;
  versionCode still alpha.17 (bump at deploy). ALSO this session: **Rachael
  reconciled + completed** — her AIOMetadata merged into the LIVE passport
  (POST /api/users; all 13 users intact), hosting bundle regenerated (index.php
  + `rachael-wv_EExTgN6I.json`, every existing filename preserved) staged at
  `~/Desktop/setup-upload/` for owner upload → then type "rachael" on the
  non-pro box (192.168.1.231). 4 Claude Code plugins installed globally.
  **Data-truth:** the passport server (127.0.0.1:5000,
  `~/Documents/Claude/StremioSurfer/`) is the SINGLE SOURCE OF TRUTH for
  users.json; the repo `docs/reference/StremioSurfer/users.json` is a STALE
  snapshot and `make_profiles.py`/`make_hosting_bundle.py` DEFAULT to it —
  pass `--users <live file>`.
- **Session 15 (2026-07-06 evening, new account) — round-10 checkpoint
  COMPLETED (docs only, no code).** Session 14 died mid-checkpoint (usage):
  it had ticked the §7.2 gate in MASTER_PLAN (gate D = owner-declared PASS)
  and written the gitignored rachael-onboarding.md, but the STATE.md
  round-10 write and the `phase-3-done` tag never happened. This session:
  logged the FULL round-10 backlog into MASTER_PLAN §10 + NEXT ACTION,
  created/pushed `phase-3-done`, and VERIFIED live that the owner's
  index.php upload works (api=1 "adam" → link; "myles" → choices — the
  name-setup flow + log drop-off are live). Confirmed Rachael absent from
  users.json (dashboard save didn't stick) and found the `Myles Dad` stub
  conflict blocking the Myles rename. Code answers pulled for the owner:
  first-name-only match is by design; ambiguous names already get the
  "Which one are you?" chooser; the Home hero = first item of the first
  catalog row (works with zero Trakt history); duplicate catalogs come
  from addons configured INSIDE AIOStreams, so disabling everything but
  AIOStreams+AIOMetadata is fine.
- **Session 14 day 2 (cont. 4, 2026-07-06) — alpha.17: daily log upload +
  weebs diagnosis (DECISIONS #35).** New `DiagnosticsUpload` (+ prefs) runs
  beside ProfileSync on app start: ≤1 successful upload/24h, POST api=log +
  who=<profile-stem> + sanitized log text; index.php (make_hosting_bundle)
  gained the api=log receiver (validates who against existing profile files,
  128 KB cap → logs/<stem>.log, overwrite). 252 tests (9 new). END-TO-END
  emulator-verified vs a mock (recreate from scratchpad description in
  TESTLOG if needed): connect-by-name → link saved → failure logged →
  relaunch → upload received; throttle held on second relaunch. Hosting
  bundle regenerated --brand SavoyStreams (has BOTH api=1 and api=log).
  Weebs-addon report diagnosed as stale box state, not a bug (see READ
  FIRST). Boxes discovered ALREADY on alpha.16 (owner deployed since
  the morning) — only reconnect + index.php upload remain owner-side.
- **Session 14 day 2 (cont. 3, 2026-07-06) — alpha.16: Phase 4 on-device
  error log (MASTER_PLAN §10 ticked, DECISIONS #34).** New
  `diagnostics/DiagnosticsLog` (plain file, newest-last, 400→300 trim,
  EVERY line URL-sanitized before disk — tokens) + `DiagnosticsSink` fun
  interface (defaulted no-op param → Hilt injects the real one, the 8
  direct-construction test files untouched). Wired at the repository
  chokepoints (Catalog/Stream/MetaRepository, ProfileSync) +
  PlayerViewModel; PlayerEvent.Error gained a log-only `detail`
  (errorCodeName+cause → codec diagnosis for 1b without adb). Settings →
  Expert mode → "App log" (focusable monospace lines, Clear log). Token
  leak fixed: OkHttpAddonClient/SetupProfile no longer put URLs/exception
  messages in logcat. VERIFIED live: dead fixture server → friendly chip
  on screen, sanitized detail in the file AND in the App log screen
  (screenshots; TESTLOG). 243 tests (7 new), R8 smoke PASS. versionCode
  16 / alpha.16. AVD restored (expert OFF, fixture server back up).
- **Session 14 day 2 (cont. 2, 2026-07-06) — alpha.15: Details verified +
  Home hold-UP stick fixed (DECISIONS #33).** Cold-booted AVD. (a) Round-7
  Details changes visually verified on the fixture series (screenshots in
  transcript; TESTLOG); added an episode-label dedupe ("Episode 1 ·
  Episode 1" → "Episode 1" when the addon titles episodes that way).
  (b) Home hold-UP glitch: repro attempts proved adb CANNOT generate real
  key-repeat (discrete pairs only; `emu event send` never reaches the input
  pipeline — dumpsys-verified; computer-use can't allowlist the qemu window,
  no bundle id). Root-caused by mechanism instead: pinned header outside the
  LazyColumn = focus escape mid-scroll cancels bring-into-view with nothing
  left to finish it → half-scrolled rest state. Header moved INSIDE the list
  (item 0) + branch-aware entry-focus re-request. Emulator-verified: entry
  focus/hero intact, 12×UP burst from 6 deep settles at true top, header
  scrolls away while browsing (intended). 236 tests, R8 smoke PASS.
  versionCode 15 / alpha.15. **Owner must confirm the hold-UP feel with a
  real remote after deploying alpha.15.**
- **Session 14 day 2 (cont., 2026-07-06) — Player control-bar REBUILT +
  no-flash auto (alpha.14, DECISIONS #32). Emulator-verified.** Owner-confirmed
  "build the full bar". PlayerScreen.kt rewritten: clean while playing → any
  key wakes a control bar with a focusable scrub bar (accent ring; ◀▶ seek
  ±10s; OK play/pause) → DOWN to plain-word buttons "Audio & subtitles" /
  "Try a different stream" (only if another stream) / "Play in another app"
  (only if VLC/MX installed); auto-hides 5s; error panel carries the same two
  escapes. PlayerViewModel gained externalPlayers + externalIntentForCurrent
  (hands the current stream to VLC at the current position; pauses our engine).
  Auto mode no longer flashes the stream list (UiState.autoStarting → calm
  "Starting…"). VERIFIED on cold-booted AVD w/ the Local Test fixture:
  autoplay-no-flash, wake, scrub+seek (0:30→1:22), play/pause, focus nav,
  labelled buttons (screenshots). TracksDialog + external launch reuse proven
  code (VLC unrenderable on the AVD). 236 tests, release built. versionCode 14.
  ~~STILL PENDING a cold-boot visual pass~~ → both items CLOSED in the
  alpha.15 entry above.
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
  **STILL OPEN from this feedback round** — ALL THREE now closed:
  1. ✅ Round-7 Details visual verify — done (alpha.15 entry above).
  2. ✅ Home scroll-up glitch — root-caused + fixed structurally
     (DECISIONS #33; owner-remote confirmation pending).
  3. ✅ **Player controls redesign — OWNER CONFIRMED "build the full bar"
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

### ⭐ START HERE (session 23 — 2026-07-10)
Z0. ⏳ **DEPLOY alpha.40 to BOTH boxes** (supersedes A0's alpha.39 — same commands, same warning:
   ask the owner first, someone may be watching). Build `assembleRelease` first.
Z1. ⏳ **Owner verifies the player on a box** (the point of alpha.40):
   (a) play an anime/HEVC title that used to rainbow-macroblock — it should open CLEAN with
   "Having trouble? → Software video: ON" showing automatically (no toggle trip);
   (b) hold RIGHT on the scrub bar — the preview should fly with accelerating steps and the video
   should jump ONCE on release (no per-press grind), with a small ring while it rebuffers;
   (c) pause → the control bar must STAY up; (d) flip "Software video" — the video should reload
   in place at the same position, not bounce through the stream list.
   If a title still macroblocks WITHOUT the toggle reading ON, the box's decoder is lying about
   that codec's profile — read the App log, then widen the label heuristics or trust-list that codec.
A0–A4 (session 22) continue below — R13-1/B2 still need the owner's remote.

### ⭐ (prior) START HERE (session 22 cont. 6 — 2026-07-10)
A0. ⏳ **DEPLOY alpha.39 to BOTH boxes.** This is the single highest-value action left — the boxes are
   still on alpha.30, so nine releases of fixes (.31–.39) have never been seen by the family, and every
   owner bug report is being made against alpha.30. Build the release APK, then:
   `adb connect 192.168.1.117:5555 && adb -s 192.168.1.117:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
   (repeat for `192.168.1.196`). A versionCode bump is MANDATORY or Android refuses the reinstall.
   `.117` was reachable over adb on 2026-07-10. **Ask the owner before installing** — these are the
   living-room TVs and someone may be watching.
A1. ⏳ **R13-1 focus drift on vertical scroll** (down 9 / up 6 / down 6 leaves rows shifted right) and
   **B2 hold-UP scroll on Home + Discover**. Both need the OWNER'S REMOTE — adb cannot simulate genuine
   key-repeat (proven, DECISIONS #33). Approach for B2: Discover likely never got the #33 structural fix
   (header/hero as LazyColumn item 0); compare `HomeScreen.kt` (fixed) against `DiscoverScreen.kt`.
A2. ⏳ **Remove Home's redundant Discover/Search/Settings pills** now that the NavRail (alpha.38) owns
   those. Blocked on re-anchoring entry focus: the pills carry `headerFocus`, which alpha.39's restore
   path also falls back to. Do it with an emulator pass (the harness notes in READ FIRST make this cheap).
A3. ⏳ **Verify NavRail focus on the emulator**: LEFT-from-content → rail, and rail → content.
   Never emulator-verified since alpha.38.
A4. Then the two big pre-existing builds, unchanged: **S3 Trakt scrobbling** and **S4 rich
   multi-instance profile builder** (both specced below).

### ⭐ OWNER BATCH 2026-07-08 (session 21) — DONE + one owner decision open
S0. ✅ **Software decoder DEFAULT ON** (was B4/N1) — shipped alpha.25.
S0b. ✅ **Episode watch marks** (progress bar + ✓ on finished episodes) — shipped
   alpha.25. DetailsScreen episode rows; `ProgressRepository.isWatched` +
   `observeProgressByExternalId`; Ended now stores completed instead of clearing.
S1. ✅ **AniSkip anime intro/credits skip — BUILT (alpha.26, DECISIONS #44).**
   Owner picked AniSkip (declined the manual button). `player/skip/*` +
   PlayerViewModel/Screen wiring + Settings toggle. Self-limits to anime (MAL-
   keyed data). ⏳ Verify on a box with a real anime episode; if no button
   appears, read adam's box App log ("skip" tag) — it says whether the anime id
   resolved to a MAL id. If their anime is IMDb-sourced, add an IMDb→MAL mapping
   (anilist: resolver is also stubbed — finish if needed).
S1b. ⏳ **Owner's config fixes (his AIOStreams UI, or ask me to prep a gated
   push):** disable `Live TV` + `Live Sport Events` + `Other Sports` catalogs;
   add `TC` to excludedQualities (currently [CAM, TS, SCR]). Rachael's config is
   the clean model. INVESTIGATION done this session; no live write made.
S2. ⏳ **Deploy alpha.30** to BOTH boxes (.117 pro + .196) — bundles .28/.29/.30:
   `adb connect 192.168.1.117:5555 && adb -s 192.168.1.117:5555 install -r app/build.nosync/outputs/apk/release/app-release.apk`
   (repeat for .196). Verify: **the new loading spinner + in-player "Resume /
   Start from the beginning" prompt** (open a partly-watched show), resume-to-last-
   episode, the "Having trouble?" panel + the **Software video: ON/OFF** toggle,
   English-dub-first on an anime (the AIOStreams sort change is live), the AniSkip
   button on an anime OP/ED.

S3. ⏳ **Trakt scrobbling — SPECCED, BUILD NEXT (app).** Owner wants Stremio-style
   scrobbling. Plan (DECISIONS #45): Trakt **device OAuth** (type a code at
   trakt.tv/activate — "Claude" app creds already in the passport), token in
   DataStore; `TraktScrobbler` maps the item to a Trakt id (IMDb tt… is native to
   Trakt — no MAL-style mapping pain) and POSTs scrobble start/pause/stop off the
   existing player events (stop at ended/≥80% = watched); Settings "Connect
   Trakt". Its own build.
S4. ⏳ **Rich multi-instance profile builder — BUILD NEXT (StremioSurfer tooling).**
   Topology (owner, in order): **2 AIOMetadata** (elfhosted, then nhyira
   "fortheweak" — new) + **3 AIOStreams** (fortheweak.cloud, weebs/midnightignite,
   elfhosted). Gap analysis (session 21): most people have 2 AIOStreams + 0
   AIOMetadata; Rachael has 1+1; Adam 3+0. **KEY FINDING: `templates/primary.json`
   has 0 presets — empty — which is why provisioned instances are thin (~3
   addons).** Concrete build steps:
   (1) Pull Adam's LIVE primary config (21 rich presets: Comet, MediaFusion,
       StremThru, Torrentio, Debridio, OpenSubtitles…) → make it the recommended
       AIOStreams template. Strip anime (seadex, neko-bt) for the family template;
       strip ALL of Adam's personal keys (push_aiostreams substitutes per-user
       keys + validates vs the owner to catch leaks — NEVER leak Adam's keys).
   (2) `push_aiostreams.py --template <new> --instance backup|nightly --user
       "Rachael"` (dry-run first) — POST-creates her missing AIOStreams, writes
       uuid/manifest back to users.json. instances map + her keys are ready.
   (3) **AIOMetadata: NO tooling exists** (no push_aiometadata; make_profiles
       skips it). Build it (reverse-engineer the AIOMetadata configure API, same
       GET/PUT-with-Basic shape as AIOStreams likely) to create her 2nd
       AIOMetadata + beef the thin 1st.
   (4) Regenerate her hosting profile (make_profiles + upload) so the box picks up
       the new manifest URLs; box re-syncs → shows all addons (fixes "only 4
       addons"). Then repeat per person.
   **RECOMMENDED-ADDON SPEC (owner 2026-07-08, from https://docs.aiostreams.viren070.me/configuration/setup/):**
   - Use **Tam-Taro's template** (Stream Expression Language filtering + sorting)
     + **Vidhin's Regex Patterns** (Trash Guides release-group rankings) — this is
     the "configuration in the 2nd pic / Template Credits". NOTE: this REVERSES
     the session-19 "Tamtaro dropped" call (memory addon-endgame) — owner now
     wants Tam-Taro's config. tam.taro on Discord; ko-fi.com/tamtaro, ko-fi.com/vidhin.
   - Add **as many scrapers as possible** from the owner's screenshot (1st pic),
     visible list: **Knaben, Zilean, AnimeTosho, Torrent Galaxy, Easynews Search,
     SeaDex, NekoBT, EZTV, Bitmagnet, Jackett, Prowlarr, NZBHydra2, Newznab,
     Torznab, Library** (list was scrollable — may be more). Add the ones that
     need no extra infra; the indexer-proxies (Jackett/Prowlarr/NZBHydra2/Newznab/
     Torznab) + Easynews need the family's own indexer URLs/keys — add only if
     available, else note as skipped. ✅ **RESOLVED (owner 2026-07-08): REMOVE the
     anime scrapers — SeaDex, NekoBT, AnimeTosho — from Rachael's list. She stays
     family-no-anime.** So her scraper set = Knaben, Zilean, Torrent Galaxy,
     Easynews, EZTV, Bitmagnet, (Jackett/Prowlarr/NZBHydra2/Newznab/Torznab if
     indexer keys exist), Library + the non-anime ones from Adam's config below.
   - Subtitles: **OpenSubtitles V3+ (Pro)** (owner: "OpenSubtitles pro v3" — type
     `opensubtitles-v3-plus`, which Adam's config already uses).
   - Also **diff Adam's live AIOStreams presets vs Rachael's and add any NON-anime
     addon Adam has that she lacks** (owner request). Adam's 21 presets incl Comet,
     MediaFusion, Torrentio, Sootio, Meteor, Knaben, STorz(torznab), TorrentsDB,
     Debridio+Watchtower, OpenSubtitles(V3+), Streaming Catalogs, Library — plus
     the anime ones (SeaDex/nekoBT) to leave out for a no-anime profile.
S5. ⏳ **Adam config follow-ups (his AIOStreams UI or a gated push):** disable
   `Live TV` + `Live Sport Events` + `Other Sports` catalogs on primary; fix the
   **backup** (weebs) instance password so it can be managed (or re-create it —
   new manifest URL → re-add on the box). TC is DONE on primary + nightly.
S6. ⏳ **App: show whose account it is (owner 2026-07-08, NEW).** Display the
   person's FIRST NAME: a warm loading line like "Hello Rachael, getting your
   things ready…" while the account/profile loads, and "Hello Rachael" somewhere
   visible in the UI (e.g. Home header / Settings). Owner: "just need a way to
   see whose account it is, thought it'd look nice." The name is already known —
   the setup name flow (ConnectViewModel/SetupNameLookup) captures the typed
   name; persist the first name (SetupConfig/ViewPrefs) and surface it. Small,
   pleasant app task. Ship with the next app build.

### ⭐ OWNER BATCH 2026-07-08 — STILL TODO (owner reported live; some now done above)
B1. **Back-out lands on the WRONG episode.** Click e.g. episode 15 → it plays →
   BACK mid-stream → the episode list highlights ~episode 12, not 15. Focus
   restoration bug in `DetailsScreen` episode list on return from the player.
   Fix: remember the opened episode's stable id/index and `requestFocus` it
   (or scroll+focus) when Details resumes. Overlaps N4 (resume-to-last-episode)
   — do them together: land on the last-watched season chip + episode. Verify
   on a real Cinemeta series (emulator focus is testable here, no remote-repeat
   needed).
B2. **Home/Discover hold-UP scroll is broken again + on Discover too.** From
   deep in the grid, holding UP is "shifty/weird", stops halfway through the
   TOP hero video, "skips the hero's ~2 spaces" in the background, and jumps
   focus to the top bar (Discover/Search/Settings or the filter chips) then
   STOPS scrolling until you press up/down again. This is the DECISIONS #33
   hold-UP stick recurring, now also on Discover. HARD: adb CANNOT simulate
   real key-repeat (proven, #33) — needs the owner's remote for final confirm.
   Approach: (a) Discover likely never got the #33 fix (header/hero as list
   item 0 + focus-rest); apply it. (b) The "hero eats 2 spaces / stops at the
   top bar" smells like the top bar is OUTSIDE the scroll container and grabs
   focus before the list reaches true top — mirror the Home structural fix
   (everything inside one LazyColumn, header = item 0). Look at
   `HomeScreen.kt` (working-ish) vs `DiscoverScreen.kt`.
B3. **"New" catalog to the BOTTOM of the Discover filters** (it's mostly
   in-theater/unstreamable stuff). Check whether the filter/catalog order is
   app-side (`DiscoverScreen` chip order) or addon-driven (AIOMetadata/
   AIOStreams catalog order). If addon-driven, it's a profile-config task
   (owner's endgame), NOT app code — note that back to the owner.
B4. ✅ **DONE (session 21, S0) — "Prefer software video decoder" now DEFAULT ON**
   (alpha.25). Owner said make it default. The 4K-stutter risk is handled by
   leaving the Settings toggle so the pro box can turn it OFF if 4K suffers —
   simpler than per-box or per-codec scoping, and reversible.

**Active backlog = owner feedback ROUND 11 (MASTER_PLAN §10 "Owner
feedback round 11") — owner's stated focus: polish, beauty, efficiency,
stability. Suggested execution order for the next session:**

N1. ⏳ **Video macroblocking — FIX SHIPPED (alpha.23, DECISIONS #42), owner
   verification pending.** Decoder fallback (always) + "Prefer software video
   decoder" Settings toggle (default OFF). Owner must turn the toggle ON on the
   glitchy box and replay the anime (silent corruption needs the software
   path — fallback alone won't do it). If SW still glitches: check the box App
   log for the codec, consider proactively surfacing "Play in another app"
   for known-bad codecs. If SW is clean but slow on 4K: leave default OFF (it
   is), that's the intended trade-off.
N2. **Poster art reload on scroll-back + held-d-pad scroll perf** (one
   efficiency pass: app-wide Coil ImageLoader with bigger memory cache +
   stable keys; profile grid travel on the box).
N3. **Discover grid focus drift** (down 6, up 3 → column shifts right) —
   repro on emulator, pin columns.
N4. **Resume-to-last-episode in Details** (ProgressRepository already has
   the data; pick initial season/episode + a "Continue" CTA).
N5. **Player: hold-to-accelerate scrubbing + prev/next episode buttons.**
N6. ✅ **alpha.23 DEPLOYED** to the two online boxes (.117 pro, .196 "4K Plus")
   — see READ FIRST. Carries the macroblocking fix, English revert, logo v3,
   and round-12 episode nav. ⏳ REMAINING: `install -r` to **.231** (non-pro)
   when it pings again (command in READ FIRST).
N7. **Profiles endgame** (standing): investigate the 3 live AIOStreams
   instances → curate catalogs → 4 templates → owner approval →
   per-person profiles as accounts arrive. Details in MASTER_PLAN §10
   round-11 last item. Don't touch the family's Stremio configs.

(Prior round-10 context below — mostly closed; R1/R5 still owner-gated.)

R1. **Rachael end-to-end (owner's #1).** Blocked owner-side on account
   creation (Trakt/TMDB etc. — owner said he'll make them; test creds in
   gitignored rachael-onboarding.md; a Stremio ACCOUNT is not needed for
   OUR app — only if the owner also wants the Stremio app as fallback).
   Claude-side once keys exist: design the **Family-no-anime** template
   (AIOStreams + AIOMetadata configs based on the owner's instances: block
   porn, no anime catalogs, NO live-TV/events catalogs, auto-updating
   popular/top lists split across both instances within catalog caps,
   coherent Home-row order + Discover types) → owner approves → configure
   her instances with HER keys → add her to users.json (verify the
   dashboard save persists this time!) → `make_profiles.py` + upload →
   type "rachael" on the non-pro box. Then derive the other 3 templates
   (Family-Anime, NSFW-Anime, NSFW-no-anime).
R2. ✅ **Discover UX fixes — ALL CLOSED.** (a) `9fda76a`; (b) closed by the
   shared-PosterCard title reveal (alpha.19/20 — Discover uses the same
   card; owner chose "expand with artwork" over the border sketch);
   (c) `481f4a2` — filter bar visually verified session 17 (TESTLOG).
   Owner-eyeball still welcome on the real TVs.
R3. **Rebrand → Streams** (was SStreams): name ✅; logo/banner ✅ — now
   logo **v3 "Streams"** (session 20, DECISIONS #41, replaces the v2 shadow-S;
   `setup.brand=Streams`). STILL TODO owner-side: upload the staged
   hosting bundle (~/Desktop/setup-upload-trim/ has the newest, trimmed
   one); LATER: the "savoy"-in-filename token migration (regen + re-paste
   each box — breaks saved box links if careless, coordinate with owner).
R4. ✅ **Live-TV/events CLOSED — not an app bug** (session 17, DECISIONS
   #38): MediaFusion serves zero metas for those catalogs and types
   football as movie; AIOStreams wraps the same source. Disappears with
   the addon trim (#37) + R1 templates.
R5. **Myles rename** — WAITING ON OWNER: confirm deleting/merging the
   `Myles Dad` stub so `Myles Manuel` can become `Myles Dad` (keep the
   hosted filename via profiles.config.json so his box's saved link
   survives).
R6. ✅ Ambience + sounds — BOTH DONE session 17 (alpha.21, DECISIONS #38);
   owner to judge colors + sound feel on the real TVs.
R7. Standing Phase-4 queue after round 10 (the NEXT app-side work):
   interface-language switcher, watched-history row, Discover scroll
   prefetch, autoplay settings / tunneling toggle / debug overlay.
   Also owner-decision-gated: skip-intro (AniSkip vs manual +85s button),
   Myles rename (R5), Trakt Scrobble drop.

Owner-side steps still open: **reconnect each box once** (Settings →
Connect this TV — fixes the missing "[BAK]AIOStreams" addon and lets
ProfileSync + daily log upload track the hosted profile; adam's box
already uploads logs), upload the staged trimmed hosting bundle
(~/Desktop/setup-upload-trim/), confirm hold-UP with the real remote
(DECISIONS #33), judge alpha.21's ambience/sounds/launcher art, and read
<setup-url>/logs/<person>.log when anything misbehaves.

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
0b. ✅ **DONE (owner, round 10; live-verified session 15)** — the `api=1`
   index.php is uploaded and `logs/` exists; POST api=1 lookups return
   correct JSON. The name flow is live on hardware.
0c. ✅ Gate D declared PASS by owner (round 10) — phase 3 closed + tagged.
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
   (a) **guard the Addons screen** — effectively DONE since alpha.10
   (Addons lives behind Settings → Expert mode; Home has no Addons button);
   (b) ✅ **error suppression + on-device log** — DONE alpha.16
   (DECISIONS #34);
   (c) **interface-language switcher** (Stremio/Nuvio parity) ← NEXT
   app-side unit;
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
