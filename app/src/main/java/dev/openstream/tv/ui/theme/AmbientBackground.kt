package dev.openstream.tv.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Ambient section backgrounds (owner round 10): a quiet color wash behind
 * each area of the app instead of one flat black — Home feels different from
 * Discover feels different from Settings, without ever shouting.
 *
 * Design constraints, in order:
 *  - TV-dark stays TV-dark (§5.8): these are deep tints of [AppBackground],
 *    not bright pastels — posters and text must keep their contrast.
 *  - Draw-phase only, zero animation (DECISIONS #22 house rule): a vertical
 *    gradient plus one soft radial glow, drawn once per size change. No
 *    per-frame cost on the 32-bit boxes.
 *  - Media surfaces (Details/Streams/Player) are NOT ambient: their scrims
 *    blend to the flat [AppBackground] and the artwork already provides the
 *    color there.
 */
enum class AmbientSection { HOME, DISCOVER, LIBRARY, SEARCH, SETTINGS, CONNECT }

/**
 * One section's wash: a top→bottom gradient with a soft glow anchored at a
 * relative position ([glowX]/[glowY] are fractions of the screen size).
 */
private class AmbientPalette(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val glowX: Float,
    val glowY: Float,
)

/** Deep-tint palettes: hue says "where am I", value stays near-black. */
private fun paletteFor(section: AmbientSection): AmbientPalette = when (section) {
    // Home — the brand's calm blue, glow up-left where the header/hero sits.
    AmbientSection.HOME -> AmbientPalette(
        top = Color(0xFF131628), bottom = Color(0xFF0D0D13),
        glow = Color(0xFF4DA3FF).copy(alpha = 0.10f), glowX = 0.15f, glowY = 0.05f,
    )
    // Discover — a teal cast, glow up-right behind the filter bar.
    AmbientSection.DISCOVER -> AmbientPalette(
        top = Color(0xFF0F1B20), bottom = Color(0xFF0D0D13),
        glow = Color(0xFF3FD8C7).copy(alpha = 0.07f), glowX = 0.85f, glowY = 0.05f,
    )
    // Library — a warm amber cast (the "your shelf" room), glow up-left
    // behind the header, distinct from Discover's teal next door on the rail.
    AmbientSection.LIBRARY -> AmbientPalette(
        top = Color(0xFF1C1712), bottom = Color(0xFF0D0D13),
        glow = Color(0xFFFFC46B).copy(alpha = 0.07f), glowX = 0.15f, glowY = 0.05f,
    )
    // Search — violet, glow behind the centered query field.
    AmbientSection.SEARCH -> AmbientPalette(
        top = Color(0xFF171226), bottom = Color(0xFF0D0D13),
        glow = Color(0xFF9A7DFF).copy(alpha = 0.08f), glowX = 0.5f, glowY = 0.0f,
    )
    // Settings family — neutral slate-lavender; calm, administrative.
    AmbientSection.SETTINGS -> AmbientPalette(
        top = Color(0xFF14141F), bottom = Color(0xFF0D0D13),
        glow = Color(0xFF8FA1FF).copy(alpha = 0.06f), glowX = 0.1f, glowY = 0.0f,
    )
    // Connect/Welcome — a touch of warmth for the greeting moment.
    AmbientSection.CONNECT -> AmbientPalette(
        top = Color(0xFF1B141F), bottom = Color(0xFF0D0D13),
        glow = Color(0xFFFFA07A).copy(alpha = 0.06f), glowX = 0.5f, glowY = 0.1f,
    )
}

/**
 * Drop-in replacement for `background(AppBackground)` on a screen's root.
 * Opaque (the gradient covers everything), so screens need no extra base.
 */
fun Modifier.ambientBackground(section: AmbientSection): Modifier = drawBehind {
    val p = paletteFor(section)
    drawRect(Brush.verticalGradient(0f to p.top, 1f to p.bottom))
    drawRect(
        Brush.radialGradient(
            0f to p.glow, 1f to Color.Transparent,
            center = Offset(size.width * p.glowX, size.height * p.glowY),
            // Wide and faint beats small and visible: the glow should read as
            // light in the room, not as a drawn circle.
            radius = size.maxDimension * 0.75f,
        )
    )
}
