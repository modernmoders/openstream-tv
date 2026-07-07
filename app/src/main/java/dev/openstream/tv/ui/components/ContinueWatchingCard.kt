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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.theme.CardSizeTokens

/**
 * Poster card + progress bar for the Continue Watching row (§5.6). Same
 * dimensions and reveal-on-focus title treatment as [PosterCard] (owner
 * round 10: "expand with artwork") so the two row kinds align visually —
 * see that file's KDoc for why the title lives inside the card. The
 * watched-fraction bar stays visible regardless of focus; it's useful
 * information on its own, not decoration.
 *
 * [modifier] lands on the Card — the focusable — so callers can attach a
 * FocusRequester for the row-entry rule (§10: DOWN lands on the first card).
 */
@Composable
fun ContinueWatchingCard(progress: WatchProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val width = CardSizeTokens.posterWidth()
    val height = CardSizeTokens.posterHeight()
    var focused by remember { mutableStateOf(false) }
    val revealAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "cw-title-reveal",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .onFocusChanged { focused = it.isFocused },
    ) {
        Box(modifier = Modifier.width(width).height(height)) {
            AsyncImage(
                model = progress.poster,
                contentDescription = progress.title,
                contentScale = ContentScale.Crop,
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
                    text = progress.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            // Watched-fraction bar over a full-width track — always visible,
            // drawn last so its thin strip sits above the title scrim.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x80000000)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.fractionWatched)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
