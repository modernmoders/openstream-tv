# Testing OpenStream TV on your onn box (owner guide)

This is the Phase 3 gate (§7.2): the last step before Phase 3 is done.
Everything here is point-and-click on the box; no computer needed for
Option A. Budget ~20 minutes.

## 1. Install the app

### Option A — no computer (recommended)

1. On the onn box, install **Downloader by AFTVnews** from the Play Store.
2. Allow sideloading: Settings → Apps → Security & restrictions →
   Unknown sources (or "Install unknown apps") → turn ON for Downloader.
3. Open Downloader and enter this URL:

   `github.com/modernmoders/openstream-tv/releases`

   Tap the newest release, then the `openstream-tv-debug.apk` file, and
   confirm the install prompts.
4. Also install **VLC for Android** from the Play Store, and **open VLC
   once** so it finishes its first-run setup (if you skip this, the first
   video handed to VLC dies silently — known VLC behavior).

### Option B — from the Mac (lets Claude drive remotely)

1. On the box: Settings → System → About → click **Android TV OS build**
   7 times ("You are now a developer!").
2. Settings → System → Developer options → turn on **USB debugging**
   (and **Network debugging / Wireless debugging** if the option exists).
3. Find the box's IP: Settings → Network & Internet → your Wi-Fi →
   IP address.
4. Tell Claude the IP in a session. (`adb connect <ip>:5555` — if the box
   shows an "Allow USB debugging?" prompt, tick "Always allow".)
   From there Claude can install builds and collect logs directly.

## 2. Run the gate checks

Set up once: in OpenStream TV, Addons → Add addon → your AIOStreams and
AIOMetadata URLs (same ones as always — never share them).

Then, using any series with 3+ episodes you can legally stream:

**A. Autoplay chain (internal player) — the big one**
1. Play episode 1, seek near the end, let it finish.
2. Expect: "Up next: S…E…" countdown card → episode 2 starts by itself →
   same again → episode 3. **Zero remote presses between episodes.**
3. During one countdown, press Back: card disappears, you stay on the
   finished screen. That's the cancel path.

**B. External player round-trip (VLC)**
1. On any episode's stream list, **long-press OK** on a stream →
   "Play with…" → VLC.
2. Let it play a few minutes, then exit VLC (Back).
3. Reopen the same stream in the app: it should offer
   **"Resume from …"** at roughly where you left VLC.
4. Now watch an episode in VLC **to the very end** (seeking ahead is fine).
   When VLC closes, the app should show the **Up Next countdown** and
   continue the series in VLC.

**C. Feel check**
- Cold start time, scrolling smoothness on the home grid, and whether
  playback ever stalls without a visible message. Anything ugly or
  confusing: note it for the Phase 4 polish list.

## 3. Report back

Tell Claude in a session (or jot down): which of A/B/C passed, what the
exact on-screen text was when something failed, and roughly when it
happened (so logs can be matched if Option B is set up). Claude writes the
TESTLOG entry, ticks the §7.2 gate, and tags `phase-3-done`.
