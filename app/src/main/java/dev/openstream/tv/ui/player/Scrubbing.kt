package dev.openstream.tv.ui.player

/**
 * D-pad scrub feel (owner 2026-07-10: "premium, responsive but fluid").
 *
 * Presses move a PREVIEW target instantly; the real seek commits only after
 * [COMMIT_DELAY_MS] of quiet — one rebuffer per gesture instead of one per
 * press (each seek rebuffers, which is what made held scrubbing feel like a
 * slideshow). Steps accelerate with the press streak so a held key crosses a
 * movie in seconds while single taps stay precise.
 *
 * Pure — table-tested without a device.
 */
object Scrubbing {

    /** Quiet time after the last press before the seek actually fires. */
    const val COMMIT_DELAY_MS = 350L

    /** Presses further apart than this reset the streak (and the step). */
    const val STREAK_WINDOW_MS = 600L

    /**
     * Seek step for the [streak]-th consecutive press (1-based). A remote's
     * key-repeat fires ~10-20/s once held. Round-14 (owner 2026-07-11): the
     * original ramp (5/12/20) hit the big steps too fast — "acceleration cut
     * by half", so every threshold is doubled; a hold now takes ~2s to reach
     * 60s steps while the first taps stay a fine 10s.
     */
    fun stepMs(streak: Int): Long = when {
        streak < 10 -> 10_000L
        streak < 24 -> 30_000L
        streak < 40 -> 60_000L
        else -> 120_000L
    }

    /** New preview target: current target (or live position) plus one step,
     *  clamped to the video. [direction] is -1 (rewind) or +1 (forward). */
    fun nextTarget(
        currentTargetMs: Long?,
        positionMs: Long,
        durationMs: Long,
        direction: Int,
        streak: Int,
    ): Long {
        val base = currentTargetMs ?: positionMs
        return (base + direction * stepMs(streak)).coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
}
