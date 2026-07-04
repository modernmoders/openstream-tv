# STATE — updated 2026-07-04T03:30 by session 1

## Phase
Phase 0 — COMPLETE (all §10 Phase 0 boxes ticked; tag `phase-0-done`).
Next phase: Phase 1 — Addon client + catalogs.

## Branch
main (no remote yet — see blockers)

## Just finished
- Full Phase 0: git repo + GPLv3, Android scaffold (builds green), continuity
  docs, CI workflow, hello screen verified on TV emulator (TESTLOG.md 2026-07-04)

## In progress (uncommitted: NO)
- none

## NEXT ACTION (start here)
1. Start Phase 1 with the mock addon fixture server + protocol DTOs (MASTER_PLAN
   §9.1 says mock server first).
2. Create: `app/src/test/java/dev/openstream/tv/addon/` — MockWebServer fixtures
   serving canned manifest/catalog/meta/stream JSON (valid, malformed, delayed,
   empty variants), then `app/src/main/java/dev/openstream/tv/addon/` DTOs with
   lenient kotlinx.serialization parsing, tested against the fixtures.
3. Authoritative spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md
   — implement against it, not memory.
4. Acceptance: DTO parse tests green incl. malformed-JSON cases; `AddonClient`
   interface defined per MASTER_PLAN §3.2/§4.

## Blockers / open questions (none block Phase 1 coding)
- **No GitHub remote.** OWNER ACTION: create public repo (suggested `openstream-tv`),
  then `git remote add origin <url> && git push -u origin main --tags`.
  Or `brew install gh && gh auth login` so sessions can push. Until then, §2.2
  rule 6 (push after every commit) is impossible — commits stay local.
- Gemini reference doc missing from docs/reference/ (owner to supply).
- Name "OpenStream TV" + GPLv3 are session-picked per plan; owner may veto
  (DECISIONS.md #2/#3) — cheap to change until the remote exists.

## Environment notes
- JAVA_HOME=/opt/homebrew/opt/openjdk@17 required for all gradlew calls
- SDK at /opt/homebrew/share/android-commandlinetools (in gitignored
  local.properties — recreate with `echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties`)
- TV emulator: AVD `openstream_tv_api34`; boot headless with
  `$SDK/emulator/emulator -avd openstream_tv_api34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`
- adb lives at $SDK/platform-tools/adb (not on default PATH)
