package dev.openstream.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
 * recomposition — same discipline as [LoadingMessage], DECISIONS #22).
 *
 * The angle is driven off the frame clock ([withFrameNanos]) rather than a
 * [rememberInfiniteTransition]: infinite transitions are gated by the system
 * animator duration scale, which is commonly 0 on TV boxes (reduced/off
 * animations) — that froze the ring into a still image (owner 2026-07-09).
 * The frame clock runs regardless of that scale, so this actually spins.
 */
@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    text: String = "Getting your show ready…",
) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                // 1.1s per full turn; only the graphicsLayer lambda reads
                // `angle`, so this is a layer-phase write (no recomposition).
                angle = ((now - startNanos) / 1_000_000f / 1_100f * 360f) % 360f
            }
        }
    }
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
