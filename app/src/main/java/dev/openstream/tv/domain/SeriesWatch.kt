package dev.openstream.tv.domain

/**
 * Series-level completion for a browse tile: [watched] of [total] episodes
 * (owner Round 17: "how much the user has watched out of ALL of the series —
 * not just the episode"). Distinct from [WatchProgress], which is one
 * episode's position.
 */
data class SeriesWatch(val watched: Int, val total: Int) {
    val fraction: Float
        get() = if (total > 0) (watched.toFloat() / total).coerceIn(0f, 1f) else 0f

    val complete: Boolean get() = total > 0 && watched >= total
}
