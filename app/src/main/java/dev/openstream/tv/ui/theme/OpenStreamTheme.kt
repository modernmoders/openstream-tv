package dev.openstream.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** App background — near-black; TV apps are dark for a reason (MASTER_PLAN §5.8). */
val AppBackground = Color(0xFF101018)

/** Secondary text color on the dark background. */
val MutedText = Color(0xFFB0B0C0)

/**
 * Dark theme only in v1 (MASTER_PLAN §5.8). All screens wrap in this so a
 * future palette change happens in exactly one place.
 */
@Composable
fun OpenStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
