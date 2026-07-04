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
- SECURITY: Real addon manifest URLs are secrets — they embed the owner's
  personal config tokens. NEVER write them into any tracked file (code,
  fixtures, docs, TESTLOG, commit messages). In committed artifacts use only
  Cinemeta (https://v3-cinemeta.strem.io/manifest.json) or the MockAddonServer.
  Refer to private instances as "owner's AIOStreams/AIOMetadata instance".
- KISS/YAGNI/SOLID. Code must be readable by a new developer; comment the why.
- Never break the Live-TV compatibility constraints in MASTER_PLAN.md §8.
- Record non-obvious choices in docs/DECISIONS.md.
- Build check before checkpoint: ./gradlew assembleDebug && ./gradlew testDebugUnitTest

Environment (this machine):
- JAVA_HOME must be /opt/homebrew/opt/openjdk@17 (run: JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew ...)
- Android SDK: /opt/homebrew/share/android-commandlinetools (already in local.properties)
- TV emulator AVD name: openstream_tv_api34 (Android TV 14, 1080p, arm64)
- Emulator binary: /opt/homebrew/share/android-commandlinetools/emulator/emulator
