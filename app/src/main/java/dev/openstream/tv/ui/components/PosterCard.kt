package dev.openstream.tv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.CardSizeTokens
import kotlinx.coroutines.delay

/** How long focus must REST on a card before its title reveals (ms). */
private const val TITLE_REVEAL_SETTLE_MS = 120L

/** What a browse tile shows about the title's watch history (watched system). */
sealed interface PosterIndicator {
    /** Latest watched episode/movie finished → check disc + dimmed artwork. */
    data object Watched : PosterIndicator

    /** Partway through → progress ring; the focus reveal adds "N min left". */
    data class InProgress(val fraction: Float, val minutesLeft: Int) : PosterIndicator
}

/**
 * Maps a title's latest progress row to a tile indicator. Uses the Continue
 * Watching floor (60s), not the resume floor (15s), so an accidental click
 * doesn't stamp a ring on a poster; past 95% shows the check instead. Pure
 * for unit tests — the thresholds already carry owner-tuned meaning.
 */
internal fun posterIndicatorFor(progress: WatchProgress?): PosterIndicator? = when {
    progress == null -> null
    ProgressRepository.isWatched(progress) -> PosterIndicator.Watched
    ProgressRepository.isResumable(progress, ProgressRepository.MIN_CONTINUE_WATCHING_MS) ->
        PosterIndicator.InProgress(progress.fractionWatched, minutesLeftOf(progress))
    else -> null
}

/**
 * The one poster card used by every browse surface (home rows, discover
 * grid, search results). 2:3 ratio is contract (§5.2); size derives from
 * CardSizeTokens so the future density setting changes a single number.
 *
 * The title lives INSIDE the card, over the artwork, and reveals only after
 * focus RESTS on the card (owner round 10 "expand with artwork" hover
 * reveal). Two things keep this smooth when the d-pad is HELD down and focus
 * flies across a row (owner report: holding up/down glitched the titles):
 *   1. a ~120ms settle delay — a fast hold never lingers long enough to fire
 *      a reveal on each card it passes, so no per-card title flicker; and
 *   2. the fade is applied in the DRAW phase via [graphicsLayer] (house rule
 *      DECISIONS #22), so the animation never recomposes/relayouts the card —
 *      it just changes an alpha the GPU already has, cheap on the 32-bit boxes.
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
    progress: WatchProgress? = null,
) {
    val width = CardSizeTokens.posterWidth(columns)
    val height = CardSizeTokens.posterHeight(columns)
    var focused by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    // Reveal only after focus settles; leaving hides immediately. Keyed on
    // `focused`, so a focus change mid-delay cancels the pending reveal —
    // exactly what makes a fast d-pad hold flicker-free.
    LaunchedEffect(focused) {
        revealed = if (focused) {
            delay(TITLE_REVEAL_SETTLE_MS)
            true
        } else {
            false
        }
    }
    val revealAlpha = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "poster-title-reveal",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(modifier = Modifier.width(width).height(height)) {
            val indicator = posterIndicatorFor(progress)
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
            // Watched artwork recedes behind a dim so unwatched content pops
            // forward (watched-system design principle). Static, no animation.
            if (indicator is PosterIndicator.Watched) {
                Box(Modifier.fillMaxSize().background(WatchedArtworkDim))
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    // Draw-phase alpha (DECISIONS #22): reading revealAlpha.value
                    // inside the lambda defers it to the draw phase, so the fade
                    // never triggers recomposition of this card while scrolling.
                    .graphicsLayer { alpha = revealAlpha.value }
                    .background(TitleScrim)
                    // Headroom above the title so the scrim fades in over the
                    // artwork instead of starting with a hard edge.
                    .padding(top = height * 0.18f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                // The focus reveal completes the in-progress story: a thin
                // progress bar + "N min left" under the title (handoff 1a).
                if (indicator is PosterIndicator.InProgress) {
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x38FFFFFF)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(indicator.fraction)
                                .height(4.dp)
                                .background(Accent),
                        )
                    }
                    Text(
                        text = "${indicator.minutesLeft} min left",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB9CDE4),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                Box(Modifier.height(4.dp))
            }
            // Corner indicator (watched system): in-progress = progress ring
            // with the percent inside; watched = solid accent check disc.
            // Unwatched artwork stays pristine — no empty badge.
            when (indicator) {
                is PosterIndicator.InProgress -> ProgressRing(
                    fraction = indicator.fraction,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
                is PosterIndicator.Watched -> WatchedDisc(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
                null -> Unit
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
