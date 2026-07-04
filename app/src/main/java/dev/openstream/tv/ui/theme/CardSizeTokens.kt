package dev.openstream.tv.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one place card geometry is defined (MASTER_PLAN §5.1): all poster/row
 * sizes derive from the column count so the future density setting changes a
 * single number.
 *
 * Aspect ratios are contract, not decoration (§5.2): poster 2:3,
 * backdrop 16:9, square 1:1.
 */
object CardSizeTokens {
    /** Default Discover/home density: 6 poster columns at 1080p (§5.1). */
    const val DEFAULT_COLUMNS = 6

    /** Usable width at 1080p TV density (960dp) minus 2×48dp overscan. */
    private val usableWidth = 864.dp
    private val gap = 12.dp

    fun posterWidth(columns: Int = DEFAULT_COLUMNS): Dp =
        (usableWidth - gap * (columns - 1)) / columns

    /** 2:3 poster. */
    fun posterHeight(columns: Int = DEFAULT_COLUMNS): Dp = posterWidth(columns) * 3 / 2

    val rowGap: Dp = gap
}
