package dev.openstream.tv.ui.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.Manifest
import dev.openstream.tv.ui.addons.AddAddonViewModel.UiState
import dev.openstream.tv.ui.components.TvTextField
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Install-by-URL flow (MASTER_PLAN §4.1.1): URL → fetch → manifest preview →
 * explicit Install. The on-screen keyboard appears when the field is clicked.
 */
@Composable
fun AddAddonScreen(
    onInstalled: () -> Unit,
    viewModel: AddAddonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remoteEntryUrl by viewModel.remoteEntryUrl.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    val urlFieldFocus = remember { FocusRequester() }

    LaunchedEffect(state) {
        if (state is UiState.Installed) onInstalled()
    }
    LaunchedEffect(Unit) {
        urlFieldFocus.requestFocus() // land the D-pad on the URL field (§5.4)
    }
    // Browser entry page lives exactly as long as this screen (RemoteEntryServer KDoc).
    DisposableEffect(Unit) {
        viewModel.startRemoteEntry()
        onDispose { viewModel.stopRemoteEntry() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Add addon",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
        )
        Text(
            text = "Paste an addon manifest URL (ends in manifest.json; stremio:// works too) " +
                "or a setup link that installs a whole set at once",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
        )

        TvTextField(
            value = url,
            onValueChange = { url = it },
            onSubmit = { viewModel.fetchPreview(url) },
            focusRequester = urlFieldFocus,
            keyboardType = KeyboardType.Uri,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.fetchPreview(url) },
                enabled = state !is UiState.Fetching && state !is UiState.Installing,
            ) { Text("Fetch addon") }
        }

        remoteEntryUrl?.let { entryUrl ->
            Text(
                text = "Tired of typing? On a phone or computer on this network, " +
                    "open  $entryUrl  and paste the link there.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedText,
            )
        }

        when (val s = state) {
            is UiState.Fetching -> Text("Fetching manifest…", color = MutedText)
            is UiState.Installing -> Text("Installing…", color = MutedText)
            is UiState.Error -> Text(s.message, color = Color(0xFFFF6B6B))
            is UiState.Preview -> ManifestPreview(
                manifest = s.manifest,
                onInstall = viewModel::confirmInstall,
                onCancel = viewModel::dismissPreview,
            )
            is UiState.ProfilePreview -> ProfilePreviewPanel(
                preview = s,
                onInstallAll = viewModel::confirmInstallProfile,
                onCancel = viewModel::dismissPreview,
            )
            else -> Unit
        }
    }
}

/**
 * Setup-link confirmation (DECISIONS #14): every addon the link resolves to,
 * good or broken, before anything installs. URLs are deliberately absent —
 * they embed personal tokens and a TV screen is a semi-public place.
 */
@Composable
private fun ProfilePreviewPanel(
    preview: UiState.ProfilePreview,
    onInstallAll: () -> Unit,
    onCancel: () -> Unit,
) {
    val good = preview.entries.count { it.ok }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A28), RoundedCornerShape(8.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = preview.profileName,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        preview.entries.forEach { entry ->
            Text(
                text = (if (entry.ok) "✓  " else "✗  ") + entry.displayName +
                    "  ·  " + entry.detail,
                color = if (entry.ok) MutedText else Color(0xFFFF6B6B),
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (good > 0) {
                Button(onClick = onInstallAll) {
                    Text(if (good == 1) "Install 1 addon" else "Install $good addons")
                }
            }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** The pre-install confirmation (§4.1.1): show what this addon actually is. */
@Composable
private fun ManifestPreview(
    manifest: Manifest,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A28), RoundedCornerShape(8.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${manifest.name}  v${manifest.version}",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        if (manifest.description.isNotBlank()) {
            Text(manifest.description, color = MutedText, maxLines = 3)
        }
        Text("Types: " + manifest.types.joinToString(", "), color = MutedText)
        Text(
            "Provides: " + manifest.resources.joinToString(", ") { it.name },
            color = MutedText,
        )
        if (manifest.catalogs.isNotEmpty()) {
            Text(
                "Catalogs (${manifest.catalogs.size}): " +
                    manifest.catalogs.take(5).joinToString(", ") { it.name.ifBlank { it.id } } +
                    if (manifest.catalogs.size > 5) ", …" else "",
                color = MutedText,
                maxLines = 2,
            )
        }
        if (manifest.behaviorHints.adult) {
            Text("⚠ This addon reports adult content", color = Color(0xFFFFB86B))
        }
        if (manifest.behaviorHints.configurationRequired) {
            Text(
                "⚠ This addon says it needs configuration — install its configured URL instead",
                color = Color(0xFFFFB86B),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onInstall) { Text("Install") }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

