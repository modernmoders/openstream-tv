package dev.openstream.tv.ui.streams

import android.content.ActivityNotFoundException
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.autoplay.AutoplayController.Companion.isCancellable
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.UpNextOverlay
import dev.openstream.tv.ui.components.asClock
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Stream list: groups by addon in user order, streams in addon order —
 * NEVER re-sorted (§4.1.7). Non-URL sources (torrents, external links) are
 * visible but not playable in v1 (§4.1.4).
 *
 * OK plays with the internal player; LONG-press OK opens "Play with…"
 * (§6.2 external players; the full always-use setting is Phase 4). This
 * screen also hosts the §7.1.6 Up Next flow after an external player
 * returns with a (near-)finished position.
 */
@Composable
fun StreamListScreen(
    onPlay: () -> Unit = {},
    onOpenStreams: (type: String, videoId: String, title: String, metaId: String, poster: String?) -> Unit =
        { _, _, _, _, _ -> },
    viewModel: StreamListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplayState.collectAsStateWithLifecycle()

    /** Stream chosen but not yet launched: resume/start-over and/or player choice pending. */
    var pendingPlay by remember { mutableStateOf<PendingPlay?>(null) }
    /** True while [pendingPlay] shows the "Play with…" list, false = resume dialog. */
    var choosingPlayer by remember { mutableStateOf(false) }

    val externalResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onExternalResult(result.resultCode, result.data) }

    fun fireExternal(intent: android.content.Intent) {
        try {
            externalResult.launch(intent)
            viewModel.onExternalLaunched()
        } catch (_: ActivityNotFoundException) {
            // Race: player uninstalled after detection. During autoplay this
            // is the §7.1 step 5 fallthrough; manually it's a silent no-op —
            // reopening the dialog re-runs detection without the ghost entry.
            viewModel.onExternalLaunchFailed()
        }
    }

    fun launch(play: PendingPlay, startPositionMs: Long) {
        val external = play.external
        if (external == null) {
            if (viewModel.stage(play.addon, play.stream, startPositionMs)) onPlay()
        } else {
            viewModel.externalIntent(play.addon, play.stream, external, startPositionMs)
                ?.let(::fireExternal)
        }
    }

    /** Player decided; ask about resume only when there is progress to resume. */
    fun onPlayerDecided(play: PendingPlay) {
        choosingPlayer = false
        if (state.resumePositionMs != null) {
            pendingPlay = play // keep dialog chain going: resume question next
        } else {
            pendingPlay = null
            launch(play, 0)
        }
    }

    // §7.1.6 chain: the next episode plays in the same external player
    LaunchedEffect(Unit) { viewModel.launchExternal.collect(::fireExternal) }
    LaunchedEffect(Unit) {
        // Autoplay gave up → next episode's manual stream list replaces this one
        viewModel.openStreams.collect { onOpenStreams(it.type, it.videoId, it.title, it.metaId, it.poster) }
    }
    // Back cancels the Up Next flow instead of leaving the screen (§7.1 step 4a)
    BackHandler(enabled = autoplay.isCancellable()) { viewModel.backPressed() }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // While the Up Next card is up, OK confirms the countdown and
                // must never click through to a stream row underneath.
                if (autoplay == null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                        viewModel.confirmAutoplay(); true
                    }
                    else -> false
                }
            },
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = viewModel.title.ifBlank { "Streams" },
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (!state.initializing && state.groups.isEmpty()) {
            RowMessage(
                "No installed addon provides streams for this item — " +
                    "add a stream addon (e.g. an AIOStreams instance)",
                horizontalPadding = 0.dp,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.groups.forEach { group ->
                item(key = "header-${group.addon.manifestUrl}") {
                    Text(
                        text = group.addon.manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                when (group) {
                    is GroupState.Loading -> item(key = "loading-${group.addon.manifestUrl}") {
                        RowMessage("Loading…", horizontalPadding = 0.dp)
                    }
                    is GroupState.Failed -> item(key = "failed-${group.addon.manifestUrl}") {
                        RowMessage("⚠ ${group.message}", horizontalPadding = 0.dp)
                    }
                    is GroupState.Loaded ->
                        if (group.streams.isEmpty()) {
                            item(key = "empty-${group.addon.manifestUrl}") {
                                RowMessage("No streams", horizontalPadding = 0.dp)
                            }
                        } else {
                            // Index in key: addons may return near-identical rows
                            group.streams.forEachIndexed { index, stream ->
                                item(key = "s-${group.addon.manifestUrl}-$index") {
                                    StreamRow(
                                        stream = stream,
                                        onClick = {
                                            onPlayerDecided(PendingPlay(group.addon, stream, external = null))
                                        },
                                        onLongClick = {
                                            if (state.externalPlayers.isNotEmpty()) {
                                                pendingPlay = PendingPlay(group.addon, stream, external = null)
                                                choosingPlayer = true
                                            }
                                        },
                                    )
                                }
                            }
                        }
                }
            }
        }
    }

    pendingPlay?.let { play ->
        if (choosingPlayer) {
            PlayWithDialog(
                externalPlayers = state.externalPlayers,
                onPick = { choice -> onPlayerDecided(play.copy(external = choice)) },
                onDismiss = { pendingPlay = null; choosingPlayer = false },
            )
        } else {
            ResumeDialog(
                resumePositionMs = state.resumePositionMs ?: 0,
                onResume = {
                    pendingPlay = null
                    launch(play, state.resumePositionMs ?: 0)
                },
                onStartOver = {
                    pendingPlay = null
                    launch(play, 0)
                },
                onDismiss = { pendingPlay = null },
            )
        }
    }

    UpNextOverlay(autoplay)
    }
}

