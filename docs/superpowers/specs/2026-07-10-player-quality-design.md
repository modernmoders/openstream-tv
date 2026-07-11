# Player quality: automatic clean decoding + fluid controls (2026-07-10)

Owner ask (verbatim intent): "the main player is kinda trash — rainbow artifacts
on some streams; I want a premium, responsive but fluid feel. I'd rather use
this over Stremio."

The "rainbow artifacts" are the known macroblocking: the onn boxes' vendor
hardware decoders silently emit garbage frames on some encodes (HEVC 10-bit /
odd profiles), which MX Player avoids by software-decoding. Today the cure is a
manual "Software video ON/OFF" toggle that reloads via the stream list —
effective but reactive: the viewer sees garbage first, then must know the trick.

## Approaches considered

1. **Auto-decide the decoder per stream (chosen).** We already parse the codec
   from the release label (`StreamCascade.videoCodecOf`, alpha.33) and know the
   box's hardware codecs (`DecoderCapabilities`). Decide software-vs-hardware
   at `play()` time; no engine rebuild — a `MediaCodecSelector` is consulted at
   every codec init, so a delegating selector switches per playback.
2. Format-sniffing renderer (override `MediaCodecVideoRenderer.getDecoderInfos`
   to reject decoders that don't claim the real profile). Most correct, but a
   deep Media3 override on fragile vendor decoders — high risk, hard to test.
3. Keep the manual toggle only, rank harder against bad codecs. Already live
   (alpha.33); doesn't stop the garbage when only bad-codec streams exist.

Chosen: (1) plus a **decode-error safety net** — a decode-class playback error
on a hardware session retries the same stream at the same position in software
once, before "try another stream" logic runs. Together: label heuristic catches
the known-bad codecs up front; the retry catches liars.

## Design

- `domain/VideoCodec.kt` — the enum moves out of `StreamCascade` into domain
  (with `hardwareDecodable(hw)`), so `PlayableSource` can carry
  `videoCodec: VideoCodec?` without domain importing autoplay. Live-TV-safe:
  null = unknown = hardware (we only demote positively-known-bad codecs).
- `StreamMapping.toPlayableSource` stamps `videoCodec = StreamCascade.videoCodecOf(stream)`.
- `ExoPlayerEngine`:
  - delegating `MediaCodecSelector` (`PREFER_SOFTWARE` when active, else
    `DEFAULT`), decided per `play()` from: global pref OR session override OR
    `!videoCodec.hardwareDecodable(hardwareCodecs)`.
  - `usingSoftwareDecoder: StateFlow<Boolean>` — the UI toggle shows the truth.
  - `stop()` before re-`play()` so codec reuse can't keep a garbage decoder.
  - `SeekParameters.CLOSEST_SYNC` for snappy scrubbing.
  - `PlayerEvent.Error.isDecodeError` classified from the error code.
- `PlaybackService` injects `DecoderCapabilities` into the engine.
- `PlayerViewModel`: decode-error → one software retry per stream URL (same
  position); "Software video" toggle now applies **in place** (persist pref,
  set session override, replay at current position) — no stream-list bounce.

## Premium/fluid UX (PlayerScreen)

- **Scrubbing**: LEFT/RIGHT moves a *preview* target instantly (accelerating
  steps: 10s → 30s → 60s → 120s with press streak, pure `Scrubbing.stepMs`);
  the real seek commits ~350 ms after the last press — one rebuffer per
  gesture instead of one per press. A "+2:30" delta chip shows while scrubbing.
- **Control bar animates** in/out (fade+slide; degrades to instant when the
  box zeroes animator scale — same as today's pop).
- **Paused ⇒ bar stays** (auto-hide only while playing).
- **Mid-playback rebuffer**: small non-blocking spinner (no scrim, no focus
  steal) shown only if buffering persists >400 ms after first READY.

## Testing

Pure units: `VideoCodec.hardwareDecodable`, `Scrubbing.stepMs`/accumulation,
decode-error code classification, StreamMapping codec stamping, engine decision
function (extracted pure). Gates: `assembleDebug` + `testDebugUnitTest`.
Box-only: whether auto-software really kills the artifacts (codec truth is
per-box) — owner verifies on deploy, same as alpha.33.
