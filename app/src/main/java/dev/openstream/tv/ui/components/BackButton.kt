package dev.openstream.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/**
 * Visible on-screen Back for every screen below Home (§10 elder-friendly:
 * users shouldn't need to know which remote key escapes a screen). Does
 * exactly what the remote's BACK does — pops one level, never more (§5.4).
 *
 * Rule: this must never be a screen's INITIAL focus, or OK-on-entry would
 * bounce the user straight back out. Screens that place it first in layout
 * order anchor their primary action with a FocusRequester instead.
 */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onBack, modifier = modifier) { Text("← Back") }
}
