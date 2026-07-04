# STATE — updated 2026-07-04T06:30 by session 1

## Phase
Phase 1 — Addon client + catalogs (in progress)

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv) — CI green

## Just finished
- Addon manager UI, verified end-to-end on the TV emulator with a REAL addon:
  Home → Addons → Add addon → typed Cinemeta manifest URL → preview → Install
  → listed with Enabled/▲/▼/Remove; survives force-stop (Room). 38/38 tests.
  - ui/: AppNavHost (navigation-compose, DECISIONS #6), theme/, home/
    (placeholder), addons/ (AddonManagerScreen+VM, AddAddonScreen+VM)
  - Fixed a real TV focus trap in text fields — rule in DECISIONS #7
- Before that: protocol DTOs + AddonClient (18984be), Room persistence (9146fd3)

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
Phase 1 remaining (MASTER_PLAN §10):
1. **CatalogRepository + Home screen rows.** Fan out over enabled addons'
   manifest catalogs (skip isSearchOnly ones), fetch each via
   AddonClient.catalog(), render as horizontal poster rows (LazyColumn of
   LazyRows, stable keys, 2:3 posters via Coil AsyncImage). Continue Watching
   stub row first (§5.6). Per-catalog failure chip on error (§4.1.8).
2. Discover grid (6-col default at 1080p, LazyVerticalGrid) + density setting
   later (§5.1); search screen using catalog `search` extra (reuse UrlField
   focus pattern from DECISIONS #7).
3. Gate: browse a real AIOMetadata instance smoothly on the TV emulator
   (owner has instances — ask for a manifest URL, or use Cinemeta which is
   already installed on the emulator from this session's test).

## Blockers / open questions
- none blocking. Owner approved name/license; remote is live.

## Environment notes
- JAVA_HOME=/opt/homebrew/opt/openjdk@17 required for all gradlew calls
- SDK at /opt/homebrew/share/android-commandlinetools (gitignored local.properties)
- TV emulator AVD: openstream_tv_api34; headless boot:
  `$SDK/emulator/emulator -avd openstream_tv_api34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
- adb at $SDK/platform-tools/adb
