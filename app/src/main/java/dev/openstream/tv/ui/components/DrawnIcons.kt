package dev.openstream.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/**
 * Canvas-drawn UI glyphs. House rule (see NavRail's [NavDestination.icon]):
 * the boxes' fonts render unicode symbols/emoji inconsistently — the round-14
 * "pause is totally out of the ballpark" report was the ⏸ emoji drawn by
 * whatever fallback font the box picked. Everything here is stroked/filled
 * geometry, so it looks identical on every box and tints like text.
 */

/** A ⚙-style gear: ring + eight stubby teeth + hollow hub. */
@Composable
fun GearIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.26f, style = stroke)
        repeat(8) { i ->
            val a = (Math.PI / 4.0 * i).toFloat()
            val c = w / 2f
            drawLine(
                tint,
                Offset(c + w * 0.30f * cos(a), c + w * 0.30f * sin(a)),
                Offset(c + w * 0.44f * cos(a), c + w * 0.44f * sin(a)),
                strokeWidth = w * 0.13f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A small ▾ caret — the "this opens options" hint on filter pills. */
@Composable
fun CaretDownIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.20f, w * 0.35f)
            lineTo(w * 0.50f, w * 0.68f)
            lineTo(w * 0.80f, w * 0.35f)
        }
        drawPath(path, tint, style = Stroke(width = w * 0.14f, cap = StrokeCap.Round))
    }
}

/** A » double chevron — the "this jumps forward" mark on the Skip pill
 *  (owner's player mockup, Round 17). */
@Composable
fun ChevronsRightIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round)
        listOf(0.16f, 0.50f).forEach { x ->
            val path = Path().apply {
                moveTo(w * x, w * 0.22f)
                lineTo(w * (x + 0.32f), w * 0.50f)
                lineTo(w * x, w * 0.78f)
            }
            drawPath(path, tint, style = stroke)
        }
    }
}

/** Transport glyphs for the player's control bar. */
enum class PlayerGlyphKind { PLAY, PAUSE, PREVIOUS, NEXT }

@Composable
fun PlayerGlyph(kind: PlayerGlyphKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (kind) {
            PlayerGlyphKind.PLAY -> playTriangle(tint)
            PlayerGlyphKind.PAUSE -> pauseBars(tint)
            PlayerGlyphKind.PREVIOUS -> {
                // bar + leftward triangle (⏮ shape, drawn)
                val w = size.width
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, w * 0.20f),
                    size = Size(w * 0.10f, w * 0.60f),
                    cornerRadius = CornerRadius(w * 0.04f),
                )
                rotate(180f) { playTriangle(tint, inset = 0.30f) }
            }
            PlayerGlyphKind.NEXT -> {
                val w = size.width
                playTriangle(tint, inset = 0.30f)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.74f, w * 0.20f),
                    size = Size(w * 0.10f, w * 0.60f),
                    cornerRadius = CornerRadius(w * 0.04f),
                )
            }
        }
    }
}

/** Filled play triangle; [inset] shrinks it for the composite prev/next glyphs. */
private fun DrawScope.playTriangle(tint: Color, inset: Float = 0.22f) {
    val w = size.width
    val left = w * (inset + 0.06f)
    val right = w * (1f - inset)
    val top = w * inset
    val bottom = w * (1f - inset)
    val path = Path().apply {
        moveTo(left, top)
        lineTo(right, w / 2f)
        lineTo(left, bottom)
        close()
    }
    drawPath(path, tint)
}

private fun DrawScope.pauseBars(tint: Color) {
    val w = size.width
    val barWidth = w * 0.18f
    val radius = CornerRadius(w * 0.05f)
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.24f, w * 0.20f),
        size = Size(barWidth, w * 0.60f),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.58f, w * 0.20f),
        size = Size(barWidth, w * 0.60f),
        cornerRadius = radius,
    )
}
