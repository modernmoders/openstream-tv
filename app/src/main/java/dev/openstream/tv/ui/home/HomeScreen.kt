package dev.openstream.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Placeholder home screen. Becomes Continue Watching + catalog rows later in
 * Phase 1 (MASTER_PLAN §5.6); for now it only links to the addon manager.
 * Padding = overscan safe margins (§5.3).
 */
@Composable
fun HomeScreen(onManageAddons: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = "OpenStream TV",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
            )
            Text(
                text = "No catalogs yet — install an addon to get started",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
            )
            Button(onClick = onManageAddons) {
                Text("Manage addons")
            }
        }
    }
}
