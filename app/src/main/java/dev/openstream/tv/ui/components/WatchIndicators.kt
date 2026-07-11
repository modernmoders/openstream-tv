package dev.openstream.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.theme.Accent
import kotlin.math.roundToInt

/**
 * The watched-system glyph kit (design_handoff_watched_system): ONE visual
 * language for watch state across poster tiles and episode rows.
 *
 * Three states — unwatched artwork stays PRISTINE (no empty badges on
 * posters), in-progress gets a progress ring with the percent inside,
 * watched gets a solid accent check disc while its artwork dims so the
 * unwatched content pops forward.
 *
 * All geometry is Canvas-drawn (DrawnIcons house rule — box fonts render
 * unicode glyphs inconsistently) and static: nothing here animates, so the
 * whole system is free on the 32-bit boxes.
 */

/** Dim laid over WATCHED artwork so it recedes (handoff: rgba(4,8,14,0.48)). */
val WatchedArtworkDim = Color(0x7A04080E)

/** Fill behind a poster ring so it reads over any artwork (handoff scrim). */
private val RingScrim = Color(0x9904090F)

/** Ring track — the unfilled remainder of the circle. */
private val RingTrack = Color(0x47FFFFFF)

/** Check stroke on the accent disc — dark, per the handoff. */
private val CheckInk = Color(0xFF06121F)

/** Dashed "not started" circle stroke (handoff: white at 22%). */
private val UnwatchedStroke = Color(0x38FFFFFF)

/**
 * In-progress ring: track circle + accent arc from 12 o'clock, percent number
 * centered inside. [scrim] fills the ring's interior so it stays legible over
 * poster artwork; episode rows sit on their own surface and don't need it.
 * Geometry follows the handoff's 44px ring: r/size ≈ 0.41, stroke ≈ 0.08.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    showPercent: Boolean = true,
    scrim: Boolean = true,
) {
    val pct = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val stroke = w * 0.08f
            val radius = w * 0.41f
            if (scrim) drawCircle(RingScrim, radius = w / 2f)
            drawCircle(RingTrack, radius = radius, style = Stroke(width = stroke))
            drawArc(
                color = Accent,
                startAngle = -90f, // progress starts at 12 o'clock (handoff)
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(w / 2f - radius, w / 2f - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (showPercent) {
            Text(
                text = "$pct",
                color = Color.White,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** WATCHED disc: solid accent circle with a drawn dark check. */
@Composable
fun WatchedDisc(modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(Accent)
        val check = Path().apply {
            moveTo(w * 0.29f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.67f)
            lineTo(w * 0.72f, w * 0.36f)
        }
        drawPath(
            path = check,
            color = CheckInk,
            style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * Not-started marker for episode rows only: a dashed empty circle. Posters
 * never show it — unwatched artwork stays clean (handoff design principle).
 */
@Composable
fun UnwatchedRing(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.09f
        drawCircle(
            color = UnwatchedStroke,
            radius = (w - stroke) / 2f,
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.16f, w * 0.13f)),
            ),
        )
    }
}

/**
 * Whole minutes remaining, rounded UP so an almost-done episode says
 * "1 min left", never the lie "0 min left". Pure for unit tests.
 */
fun minutesLeftOf(progress: WatchProgress): Int {
    val remaining = (progress.durationMs - progress.positionMs).coerceAtLeast(0L)
    return ((remaining + 59_999) / 60_000).toInt()
}
