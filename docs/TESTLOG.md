# TESTLOG.md — what was tested, where, and the result

Append-only. Newest entries at the top.

---

## 2026-07-04 — Phase 1: addon manager UI, end-to-end on emulator (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 38 tests (new: AddAddonViewModel state machine ×5) | macOS, JDK 17 | PASS (38/38) |
| D-pad-only walk: Home → Addons → Add addon → type real Cinemeta URL via on-screen keyboard → Fetch → preview (name/version/desc/types/resources/catalogs) → Install → list shows addon with Enabled/▲/▼/Remove | AVD `openstream_tv_api34`, live internet | PASS (screenshots) |
| **Focus bug found & fixed:** focused `BasicTextField` consumed DPAD_DOWN → focus trapped in URL field, Fetch unreachable. Fix: `onPreviewKeyEvent` routes UP/DOWN to `focusManager.moveFocus` + ImeAction.Go submits. Re-tested: PASS. Rule recorded as DECISIONS.md #7 | same | FIXED + PASS |
| Persistence: `am force-stop` → relaunch → addon list still shows Cinemeta (Room survives process death) | same | PASS |
| GitHub Actions `ci` on push (assembleDebug + unit tests) | ubuntu-latest | PASS (run 28702277593) |

## 2026-07-04 — Phase 1: AddonRepository + Room persistence (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 33 tests total; new AddonRepositoryTest: install/persist, same-manifest-id coexistence (§4.2), reinstall keeps order+enabled, failed install stores nothing, invalid URL fast-fail, uninstall+reorder | macOS, JDK 17, local JVM | PASS (33/33) |
| `./gradlew assembleDebug` (Room schema exported to app/schemas/) | same | PASS |

## 2026-07-04 — Phase 1: addon protocol DTOs + AddonClient (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew testDebugUnitTest` — 27 tests: manifest parsing (mixed resources, legacy extra, declares()), meta/stream parsing (channel/tv preserved, bingeGroup/proxyHeaders, infoHash kept-not-playable), URL normalization (stremio://), AddonClient vs MockAddonServer (paths, malformed JSON, 404, dead server, 1s-delayed body, colon ids) | macOS, JDK 17, local JVM | PASS (27/27) |
| `./gradlew assembleDebug` (Hilt NetworkModule/AddonModule wired) | same | PASS |

## 2026-07-04 — Phase 0 scaffold (session 1)

| Check | Environment | Result |
|---|---|---|
| `./gradlew assembleDebug` | macOS, JDK 17.0.19, Gradle 9.6.1, AGP 9.2.1 | PASS (APK ~18.6 MB) |
| `./gradlew testDebugUnitTest` (SanityTest: lenient JSON) | same | PASS |
| Hello screen boots + button focusable on TV emulator | AVD `openstream_tv_api34` (Android TV 14, 1080p, arm64), headless | PASS — MainActivity resumed, button rendered focused, 2× KEYCODE_DPAD_CENTER updated label to "OK pressed ×2" (screenshot-verified) |
