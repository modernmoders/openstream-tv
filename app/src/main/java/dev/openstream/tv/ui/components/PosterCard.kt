package dev.openstream.tv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.ui.theme.CardSizeTokens
import dev.openstream.tv.ui.theme.MutedText

/**
 * The one poster card used by every browse surface (home rows, discover
 * grid, search results). 2:3 ratio is contract (§5.2); size derives from
 * CardSizeTokens so the density setting stays one number (§5.1).
 *
 * [modifier] lands on the Card — the focusable — so callers can attach
 * FocusRequesters for row-entry rules (§10 Phase 4 search focus rule).
 */
@Composable
fun PosterCard(item: MetaItem, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    val width = CardSizeTokens.posterWidth()
    Column(modifier = Modifier.width(width)) {
        Card(onClick = onClick, modifier = modifier) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(width)
                    .height(CardSizeTokens.posterHeight()),
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Inline status text used under row/grid headers (loading, failure chip). */
@Composable
fun RowMessage(text: String, horizontalPadding: androidx.compose.ui.unit.Dp = 48.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MutedText,
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
    )
}
