package dev.openstream.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.openstream.tv.ui.theme.MutedText

/**
 * Poster card + progress bar for the Continue Watching row (§5.6). Same
 * dimensions as [PosterCard] so the two row kinds align visually.
 *
 * [modifier] lands on the Card — the focusable — so callers can attach a
 * FocusRequester for the row-entry rule (§10: DOWN lands on the first card).
 */
@Composable
fun ContinueWatchingCard(progress: WatchProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val width = CardSizeTokens.posterWidth()
    Column(modifier = Modifier.width(width)) {
        Card(onClick = onClick, modifier = modifier) {
            Box {
                AsyncImage(
                    model = progress.poster,
                    contentDescription = progress.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(width)
                        .height(CardSizeTokens.posterHeight()),
                )
                // Watched-fraction bar over a full-width track.
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
        Text(
            text = progress.title,
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
