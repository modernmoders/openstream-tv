package dev.openstream.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.ui.theme.CardSizeTokens

/**
 * The one poster card used by every browse surface (home rows, discover
 * grid, search results). 2:3 ratio is contract (§5.2); size derives from
 * CardSizeTokens so the future density setting changes a single number.
 *
 * The title lives INSIDE the card, over the artwork, and only fades in on
 * focus (owner round 10: "expand with artwork" hover reveal) — a plain
 * sibling Text below the card used to hold it, but TV Material's default
 * 1.1× focus scale grows the Card downward into that space with nothing
 * reserving the extra room, so the artwork covered the title on every
 * hover. Living inside the Card, the title scales and fades WITH the
 * artwork instead of getting run over by it. [revealAlpha] is a plain
 * alpha fade (draw-phase only, house rule DECISIONS #22) — cheap on the
 * 32-bit boxes, no relayout, no jank.
 *
 * [modifier] lands on the Card — the focusable — so callers can attach
 * FocusRequesters for row-entry rules (§10 Phase 4 search focus rule).
 */
@Composable
fun PosterCard(
    item: MetaItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    columns: Int = CardSizeTokens.DEFAULT_COLUMNS,
) {
    val width = CardSizeTokens.posterWidth(columns)
    val height = CardSizeTokens.posterHeight(columns)
    var focused by remember { mutableStateOf(false) }
    val revealAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "poster-title-reveal",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(modifier = Modifier.width(width).height(height)) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                // Solid placeholder: the grid paints its full layout on the
                // first frame instead of popping in card by card as posters
                // arrive — the pop-in itself read as jank on the onn boxes.
                placeholder = ColorPainter(PosterPlaceholder),
                error = ColorPainter(PosterPlaceholder),
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(height * 0.42f)
                    .alpha(revealAlpha)
                    .background(TitleScrim),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Matches SkeletonPosterCard's fill so load states blend into each other. */
private val PosterPlaceholder = Color(0xFF23232F)

/** Bottom-up scrim behind a reveal-on-focus title; shared with [ContinueWatchingCard]. */
val TitleScrim = Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xE6000000))

/** Inline status text used under row/grid headers (loading, failure chip). */
@Composable
fun RowMessage(text: String, horizontalPadding: androidx.compose.ui.unit.Dp = 48.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = dev.openstream.tv.ui.theme.MutedText,
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
    )
}
