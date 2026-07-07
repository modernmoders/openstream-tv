package dev.openstream.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A crisp microphone glyph, hand-authored (no material-icons-extended
 * dependency exists in this project — pulling one in for a single glyph
 * would be a heavier change than drawing it, KISS). Replaces the "🎤" emoji
 * on the Search screen's voice-search button (owner: "swap that microphone
 * for a better, clearer icon").
 *
 * Classic capsule-body + stand glyph on a 24x24 grid, tintable via
 * [androidx.compose.ui.graphics.Color] like any vector asset.
 */
val MicIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Capsule body of the mic.
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
            lineTo(15f, 5f)
            curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
            curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
            lineTo(9f, 11f)
            curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
            close()
        }
        // Stand + base: the U-shaped cradle, neck, and foot.
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.3f, 11f)
            curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
            curveTo(9.24f, 16.1f, 6.7f, 14f, 6.7f, 11f)
            lineTo(5f, 11f)
            curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 17.72f)
            curveTo(16.28f, 17.23f, 19f, 14.41f, 19f, 11f)
            lineTo(17.3f, 11f)
            close()
        }
    }.build()

/**
 * Renders [MicIcon] tinted to [tint], the size/behavior swap-in for the old
 * "🎤" Text glyph — same footprint inside a button, just a real vector so it
 * reads crisp at any density instead of relying on an emoji font.
 */
@Composable
fun MicIconImage(tint: Color, modifier: Modifier = Modifier) {
    val icon = remember { MicIcon }
    Image(
        imageVector = icon,
        contentDescription = "Voice search",
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}
