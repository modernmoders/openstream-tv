package dev.openstream.tv.domain

/**
 * Saved playback position of one item, plus the display fields the Continue
 * Watching row needs without re-fetching meta (poster/title may be stale
 * after an addon change — acceptable; they're cosmetic).
 */
data class WatchProgress(
    val ref: MediaRef,
    /** Meta id + type: where a Continue Watching click navigates (details). */
    val metaId: String,
    val metaType: String,
    val title: String,
    val poster: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /**
     * Where this episode's end credits START, if the anime skip data knows
     * (AniSkip CREDITS window, milliseconds). Null for everything that isn't
     * community-timed anime. Lets "watched" begin at the credits instead of
     * the blanket 90% line — anime EDs plus previews can run past 10% of the
     * episode (owner backlog 2026-07-26, credits-marker restart threshold).
     */
    val creditsStartMs: Long? = null,
) {
    val fractionWatched: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
