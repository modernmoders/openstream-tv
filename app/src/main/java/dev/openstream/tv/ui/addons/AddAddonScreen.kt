package dev.openstream.tv.ui.addons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.Manifest
import dev.openstream.tv.ui.addons.AddAddonViewModel.UiState
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
    var url by rememberSaveable { mutableStateOf("") }
    val urlFieldFocus = remember { FocusRequester() }

    LaunchedEffect(state) {
        if (state is UiState.Installed) onInstalled()
    }
    LaunchedEffect(Unit) {
        urlFieldFocus.requestFocus() // land the D-pad on the URL field (§5.4)
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
            text = "Paste the addon's manifest URL (ends in manifest.json; stremio:// links work too)",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedText,
        )

        UrlField(
            value = url,
            onValueChange = { url = it },
            onSubmit = { viewModel.fetchPreview(url) },
            focusRequester = urlFieldFocus,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.fetchPreview(url) },
                enabled = state !is UiState.Fetching && state !is UiState.Installing,
            ) { Text("Fetch addon") }
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
            else -> Unit
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

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(Color.White),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            // A focused text field consumes D-pad by default, trapping focus
            // (§5.4: focus must never be lost OR trapped). Route vertical
            // D-pad presses to normal focus navigation instead.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            }
            .background(Color(0xFF1A1A28), RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                // Visible focus indicator on every focusable (§5.4)
                color = if (isFocused) Color(0xFF4DA3FF) else Color(0xFF33334A),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
    )
}
