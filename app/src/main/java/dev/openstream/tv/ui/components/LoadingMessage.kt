package dev.openstream.tv.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.MutedText

/**
 * The one loading indicator, used by every screen (owner request
 * 2026-07-05 round 5: the ghost-comet loader read as static/broken on the
 * onn boxes — "replace all loading with a faint or pulsing 'loading'").
 *
 * The pulse is read inside graphicsLayer's lambda — layer-phase only, no
 * per-frame recomposition (DECISIONS #22). If a device ever suppresses
 * infinite animations it degrades to faint static text, which still reads
 * correctly as "working on it".
 */
@Composable
fun LoadingMessage(
    modifier: Modifier = Modifier,
    text: String = "Loading…",
    horizontalPadding: Dp = 48.dp,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val pulse by rememberInfiniteTransition(label = "loading")
        .animateFloat(
            initialValue = 0.30f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
            label = "pulse",
        )
    Text(
        text = text,
        style = style,
        color = MutedText,
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .graphicsLayer { alpha = pulse },
    )
}
