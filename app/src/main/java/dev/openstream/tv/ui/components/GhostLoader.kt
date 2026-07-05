package dev.openstream.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Full-screen ghost loader (owner request 2026-07-05): the app's figure-8
 * mark, traced by a comet with a fading tail — "ghostly motion" per the
 * reference gradient art in docs/reference.
 *
 * Built for the 32-bit onn boxes: ONE infinite float drives everything and
 * it is read only inside the Canvas draw lambda, so each frame is a redraw
 * of ~90 tiny circles — no recomposition, no layout, no allocation. This
 * pattern (draw/layer-phase-only animation reads) is the rule for every
 * always-running animation in this app.
 */
@Composable
fun GhostLoadingOverlay(modifier: Modifier = Modifier, label: String? = null) {
    val transition = rememberInfiniteTransition(label = "ghost")
    // One full lap of the eight every 2.8s; LinearEasing keeps speed even
    // through the crossover so the comet never appears to stall.
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "head",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(200.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                // Vertical figure-8 (the logo mark): x=sin(2a), y=-cos(a).
                fun point(u: Float): Offset {
                    val a = u * 2f * PI.toFloat()
                    return Offset(
                        cx + size.width * 0.30f * sin(2f * a),
                        cy - size.height * 0.40f * cos(a),
                    )
                }

                // Faint full path — the "ghost" of the mark, always visible.
                val pathDots = 72
                for (i in 0 until pathDots) {
                    drawCircle(
                        color = GhostDim,
                        radius = 2.dp.toPx(),
                        center = point(i / pathDots.toFloat()),
                    )
                }

                // Comet: head + trail with quadratic alpha falloff. Drawing
                // back-to-front keeps the bright head on top of its tail.
                val trail = 22
                for (k in trail downTo 0) {
                    val fade = 1f - k / trail.toFloat()
                    drawCircle(
                        color = lerp(GhostTint, Color.White, fade.pow(2)),
                        alpha = 0.85f * fade.pow(2),
                        radius = (2f + 5f * fade) * density,
                        center = point(head - k * 0.011f),
                    )
                }
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = dev.openstream.tv.ui.theme.MutedText,
                )
            }
        }
    }
}

/**
 * Poster-shaped shimmer placeholder for grids/rows still waiting on an
 * addon. The pulse reads its animated value inside graphicsLayer's lambda —
 * layer-phase only, so like the ghost it never recomposes per frame.
 */
@Composable
fun SkeletonPosterCard(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer { alpha = pulse }
                .background(SkeletonFill, RoundedCornerShape(8.dp)),
        )
        Box(
            Modifier
                .size(width * 0.7f, 10.dp)
                .graphicsLayer { alpha = pulse }
                .background(SkeletonFill, RoundedCornerShape(4.dp)),
        )
    }
}

/** Pastel lavender, sampled from the owner's ghost-gradient reference art. */
private val GhostTint = Color(0xFF9C8FC4)
private val GhostDim = Color(0x2E9C8FC4)
private val SkeletonFill = Color(0xFF23232F)
