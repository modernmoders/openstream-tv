package dev.openstream.tv.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.openstream.tv.BuildConfig
import dev.openstream.tv.data.EpisodeNumbering
import dev.openstream.tv.data.MAX_POSTER_COLUMNS
import dev.openstream.tv.data.MIN_POSTER_COLUMNS
import dev.openstream.tv.data.PLAYER_ASK
import dev.openstream.tv.data.PLAYER_INTERNAL
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.ui.update.UpdateViewModel
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AmbientSection
import dev.openstream.tv.ui.theme.ambientBackground
import dev.openstream.tv.ui.theme.Hairline
import dev.openstream.tv.ui.theme.MutedText
import dev.openstream.tv.ui.theme.SurfaceCard
import dev.openstream.tv.ui.theme.SurfaceCardFocused

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
    onAddons: () -> Unit,
    onAppLog: () -> Unit,
    onReset: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val updateUi by updateViewModel.ui.collectAsStateWithLifecycle()
    val columns by viewModel.posterColumns.collectAsStateWithLifecycle()
    val playerPref by viewModel.playerPref.collectAsStateWithLifecycle()
    val autoPlay by viewModel.autoPlayFirstStream.collectAsStateWithLifecycle()
    val softwareDecoder by viewModel.preferSoftwareDecoder.collectAsStateWithLifecycle()
    val skipIntros by viewModel.skipIntrosEnabled.collectAsStateWithLifecycle()
    val autoSkipIntros by viewModel.autoSkipIntros.collectAsStateWithLifecycle()
    val autoSkipCredits by viewModel.autoSkipCredits.collectAsStateWithLifecycle()
    val numbering by viewModel.episodeNumbering.collectAsStateWithLifecycle()
    val sounds by viewModel.uiSounds.collectAsStateWithLifecycle()
    val voiceFirst by viewModel.voiceFirstSearch.collectAsStateWithLifecycle()
    val discoverHideWatched by viewModel.discoverHideWatched.collectAsStateWithLifecycle()
    val expert by viewModel.expertMode.collectAsStateWithLifecycle()
    val profileName by viewModel.profileName.collectAsStateWithLifecycle()
    var pickingDensity by remember { mutableStateOf(false) }
    var pickingPlayer by remember { mutableStateOf(false) }
    var pickingNumbering by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var confirmingSettingsReset by remember { mutableStateOf(false) }

    // Predictable entry point: land on the first setting, not Back.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .ambientBackground(AmbientSection.SETTINGS)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                // Who this box is signed in as, and which build it runs — the
                // owner could previously only see the name during setup, and the
                // version only over adb (owner 2026-07-10).
                Text(
                    text = "${profileName.ifBlank { "Not connected" }} · " +
                        "${viewModel.brand} ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
            }
        }

        Spacer(Modifier.padding(top = 24.dp))

        // The list outgrew one screen when Connect/Expert arrived — scroll,
        // with vertical headroom so the focus scale isn't clipped (§5.3).
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            // Round-15 #10 layout: what you SEE first, then sound, search,
            // anime, playback — and "this TV" housekeeping at the bottom.
            // Sections are flat captions, not expanders: everything is one
            // glance + one scroll, nothing to unfold.
            SectionCaption("HOW THINGS LOOK — Home and Discover also have a View button right on them")
            SettingEntry(
                title = "Home screen rows",
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
                title = "Hide watched shows in Discover",
                description = if (discoverHideWatched) {
                    "On — shows you've finished are hidden while browsing Discover"
                } else {
                    "Off — finished shows stay visible while browsing Discover"
                },
                onClick = { viewModel.setDiscoverHideWatched(!discoverHideWatched) },
            )
            SectionCaption("SOUND")
            SettingEntry(
                title = "Menu sounds",
                description = if (sounds) {
                    "On — soft ticks while moving around the menus"
                } else {
                    "Off — the menus stay silent"
                },
                onClick = { viewModel.setUiSounds(!sounds) },
            )
            SectionCaption("SEARCH")
            SettingEntry(
                title = "Search by talking",
                description = if (voiceFirst) {
                    "On — opening Search starts the microphone right away: just " +
                        "say the show's name. You can still type instead"
                } else {
                    "Off — opening Search shows the keyboard; the microphone " +
                        "button next to the box still works"
                },
                onClick = { viewModel.setVoiceFirstSearch(!voiceFirst) },
            )
            // --- Anime (owner Round-15 #7): the skip features only ever fire
            // on anime episodes the community has timed, so they live under
            // their own plainly-labelled group. Every profile sees it — even
            // "no-anime" accounts still surface anime titles, and skipping
            // should just work if they wander into one.
            SectionCaption("ANIME — these only affect anime episodes")
            SettingEntry(
                title = "Skip buttons on anime",
                description = if (skipIntros) {
                    "On — during an anime's opening, a “Skip Intro” button pops " +
                        "up; during the ending it's “Next Episode”. One OK press. " +
                        "Nothing appears on regular shows or movies"
                } else {
                    "Off — anime openings and endings play in full, no buttons"
                },
                onClick = { viewModel.setSkipIntrosEnabled(!skipIntros) },
            )
            if (skipIntros) {
                SettingEntry(
                    title = "Skip intros by themselves",
                    description = if (autoSkipIntros) {
                        "On — the opening skips itself the moment it starts; " +
                            "you never press anything"
                    } else {
                        "Off — the “Skip Intro” button waits for you to press OK"
                    },
                    onClick = { viewModel.setAutoSkipIntros(!autoSkipIntros) },
                )
                SettingEntry(
                    title = "Play the next episode by itself",
                    description = if (autoSkipCredits) {
                        "On — when the ending song starts, a 5-second countdown " +
                            "appears, then the next episode plays. Press BACK " +
                            "during the countdown to keep watching"
                    } else {
                        "Off — the “Next Episode” button waits for you to press OK"
                    },
                    onClick = { viewModel.setAutoSkipCredits(!autoSkipCredits) },
                )
            }
            SettingEntry(
                title = "Episode numbers",
                description = episodeNumberingLabel(numbering) +
                    " — mostly matters for long anime, where seasons and " +
                    "one big count disagree",
                onClick = { pickingNumbering = true },
            )
            SectionCaption("PLAYBACK")
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
            SectionCaption("THIS TV")
            // Self-update (owner 2026-07-11): boxes that leave the house
            // update themselves. The row is its own tiny state machine —
            // check on demand, then flip into an install button.
            SettingEntry(
                title = "App update",
                description = when (val u = updateUi) {
                    is UpdateViewModel.UpdateUi.Available ->
                        "Version ${u.versionName} is ready — press OK to update"
                    UpdateViewModel.UpdateUi.Checking -> "Checking…"
                    UpdateViewModel.UpdateUi.Downloading -> "Getting the update…"
                    UpdateViewModel.UpdateUi.InstallFailed ->
                        "The update didn't finish — press OK to try again"
                    UpdateViewModel.UpdateUi.UpToDate ->
                        "Up to date — this TV runs the newest version " +
                            "(${BuildConfig.VERSION_NAME})"
                    UpdateViewModel.UpdateUi.CheckFailed ->
                        "Couldn't check right now — press OK to try again"
                    UpdateViewModel.UpdateUi.Hidden ->
                        "You're on ${BuildConfig.VERSION_NAME} — press OK to " +
                            "check for a newer version"
                },
                onClick = {
                    when (updateUi) {
                        is UpdateViewModel.UpdateUi.Available,
                        UpdateViewModel.UpdateUi.InstallFailed,
                        -> updateViewModel.install()
                        UpdateViewModel.UpdateUi.Checking,
                        UpdateViewModel.UpdateUi.Downloading,
                        -> Unit // in flight — OK does nothing
                        else -> updateViewModel.manualCheck()
                    }
                },
            )
            // Round-15 #10: an everyday-safe "undo my fiddling" — resets the
            // knobs above WITHOUT touching who this box belongs to. Sits just
            // before Expert on purpose: it's the last non-technical thing.
            SettingEntry(
                title = "Reset settings to default",
                description = "Puts every setting on this screen back how it " +
                    "started. Keeps your name, your shows, and where you left off",
                onClick = { confirmingSettingsReset = true },
            )
            // Deliberately LAST (owner directive 2026-07-06): technical tools
            // stay out of sight unless whoever looks after the box opts in.
            SettingEntry(
                title = "Expert mode",
                description = if (expert) {
                    "On — technical tools are shown (like the addon manager below)"
                } else {
                    "Off — keeps things simple. Only for whoever looks after this TV"
                },
                onClick = { viewModel.setExpertMode(!expert) },
            )
            if (expert) {
                // Round 14/15 (owner): decoder + the external-player pick live
                // in here — a viewer has no business flipping decoders or
                // players; the in-player "Fix blocky video" / long-press
                // cover the everyday cases. "Connect this TV" is GONE
                // (Round-15 #10): "Reset this TV" below reaches the same
                // name-setup screen without a confusing twin entry.
                SettingEntry(
                    title = "Player",
                    description = "Pressing OK on a stream uses: " +
                        playerPrefLabel(playerPref, viewModel.installedPlayers),
                    onClick = { pickingPlayer = true },
                )
                SettingEntry(
                    title = "Prefer software video decoder",
                    description = if (softwareDecoder) {
                        "On — plays video in software. Fixes blocky or glitchy " +
                            "picture on some shows (like anime). Turn off if video stutters"
                    } else {
                        "Off — uses the TV's built-in video chip (fastest). Turn this " +
                            "on if the picture looks blocky or scrambled on some shows"
                    },
                    onClick = { viewModel.setPreferSoftwareDecoder(!softwareDecoder) },
                )
                SettingEntry(
                    title = "Addons",
                    description = "Add, remove, or reorder the services this TV gets its shows from",
                    onClick = onAddons,
                )
                SettingEntry(
                    title = "App log",
                    description = "What went wrong lately — viewers never see errors, they land here",
                    onClick = onAppLog,
                )
                // Destructive + rare: last, and gated behind a confirm dialog
                // (owner request: a way back to "What's your name?" with no
                // adb — e.g. handing the box to a different person).
                SettingEntry(
                    title = "Reset this TV",
                    description = "Forgets every addon and goes back to the name-setup screen",
                    onClick = { confirmingReset = true },
                )
            }
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
    if (pickingNumbering) {
        EpisodeNumberingDialog(
            current = numbering,
            onPick = { picked ->
                viewModel.setEpisodeNumbering(picked)
                pickingNumbering = false
            },
            onDismiss = { pickingNumbering = false },
        )
    }
    if (confirmingReset) {
        ResetTvDialog(
            onConfirm = {
                confirmingReset = false
                viewModel.resetTv(onDone = onReset)
            },
            onDismiss = { confirmingReset = false },
        )
    }
    if (confirmingSettingsReset) {
        ResetSettingsDialog(
            onConfirm = {
                viewModel.resetSettingsToDefaults { confirmingSettingsReset = false }
            },
            onDismiss = { confirmingSettingsReset = false },
        )
    }
}