/** A chosen stream on its way to a player. external == null → internal ExoPlayer. */
private data class PendingPlay(
    val addon: InstalledAddon,
    val stream: Stream,
    val external: ExternalPlayerPort.Choice?,
)

/**
 * "Play with…" (§6.2): internal player first (the default everywhere else),
 * then only the players that are actually installed. Same Dialog rationale
 * as ResumeDialog — D-pad focus trapped, Back dismisses.
 */
@Composable
private fun PlayWithDialog(
    externalPlayers: List<ExternalPlayerPort.Choice>,
    onPick: (ExternalPlayerPort.Choice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .background(Color(0xF0181822))
                .padding(32.dp),
        ) {
            Text(
                text = "Play with",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Button(
                onClick = { onPick(null) },
                modifier = Modifier.focusRequester(firstFocus),
            ) { Text("Internal player") }
            externalPlayers.forEach { choice ->
                Button(onClick = { onPick(choice) }) { Text(choice.player.label) }
            }
        }
    }
}

/**
 * "Resume from X / Start over" (§10 Phase 2). A real Dialog window so D-pad
 * focus is trapped inside and Back dismisses — the list behind must not be
 * reachable while it's up (§5.4 focus predictability).
 */
@Composable
private fun ResumeDialog(
    resumePositionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { resumeFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .background(Color(0xF0181822))
                .padding(32.dp),
        ) {
            Text(
                text = "You've watched part of this",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.focusRequester(resumeFocus),
                ) { Text("Resume from ${resumePositionMs.asClock()}") }
                Button(onClick = onStartOver) { Text("Start over") }
            }
        }
    }
}

@Composable
private fun StreamRow(stream: Stream, onClick: () -> Unit, onLongClick: () -> Unit) {
    if (stream.isPlayableInV1) {
        Button(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = stream.name ?: "Stream",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stream.displayDescription.isNotBlank()) {
                    Text(
                        text = stream.displayDescription,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        // §4.1.4: parsed and visible, but not playable in v1 — an honest note
        // instead of a dead button (no focusable dead-ends, §5.4).
        Text(
            text = "◇ ${stream.name ?: "Stream"} — unsupported source in v1 " +
                (stream.infoHash?.let { "(torrent)" } ?: "(external)"),
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}
