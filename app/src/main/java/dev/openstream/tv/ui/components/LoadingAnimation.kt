package dev.openstream.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.MutedText

/**
 * A looping "getting your show ready" spinner for the player's load/test phase
 * (owner request 2026-07-08). Shown while a stream buffers and while the debrid
 * services (Real-Debrid/Torbox/Debridio) serve their static "resolving" clips,
 * so the viewer never stares at a black screen or a raw placeholder video.
 *
 * Deliberately cheap for the 32-bit onn boxes: the ring spins by writing
 * [graphicsLayer] rotationZ inside its lambda (layer phase only, no per-frame
 * recomposition — same discipline as [LoadingMessage], DECISIONS #22). If a box
 * ever suppresses infinite animations it degrades to a static ring + text,
 * which still reads correctly as "working on it" — never the "broken/static"
 * look the old ghost-comet loader had.
 */
@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    text: String = "Getting your show ready…",
) {
    val angle by rememberInfiniteTransition(label = "spinner")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
            label = "angle",
        )
    val track = Color.White.copy(alpha = 0.14f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(64.dp)
                // rotationZ read in the layer lambda: the spin never triggers
                // recomposition, so it stays smooth on the weak boxes.
                .graphicsLayer { rotationZ = angle },
        ) {
            val stroke = Stroke(width = 6.dp.toPx())
            // Faint full ring so the arc always reads as part of a circle even
            // at the animation's degraded (static) fallback.
            drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = Accent, startAngle = 0f, sweepAngle = 90f, useCenter = false, style = stroke)
        }
        if (text.isNotBlank()) {
            Text(text = text, style = MaterialTheme.typography.titleMedium, color = MutedText)
        }
    }
}
