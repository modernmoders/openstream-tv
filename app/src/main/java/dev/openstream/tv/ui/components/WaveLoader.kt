package dev.openstream.tv.ui.components

/**
 * Pure keyframe math for the wave-dots loader — the owner's "SStreams Loader"
 * design (Claude Design export, 2026-07-11): five dots, each cycling
 *
 *   rest (0–?) → rise+brighten (peak at 30%) → fall+dim (done at 60%) → rest
 *
 * over [WAVE_PERIOD_MS], staggered [WAVE_STAGGER_MS] per dot. Kept free of
 * Compose/Android imports so the timing can be unit-tested on the JVM.
 */

const val WAVE_DOT_COUNT = 5
const val WAVE_PERIOD_MS = 2200f
const val WAVE_STAGGER_MS = 160f

/** A resting dot's opacity; it climbs to 1.0 at the crest of the wave. */
const val WAVE_ALPHA_REST = 0.45f

data class WaveDotPose(
    /** 0 = resting baseline, 1 = full crest. */
    val rise: Float,
    val alpha: Float,
)

/** Where dot [dot] is in its own cycle at [timeMs], as a phase in [0, 1). */
fun wavePhase(timeMs: Float, dot: Int): Float {
    val raw = (timeMs - dot * WAVE_STAGGER_MS) / WAVE_PERIOD_MS
    return ((raw % 1f) + 1f) % 1f // true modulo: pre-start dots rest at the tail
}

/** The design's ease-in-out; smoothstep(1-x) == 1-smoothstep(x), so the fall mirrors the rise. */
private fun ease(u: Float): Float = u * u * (3f - 2f * u)

fun waveDotPose(phase: Float): WaveDotPose {
    val rise = when {
        phase < 0.3f -> ease(phase / 0.3f)
        phase < 0.6f -> 1f - ease((phase - 0.3f) / 0.3f)
        else -> 0f
    }
    return WaveDotPose(rise = rise, alpha = WAVE_ALPHA_REST + (1f - WAVE_ALPHA_REST) * rise)
}
