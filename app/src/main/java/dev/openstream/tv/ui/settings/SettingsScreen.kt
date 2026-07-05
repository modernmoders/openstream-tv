package dev.openstream.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.theme.AppBackground

/**
 * Settings home (Phase 4). Deliberately a short list of large, described
 * entries — depth lives one level down, never on this screen (§10
 * elder-friendly: customization is optional, the main path stays simple).
 * Future units add entries here: player preference (§6.2 "Always use"),
 * autoplay options (§7.1.7), preferred audio/subtitle language
 * (DECISIONS #19), global density (§5.1).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHomeRows: () -> Unit,
) {
    // Predictable entry point: land on the first setting, not Back.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(onBack)
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }

        Spacer(Modifier.padding(top = 24.dp))

        SettingEntry(
            title = "Home rows",
            description = "Reorder, rename, or hide the rows on the home screen",
            onClick = onHomeRows,
            modifier = Modifier.focusRequester(firstFocus),
        )
    }
}

@Composable
private fun SettingEntry(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            // No explicit color: inherit the button's content color so the
            // focused (inverted) state stays readable.
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
