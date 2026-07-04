# STATE — updated 2026-07-04T03:25 by session 1

## Phase
Phase 0 — Repo + continuity scaffolding

## Branch
main (no remote yet — see blockers)

## Just finished
- Gradle/Android scaffold builds green: assembleDebug + testDebugUnitTest PASS
- Continuity docs created (CLAUDE.md, DECISIONS.md #1–#5, TESTLOG.md)

## In progress (uncommitted: YES)
- Phase 0 remainder: CI workflow, emulator boot verification, first commit

## NEXT ACTION (start here)
1. Verify hello screen on TV emulator: boot AVD `openstream_tv_api34`, install
   app/build/outputs/apk/debug/app-debug.apk, launch, send DPAD_CENTER, confirm
   button reacts. Log result in docs/TESTLOG.md.
2. Commit everything (conventional commits), tag `phase-0-done` once §10 Phase 0
   checkboxes are all satisfied.

## Blockers / open questions
- **No GitHub remote.** `gh` CLI is not installed and this machine has no stored
  GitHub credentials. OWNER ACTION: create the public repo (suggested name
  `openstream-tv`), then `git remote add origin <url> && git push -u origin main`.
  Alternatively install gh (`brew install gh && gh auth login`) so sessions can push.
- Gemini reference doc missing from docs/reference/ (owner to supply).
- License GPLv3 + name "OpenStream TV" are session-picked per plan; owner may veto
  (DECISIONS.md #2, #3).

## Environment notes
- JAVA_HOME=/opt/homebrew/opt/openjdk@17 required for all gradlew calls
- SDK at /opt/homebrew/share/android-commandlinetools (in local.properties;
  local.properties is gitignored — recreate if missing)
- Emulator AVD: openstream_tv_api34 (Android TV 14, 1080p, arm64)