/**
 * Confirm for "Reset settings to default" (Round-15 #10). Reassurance is the
 * whole point of the wording: nothing about WHO the box is gets touched.
 */
@Composable
private fun ResetSettingsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .width(460.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = "Reset settings to default?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = "Every setting goes back how it started — poster size, " +
                    "sounds, anime skipping, all of it. You stay signed in, and " +
                    "your shows and watch history are untouched.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocus),
                ) { Text("Cancel") }
                Button(onClick = onConfirm) { Text("Reset settings") }
            }
        }
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

/** Plain-words description of the episode-numbering choice (owner: anime). */
private fun episodeNumberingLabel(mode: EpisodeNumbering): String =
    when (mode) {
        EpisodeNumbering.SEASONAL ->
            "By season — e.g. Season 3, Episode 32"
        EpisodeNumbering.ABSOLUTE ->
            "Straight through — e.g. Episode 115 (counts every episode; good for anime)"
    }

/**
 * One refined settings row (2026-07-06 owner UX pass): a quiet surface with a
 * hairline border that lifts to a calm accent tint + accent border on focus,
 * with TV Material's built-in (non-stuttering) scale. No white invert — the
 * description stays readable in both states. Trailing chevron cues "opens".
 */
@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MutedText,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp),
    )
}

@Composable
private fun SettingEntry(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceCard,
            focusedContainerColor = SurfaceCardFocused,
            pressedContainerColor = SurfaceCardFocused,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MutedText)
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MutedText,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/** A dialog picker option in the same refined language (selected = accent). */
@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) SurfaceCardFocused else SurfaceCard,
            focusedContainerColor = SurfaceCardFocused,
            pressedContainerColor = SurfaceCardFocused,
            contentColor = Color.White,
            focusedContentColor = Color.White,
            pressedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) Accent else Hairline), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Accent), shape = shape),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("✓", style = MaterialTheme.typography.titleMedium, color = Accent)
            }
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
        PickerRow(
            label = label,
            selected = value == current,
            onClick = { onPick(value) },
            modifier = if (value == current) Modifier.focusRequester(selectedFocus) else Modifier,
        )
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
                    PickerRow(
                        label = "$columns per row$hint",
                        selected = columns == current,
                        onClick = { onPick(columns) },
                        modifier = if (columns == current) Modifier.focusRequester(selectedFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * Episode-numbering picker (owner request: anime). Two plain-words choices —
 * per-season vs the straight-through absolute count — in the same trapped-focus
 * dialog language as the other pickers, initial focus on the current value.
 */
@Composable
private fun EpisodeNumberingDialog(
    current: EpisodeNumbering,
    onPick: (EpisodeNumbering) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    @Composable
    fun option(value: EpisodeNumbering, label: String) {
        PickerRow(
            label = label,
            selected = value == current,
            onClick = { onPick(value) },
            modifier = if (value == current) Modifier.focusRequester(selectedFocus) else Modifier,
        )
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
                text = "Episode numbering",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            option(EpisodeNumbering.SEASONAL, "By season  ·  Season 3, Episode 32")
            option(EpisodeNumbering.ABSOLUTE, "Straight through  ·  Episode 115")
            Text(
                text = "Absolute counts every episode from the start — handy for " +
                    "long anime that some services number that way.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * "Reset this TV" confirmation (owner request: an in-app way back to the
 * name-setup screen, e.g. handing the box to someone else — no adb needed).
 * Destructive, so it defaults focus to Cancel, not the reset action, and the
 * copy spells out exactly what happens rather than relying on a color cue —
 * this codebase has no dedicated "danger" color token, and one dialog isn't
 * reason enough to invent one.
 */
@Composable
private fun ResetTvDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .width(460.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = "Reset this TV?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = "This box forgets every addon and the saved setup link, " +
                    "then shows the \"What's your name?\" screen again — like it " +
                    "just came out of the box.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocus),
                ) { Text("Cancel") }
                Button(onClick = onConfirm) { Text("Reset this TV") }
            }
        }
    }
}
