package dev.openstream.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openstream.tv.ui.theme.CardSizeTokens

/**
 * Loading placeholders in poster geometry (owner round 14 #9: while rows
 * loaded, Home was "half skeleton, half unallocated blank" — a text line
 * here, nothing there). Every loading surface now paints the SAME full-size
 * tile silhouettes the real content will occupy, so the layout is complete
 * on the first frame and content swaps in without reflow — the Netflix
 * pattern. Deliberately static (no shimmer): animation costs frames on the
 * 32-bit boxes exactly when catalogs are streaming in (DECISIONS #22).
 */

/** Matches PosterCard/SurfaceCard placeholders so loads blend into content. */
val SkeletonFill = Color(0xFF23232F)

/** One row of placeholder poster tiles, sized exactly like a real row. */
@Composable
fun SkeletonPosterRow(
    columns: Int = CardSizeTokens.DEFAULT_COLUMNS,
    horizontalPadding: Dp = 48.dp,
    // Real rows reserve ±focusHeadroom for the focus scale; the skeleton
    // reserves the same so the swap to content never reflows the column.
    verticalPadding: Dp = CardSizeTokens.focusHeadroom,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap),
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        repeat(columns) {
            Box(
                Modifier
                    .size(CardSizeTokens.posterWidth(columns), CardSizeTokens.posterHeight(columns))
                    .background(SkeletonFill, RoundedCornerShape(10.dp)),
            )
        }
    }
}

/** A few rows of placeholder tiles — the initial state of a poster grid. */
@Composable
fun SkeletonPosterGrid(
    columns: Int = CardSizeTokens.DEFAULT_COLUMNS,
    rows: Int = 3,
    horizontalPadding: Dp = 0.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CardSizeTokens.rowGap)) {
        repeat(rows) {
            SkeletonPosterRow(
                columns = columns,
                horizontalPadding = horizontalPadding,
                verticalPadding = 0.dp,
            )
        }
    }
}
