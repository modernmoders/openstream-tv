package dev.openstream.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.MutedText

/**
 * The "getting your show ready" loader for the player's load/test phase:
 * five soft periwinkle dots that rise and brighten in a travelling wave —
 * the owner's own "SStreams Loader" design (Claude Design export,
 * 2026-07-11), replacing the alpha.30 spinning arc. Timing/keyframe math
 * lives in [WaveLoader.kt][waveDotPose], pure and unit-tested.
 *
 * Box discipline (both lessons already paid for):
 * - Time comes from the frame clock ([withFrameNanos]), NOT a
 *   rememberInfiniteTransition — the boxes run with animator duration scale
 *   0, which froze the old spinner into a still image (owner 2026-07-09).
 * - The per-frame write is read ONLY inside the Canvas draw block, so each
 *   frame is a redraw, never a recomposition (DECISIONS #22 discipline).
 */
@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    text: String = "Getting your show ready…",
) {
    var timeMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                timeMs = (now - startNanos) / 1_000_000f
            }
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val dot = 10.dp
        val gap = 16.dp
        val rise = 14.dp
        Canvas(
            modifier = Modifier
                .width(dot * WAVE_DOT_COUNT + gap * (WAVE_DOT_COUNT - 1))
                .height(rise + dot + 4.dp),
        ) {
            val radius = dot.toPx() / 2f
            val stride = dot.toPx() + gap.toPx()
            val restY = size.height - radius - 2.dp.toPx()
            repeat(WAVE_DOT_COUNT) { i ->
                // `timeMs` is read here, in the draw phase: the wave animates
                // by redrawing this Canvas only.
                val pose = waveDotPose(wavePhase(timeMs, i))
                drawCircle(
                    color = DotColor.copy(alpha = pose.alpha),
                    radius = radius,
                    center = Offset(radius + i * stride, restY - rise.toPx() * pose.rise),
                )
            }
        }
        if (text.isNotBlank()) {
            Text(text = text, style = MaterialTheme.typography.titleMedium, color = MutedText)
        }
    }
}

/** The design's accent (#A8CBE8) — the brand periwinkle, not the UI accent blue. */
private val DotColor = Color(0xFFA8CBE8)
