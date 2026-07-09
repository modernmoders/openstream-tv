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
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import dev.openstream.tv.autoplay.StreamCascade
import androidx.compose.foundation.shape.RoundedCornerShape
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
import dev.openstream.tv.player.PlayerDecision
import dev.openstream.tv.player.resolvePreferredPlayer
import dev.openstream.tv.ui.components.BackButton
import dev.openstream.tv.ui.components.LoadingMessage
import dev.openstream.tv.ui.components.RowMessage
import dev.openstream.tv.ui.components.SurfaceRow
import dev.openstream.tv.ui.components.UpNextOverlay
import dev.openstream.tv.ui.components.asClock
import dev.openstream.tv.ui.streams.StreamListViewModel.GroupState
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.AppBackground
import dev.openstream.tv.ui.theme.MutedText

/**
 * Stream list: groups by addon in user order, streams in addon order —
 * NEVER re-sorted (§4.1.7). Non-URL sources (torrents, external links) are
 * visible but not playable in v1 (§4.1.4).
 *
 * OK obeys the §6.2 "Always use" player setting (internal by default);
 * LONG-press OK always opens "Play with…" for a one-off override. This
 * screen also hosts the §7.1.6 Up Next flow after an external player
 * returns with a (near-)finished position.
 */
@Composable
fun StreamListScreen(
    onBack: () -> Unit = {},
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

    /**
     * Player decided. The INTERNAL player now asks "resume / start over" itself,
     * over its loading animation (owner 2026-07-08), so we just launch it at the
     * saved position (resume by default) and let it prompt. An EXTERNAL player
     * (VLC/MX) can't host our prompt, so it still gets the pre-launch dialog.
     */
    fun onPlayerDecided(play: PendingPlay) {
        choosingPlayer = false
        val resume = state.resumePositionMs
        when {
            play.external == null -> {
                pendingPlay = null
                launch(play, resume ?: 0)
            }
            resume != null -> pendingPlay = play // external: ask before leaving the app
            else -> {
                pendingPlay = null
                launch(play, 0)
            }
        }
    }

    // Auto-play first stream: launch hands-free, resuming automatically.
    // "Ask every time" can't ask silently — auto-start uses the internal
    // player then; a real "Always use VLC/MX" preference is honored.
    LaunchedEffect(Unit) {
        viewModel.autoStart.collect { auto ->
            val s = viewModel.uiState.value
            val external = (resolvePreferredPlayer(s.playerPref, s.externalPlayers)
                as? PlayerDecision.External)?.choice
            launch(PendingPlay(auto.addon, auto.stream, external), auto.startPositionMs)
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

    // The Back button is the first focusable, so anchor entry focus on the
    // first playable stream once one arrives (BackButton KDoc). runCatching:
    // the row may not be composed yet if the user scrolled away — fall back
    // to default focus rather than crash.
    val firstStreamFocus = remember { FocusRequester() }
    // Interweave every addon's streams into one ranked, de-duplicated list
    // (owner 2026-07-09) instead of per-addon blocks. The dedupe collapses the
    // same release returned by all three AIOStreams instances.
    val loadedGroups = state.groups.filterIsInstance<GroupState.Loaded>()
    val mergedStreams = remember(state.groups) {
        StreamCascade.mergeForDisplay(
            loadedGroups.mapIndexed { i, g ->
                StreamCascade.AddonStreams(g.addon.manifestUrl, i, g.streams)
            },
            viewModel.hardwareCodecs,
        )
    }
    val addonByUrl = remember(state.groups) {
        loadedGroups.associate { it.addon.manifestUrl to it.addon }
    }
    LaunchedEffect(mergedStreams.isNotEmpty()) {
        if (mergedStreams.isNotEmpty()) runCatching { firstStreamFocus.requestFocus() }
    }

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            BackButton(onBack)
            Text(
                text = viewModel.title.ifBlank { "Streams" },
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.autoStarting) {
            // Auto mode: a calm "Starting…" fills the screen so the technical
            // stream list never flashes by (owner 2026-07-06). If auto-play
            // gives up, autoStarting flips false and the real list appears.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingMessage(
                    text = "Starting your show…",
                    horizontalPadding = 0.dp,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        } else {
        if (!state.initializing && state.groups.isEmpty()) {
            RowMessage(
                "No installed addon provides streams for this item — " +
                    "add a stream addon (e.g. an AIOStreams instance)",
                horizontalPadding = 0.dp,
            )
        }

        // Leading stream numbers so a viewer can tell when auto-retry has looped
        // back to a stream already tried; they fade out ~5s after the list shows
        // so they don't clutter the row text (owner 2026-07-09).
        var showNumbers by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(5_000); showNumbers = false }
        val numberAlpha by animateFloatAsState(
            targetValue = if (showNumbers) 1f else 0f,
            animationSpec = tween(700),
            label = "stream-numbers",
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // Scroll-axis headroom so the first/last stream row's focus
            // scale isn't clipped (§5.3).
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            // Some sources still answering — tell the viewer more may arrive.
            if (state.groups.any { it is GroupState.Loading }) {
                item(key = "loading-note") {
                    LoadingMessage(text = "Finding more streams…", horizontalPadding = 0.dp)
                }
            }
            // ONE interwoven, ranked, de-duplicated list across every addon.
            itemsIndexed(
                mergedStreams,
                key = { _, c -> "m-${c.addonUrl}-${c.serverIndex}" },
            ) { index, candidate ->
                val addon = addonByUrl[candidate.addonUrl]
                if (addon != null) {
                    val stream = candidate.stream
                    StreamRow(
                        stream = stream,
                        number = index + 1,
                        numberAlpha = { numberAlpha },
                        modifier = if (index == 0) Modifier.focusRequester(firstStreamFocus) else Modifier,
                        onClick = {
                            // §6.2 "Always use" setting decides what OK means;
                            // uninstalled/unknown falls back to internal.
                            when (val decision = resolvePreferredPlayer(
                                state.playerPref, state.externalPlayers,
                            )) {
                                PlayerDecision.Internal -> onPlayerDecided(
                                    PendingPlay(addon, stream, external = null)
                                )
                                PlayerDecision.Ask -> {
                                    pendingPlay = PendingPlay(addon, stream, external = null)
                                    choosingPlayer = true
                                }
                                is PlayerDecision.External -> onPlayerDecided(
                                    PendingPlay(addon, stream, decision.choice)
                                )
                            }
                        },
                        onLongClick = {
                            if (state.externalPlayers.isNotEmpty()) {
                                pendingPlay = PendingPlay(addon, stream, external = null)
                                choosingPlayer = true
                            }
                        },
                    )
                }
            }
            // Nothing playable and nothing left loading.
            if (mergedStreams.isEmpty() && state.groups.none { it is GroupState.Loading }) {
                item(key = "none") {
                    RowMessage("No playable streams for this item", horizontalPadding = 0.dp)
                }
            }
            // Failed sources, compact, at the bottom (never blocks the list).
            val failedCount = state.groups.count { it is GroupState.Failed }
            if (failedCount > 0) {
                item(key = "failed-note") {
                    RowMessage("⚠ $failedCount source(s) unavailable", horizontalPadding = 0.dp)
                }
            }
        }
        } // end: auto-starting spinner vs. the real stream list
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

    // This dialog opens from a LONG-press, so the user is usually still
    // holding OK when it appears — the held key's repeats and release land
    // here and instantly "click" the first option (owner bug 2026-07-05:
    // "hold too long and it selects the first option"). Ignore selection
    // keys until a fresh press (repeatCount 0 key-down) happens inside.
    var freshPress by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp)
                .onPreviewKeyEvent { event ->
                    val select = when (event.key.nativeKeyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> true
                        else -> false
                    }
                    when {
                        !select -> false
                        event.type == KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount == 0) freshPress = true
                            !freshPress // consume leftover long-press repeats
                        }
                        event.type == KeyEventType.KeyUp -> !freshPress // consume the stale release
                        else -> false
                    }
                },
        ) {
            Text(
                text = "Play with",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            // Full-width buttons: ragged wrap-content widths read as broken
            // on a 10-foot UI (§5 visual polish)
            Button(
                onClick = { onPick(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFocus),
            ) { Text("Internal player") }
            externalPlayers.forEach { choice ->
                Button(
                    onClick = { onPick(choice) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(choice.player.label) }
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
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
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
private fun StreamRow(
    stream: Stream,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int = 0,
    numberAlpha: () -> Float = { 0f },
) {
    if (stream.isPlayableInV1) {
        SurfaceRow(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
        ) {
            if (number > 0) {
                // Fades out via a draw-phase alpha read (no recomposition); keeps
                // a small fixed gutter so the row text never reflows as it fades.
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.titleMedium,
                    color = Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(28.dp)
                        .graphicsLayer { alpha = numberAlpha() },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stream.name ?: "Stream",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stream.displayDescription.isNotBlank()) {
                    Text(
                        text = stream.displayDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
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
