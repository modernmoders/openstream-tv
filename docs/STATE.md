# STATE — updated 2026-07-04T05:10 by session 1

## Phase
Phase 1 — Addon client + catalogs (in progress)

## Branch
main (remote pending — see blockers)

## Just finished
- Addon protocol layer complete and tested (27/27 unit tests green):
  - `addon/` DTOs with lenient parsing: Manifest (string-or-object resources,
    legacy extraSupported/extraRequired), MetaItem/Video (title-or-name),
    Stream (bingeGroup, proxyHeaders, infoHash kept but flagged not-v1-playable),
    Subtitle, response envelopes. `domain/ContentType` keeps CHANNEL/TV (§8).
  - `AddonClient` interface + `OkHttpAddonClient` (Result-based errors with
    Reason enum for failure chips; extraProps path segments; stremio:// rewrite)
  - `di/NetworkModule` (15s call timeout = §4.1.5 per-addon budget)
  - Mock addon fixture server (§9.1) + fixtures: valid/malformed/delayed/empty
- Gemini reference doc committed to docs/reference/

## In progress (uncommitted: NO — checkpoint commit follows this file)
- none

## NEXT ACTION (start here)
Phase 1 remaining, in order (MASTER_PLAN §10):
1. **AddonRepository + Room persistence for installed addons.** Key addons by
   manifest URL (not manifest id — multiple AIOStreams instances must coexist,
   §4.2). Room entity: manifest URL (PK), raw manifest JSON, install order,
   enabled flag. Install/uninstall/reorder operations. Unit tests with the
   existing MockAddonServer.
2. Manifest install UI (URL entry w/ on-screen keyboard, show name/description/
   types/resources/catalogs before confirming — §4.1.1).
3. Home screen catalog rows + Discover grid (6-col default) + search.
4. Gate: browse a real AIOMetadata instance smoothly on the TV emulator.

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
