package dev.openstream.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.data.MAX_POSTER_COLUMNS
import dev.openstream.tv.data.MIN_POSTER_COLUMNS
import dev.openstream.tv.data.PLAYER_ASK
import dev.openstream.tv.data.PLAYER_INTERNAL
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Settings home (Phase 4). Deliberately a short list of large, described
 * entries — depth lives one level down, never on this screen (§10
 * elder-friendly: customization is optional, the main path stays simple).
 * Future units add entries here: player preference (§6.2 "Always use"),
 * autoplay options (§7.1.7), preferred audio/subtitle language
 * (DECISIONS #19).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHomeRows: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val columns by viewModel.posterColumns.collectAsStateWithLifecycle()
    val playerPref by viewModel.playerPref.collectAsStateWithLifecycle()
    val autoPlay by viewModel.autoPlayFirstStream.collectAsStateWithLifecycle()
    var pickingDensity by remember { mutableStateOf(false) }
    var pickingPlayer by remember { mutableStateOf(false) }

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

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingEntry(
                title = "Home rows",
                description = "Reorder, rename, or hide the rows on the home screen",
                onClick = onHomeRows,
                modifier = Modifier.focusRequester(firstFocus),
            )
            SettingEntry(
                title = "Poster size",
                description = "$columns posters per row — smaller posters fit more on screen",
                onClick = { pickingDensity = true },
            )
            SettingEntry(
                title = "Player",
                description = "Pressing OK on a stream uses: " +
                    playerPrefLabel(playerPref, viewModel.installedPlayers),
                onClick = { pickingPlayer = true },
            )
            // OK toggles directly — an on/off needs no picker dialog.
            SettingEntry(
                title = "Auto-play first stream",
                description = if (autoPlay) {
                    "On — picking a movie or episode starts playing right away; " +
                        "a broken stream quietly tries the next server"
                } else {
                    "Off — picking a movie or episode shows the list of streams"
                },
                onClick = { viewModel.setAutoPlayFirstStream(!autoPlay) },
            )
        }
    }

    if (pickingDensity) {
        DensityDialog(
            current = columns,
            onPick = { picked ->
                viewModel.setPosterColumns(picked)
                pickingDensity = false
            },
            onDismiss = { pickingDensity = false },
        )
    }
    if (pickingPlayer) {
        PlayerPrefDialog(
            current = playerPref,
            installedPlayers = viewModel.installedPlayers,
            onPick = { picked ->
                viewModel.setPlayerPref(picked)
                pickingPlayer = false
            },
            onDismiss = { pickingPlayer = false },
        )
    }
}

/** Human label for the stored player preference (§6.2). */
private fun playerPrefLabel(pref: String, installed: List<ExternalPlayerPort.Choice>): String =
    when (pref) {
        PLAYER_INTERNAL -> "the internal player"
        PLAYER_ASK -> "ask every time"
        else -> installed.firstOrNull { it.player.name == pref }?.player?.label
            // Preferred player got uninstalled: say what actually happens.
            ?: "the internal player"
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

/**
 * "Always use" player picker (§6.2): internal first (the default), then only
 * the players actually installed, then Ask. Long-press on a stream stays the
 * one-off override regardless of this setting.
 */
@Composable
private fun PlayerPrefDialog(
    current: String,
    installedPlayers: List<ExternalPlayerPort.Choice>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    @Composable
    fun option(value: String, label: String) {
        Button(
            onClick = { onPick(value) },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (value == current) Modifier.focusRequester(selectedFocus)
                    else Modifier
                ),
        ) { Text(label + if (value == current) "  ✓" else "") }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(460.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = "Pressing OK on a stream uses…",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            option(PLAYER_INTERNAL, "Internal player")
            installedPlayers.forEach { choice ->
                option(choice.player.name, choice.player.label)
            }
            option(PLAYER_ASK, "Ask every time")
            Text(
                text = "Holding OK on a stream always shows the full list.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Poster-density picker (§5.1, 4–8 columns): trapped-focus Dialog, initial
 * focus on the current value so OK-OK is a no-op — same pattern as
 * Discover's pickers.
 */
@Composable
private fun DensityDialog(
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(460.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = "Poster size",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    // Scroll-axis headroom for focus scale (§5.3).
                    .padding(vertical = 8.dp),
            ) {
                for (columns in MIN_POSTER_COLUMNS..MAX_POSTER_COLUMNS) {
                    val hint = when (columns) {
                        MIN_POSTER_COLUMNS -> " · biggest posters"
                        MAX_POSTER_COLUMNS -> " · most on screen"
                        else -> ""
                    }
                    Button(
                        onClick = { onPick(columns) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (columns == current) Modifier.focusRequester(selectedFocus)
                                else Modifier
                            ),
                    ) {
                        Text("$columns per row$hint" + if (columns == current) "  ✓" else "")
                    }
                }
            }
        }
    }
}
