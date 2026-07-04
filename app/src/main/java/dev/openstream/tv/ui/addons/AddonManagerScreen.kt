package dev.openstream.tv.ui.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Installed-addon list: enable/disable, reorder (stream-group order, §4.1.7),
 * uninstall, and the entry point to installing a new addon by URL.
 * All controls are Buttons — focus behavior comes free from tv-material (§5.4).
 */
@Composable
fun AddonManagerScreen(
    onAddAddon: () -> Unit,
    viewModel: AddonManagerViewModel = hiltViewModel(),
) {
    val addons by viewModel.addons.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Addons",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAddAddon) { Text("Add addon") }
        }

        Spacer(Modifier.padding(top = 16.dp))

        if (addons.isEmpty()) {
            Text(
                text = "No addons installed. Add one by its manifest URL.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Stable keys: rows animate/reuse correctly on reorder (§5.7)
                items(addons, key = { it.manifestUrl }) { addon ->
                    AddonRow(
                        addon = addon,
                        onToggleEnabled = {
                            viewModel.setEnabled(addon.manifestUrl, !addon.enabled)
                        },
                        onMoveUp = { viewModel.move(addon.manifestUrl, -1) },
                        onMoveDown = { viewModel.move(addon.manifestUrl, +1) },
                        onUninstall = { viewModel.uninstall(addon.manifestUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonRow(
    addon: InstalledAddon,
    onToggleEnabled: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUninstall: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${addon.manifest.name}  v${addon.manifest.version}",
                style = MaterialTheme.typography.titleMedium,
                color = if (addon.enabled) Color.White else MutedText,
            )
            Text(
                text = addon.manifestUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(500.dp),
            )
            Text(
                text = addon.manifest.types.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
        }
        Button(onClick = onToggleEnabled) {
            Text(if (addon.enabled) "Enabled" else "Disabled")
        }
        Button(onClick = onMoveUp) { Text("▲") }
        Button(onClick = onMoveDown) { Text("▼") }
        Button(onClick = onUninstall) { Text("Remove") }
    }
}
