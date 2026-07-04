# STATE — updated 2026-07-04T05:40 by session 1

## Phase
Phase 1 — Addon client + catalogs (in progress)

## Branch
main (remote pending — see blockers)

## Just finished
- Addon protocol layer (27 tests) — DTOs, AddonClient/OkHttpAddonClient,
  MockAddonServer fixtures, NetworkModule. See commit 18984be.
- Addon persistence (33 tests total now): `data/db/` InstalledAddonEntity/Dao/
  OpenStreamDatabase (Room, schema exported to app/schemas/), `addon/
  AddonRepository` (install/uninstall/reorder/setEnabled; keyed by manifest URL
  so same-id instances coexist §4.2; reinstall keeps order+enabled),
  `di/DataModule`. Repository tested against fake DAO + MockAddonServer.

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
Phase 1 remaining, in order (MASTER_PLAN §10):
1. **Addon manager UI** (§4.1.1): screen listing installed addons
   (observeInstalled), URL entry field (on-screen keyboard) → install() →
   confirmation dialog showing manifest name/description/types/resources/
   catalogs before persisting; uninstall + reorder + enable toggle.
   Needs a ViewModel (Hilt) + navigation from MainActivity (add
   androidx.navigation:navigation-compose or simple state-based nav — record
   choice in DECISIONS.md). D-pad focus per §5.4.
2. Home screen catalog rows + Discover grid (6-col default) + search
   (CatalogRepository fan-out over enabled addons' catalogs).
3. Gate: browse a real AIOMetadata instance smoothly on the TV emulator.

## Blockers / open questions
- **No GitHub remote.** gh CLI now installed + authed (account: modernmoders),
  but repo creation was policy-blocked for the agent: OWNER must create the
  public repo (web UI or `gh repo create openstream-tv --public`), then any
  session can wire + push: `git remote add origin <url> && git push -u origin main --tags`.
- Owner approved name/license going forward (session 1, 2026-07-04).

## Environment notes
- JAVA_HOME=/opt/homebrew/opt/openjdk@17 required for all gradlew calls
- SDK at /opt/homebrew/share/android-commandlinetools (gitignored local.properties)
- TV emulator AVD: openstream_tv_api34; headless boot:
  `$SDK/emulator/emulator -avd openstream_tv_api34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
- adb at $SDK/platform-tools/adb
