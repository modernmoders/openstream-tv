package dev.openstream.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.MutedText

/**
 * The "Up next" card from the owner's player mockup (Round 17): episode
 * thumbnail, an "Up next" eyebrow over the episode line, then a draining
 * countdown ring beside a solid-accent "Play now" and a see-through "Cancel"
 * ("the second one a little more see-through" — owner). Shared by the
 * in-player credits countdown and the between-episodes Up Next flow.
 *
 * NOT focusable on purpose: both hosts intercept OK (play now) and BACK
 * (cancel) globally, so the pills carry small key hints instead of pulling
 * TV focus off the video.
 */
@Composable
fun NextEpisodeCard(
    episodeLabel: String,
    thumbnail: String?,
    secondsLeft: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            // Translucent enough that the credits read underneath, opaque
            // enough that the card stays legible over bright frames.
            .background(Color(0xE014171C), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(Color(0xFF23232F)),
                    error = ColorPainter(Color(0xFF23232F)),
                    modifier = Modifier
                        .width(92.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Up next", style = MaterialTheme.typography.labelMedium, color = MutedText)
                Text(
                    text = episodeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The ring drains with the countdown — the "this will happen by
            // itself" cue, no words needed (mockup).
            ProgressRing(
                fraction = secondsLeft.toFloat() / totalSeconds.coerceAtLeast(1),
                size = 26.dp,
                showPercent = false,
                scrim = false,
            )
            HintPill(
                label = "Play now",
                keyHint = "OK",
                background = Accent,
                labelColor = Color(0xFF0E0E16),
                hintColor = Color(0x990E0E16),
            )
            HintPill(
                label = "Cancel",
                keyHint = "BACK",
                background = Color(0x24FFFFFF),
                labelColor = Color.White,
                hintColor = Color(0x8AFFFFFF),
            )
        }
    }
}

/** A non-focusable action pill with a tiny remote-key hint beside its label. */
@Composable
private fun HintPill(
    label: String,
    keyHint: String,
    background: Color,
    labelColor: Color,
    hintColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
        )
        Text(
            text = keyHint,
            style = MaterialTheme.typography.labelSmall,
            color = hintColor,
        )
    }
}
