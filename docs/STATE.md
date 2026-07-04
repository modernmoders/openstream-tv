# STATE — updated 2026-07-04T07:45 by session 1

## Phase
Phase 1 — Addon client + catalogs. **GATE PASSED** (real AIOMetadata browses
on the emulator). Remaining: Discover grid + search, then tag phase-1-done.

## Branch
main @ origin (https://github.com/modernmoders/openstream-tv) — CI green

## Just finished
- CatalogRepository (browsable-feed refs in addon order; isBrowsableFeed skips
  extras-required catalogs) + HomeViewModel parallel fan-out with incremental
  rows + failure chips (§4.1.5/§4.1.8) + HomeScreen poster rows
  (CardSizeTokens 6-col, Coil AsyncImage). 46/46 tests, clean build.
- GATE: owner's private AIOMetadata instance (URL is a SECRET — never commit;
  it lives only in the emulator's Room DB) installed via UI; its rows render
  alongside Cinemeta with smooth D-pad scrolling. TESTLOG has details.
- Wild-JSON fix: FlexibleStringListSerializer (AIOMetadata sends "director"
  as string). Security fix: failure chips no longer render addon URLs.

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
1. **Discover grid**: LazyVerticalGrid, CardSizeTokens.DEFAULT_COLUMNS (6),
   route "discover"; pick a catalog (or all-catalogs browse), paginate with
   catalog `skip` extra on scroll end. §5.1.
2. **Search screen**: reuse the UrlField D-pad pattern (DECISIONS #7 — this
   was written for exactly this screen); query all search-capable catalogs
   (`supportsExtra("search")`) in parallel; render result rows like Home.
3. Then: focus audit vs §5, tag `phase-1-done`, update MASTER_PLAN checkboxes.
4. Emulator state note: AVD `openstream_tv_api34` already has Cinemeta + the
   owner's AIOMetadata installed in the app DB from the gate test.

## Warnings for future sessions
- NEVER run two gradle invocations concurrently (background + foreground) —
  it corrupted intermediates once (" 2.class" files, stale APKs). Serial only;
  full clean fixes it if it recurs.
- Real addon URLs are secrets — CLAUDE.md security rule.

## Blockers / open questions
- none blocking.

## Environment notes
- JAVA_HOME=/opt/homebrew/opt/openjdk@17 required for all gradlew calls
- SDK at /opt/homebrew/share/android-commandlinetools (gitignored local.properties)
- TV emulator AVD: openstream_tv_api34; headless boot:
  `$SDK/emulator/emulator -avd openstream_tv_api34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
- adb at $SDK/platform-tools/adb
