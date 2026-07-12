package dev.openstream.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** App background — near-black; TV apps are dark for a reason (MASTER_PLAN §5.8). */
val AppBackground = Color(0xFF101018)

/** Secondary text color on the dark background. */
val MutedText = Color(0xFFB0B0C0)

// ── Refined UI tokens (2026-07-06 owner UX pass, DECISIONS #29) ──────────────
// One calm accent, subtle surfaces, hairline borders — the shared vocabulary
// every menu/list row uses so the app reads consistent and quiet on a big
// screen across the room. Keep the palette here (this file is the one place a
// future theme change lands).

/** The single interaction accent — focus rings, selection ticks. */
val Accent = Color(0xFF4DA3FF)

/**
 * Series-completion display (owner Round 17: "a different colored display for
 * how much the user has watched out of ALL of the series"). Deliberately NOT
 * the accent — warm amber so "how far through the SHOW" never gets read as
 * the blue "how far through the EPISODE" ring. The one sanctioned second hue;
 * anything else stays on [Accent].
 */
val SeriesAmber = Color(0xFFE2B457)

/** Resting surface for a list row / card, just above the page background. */
val SurfaceCard = Color(0xFF17171F)

/** Focused surface — a calm accent tint, not a jarring white invert. */
val SurfaceCardFocused = Color(0xFF1E2A44)

/** Hairline divider/border on resting surfaces. */
val Hairline = Color(0xFF262633)

/**
 * Dark theme only in v1 (MASTER_PLAN §5.8). All screens wrap in this so a
 * future palette change happens in exactly one place.
 */
@Composable
fun OpenStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
