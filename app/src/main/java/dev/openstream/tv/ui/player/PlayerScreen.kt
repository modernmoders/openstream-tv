package dev.openstream.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.autoplay.AutoplayController.Companion.isCancellable
import dev.openstream.tv.player.applyTrackOption
import dev.openstream.tv.player.disableSubtitles
import dev.openstream.tv.player.toTrackMenu
import dev.openstream.tv.ui.components.UpNextOverlay
import dev.openstream.tv.ui.components.asClock
import dev.openstream.tv.ui.theme.MutedText
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 5_000L

/**
 * Internal player (§6.1): Media3 PlayerView (SurfaceView) with a Compose
 * overlay for all controls. D-pad: any key wakes the overlay; CENTER
 * play/pauses; LEFT/RIGHT seek ±10s/±30s; UP opens the audio & subtitle
 * picker; overlay hides after 5s.
 *
 * The engine lives in PlaybackService; this screen binds once the service
 * hands it over (a few ms — a black frame, not a spinner).
 */
@Composable
fun PlayerScreen(
    onExit: () -> Unit,
    onOpenStreams: (type: String, videoId: String, title: String, metaId: String, poster: String?) -> Unit =
        { _, _, _, _, _ -> },
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val engineOrNull by viewModel.engine.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplayState.collectAsStateWithLifecycle()

    LaunchedEffect(state.hasSource) {
        if (!state.hasSource) onExit() // process death restored this route
    }
    LaunchedEffect(Unit) {
        // §7.1 step 4: autoplay gave up → the next episode's manual stream list
        viewModel.openStreams.collect { onOpenStreams(it.type, it.videoId, it.title, it.metaId, it.poster) }
    }
    // Back cancels autoplay instead of leaving the screen while the card is up
    BackHandler(enabled = autoplay.isCancellable()) { viewModel.backPressed() }

    val engine = engineOrNull
    if (engine == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    var overlayVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var positionText by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    // Audio & subtitle menu (owner request 2026-07-05): mirrors the player's
    // track list; onTracksChanged also fires when autoplay swaps episodes.
    var tracksMenu by remember(engine) { mutableStateOf(engine.exoPlayer.currentTracks.toTrackMenu()) }
    var showTracks by remember { mutableStateOf(false) }
    // "Try another server" confirm (owner request 2026-07-06): for streams
    // that are broken in ways the player can't detect (frozen, wrong file).
    var showTryNext by remember { mutableStateOf(false) }
    DisposableEffect(engine) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksMenu = tracks.toTrackMenu()
            }
        }
        engine.exoPlayer.addListener(listener)
        onDispose { engine.exoPlayer.removeListener(listener) }
    }

    // Overlay auto-hide + position ticker (only while visible).
    LaunchedEffect(overlayVisible, lastInteractionMs) {
        while (overlayVisible) {
            val player = engine.exoPlayer
            positionText = "${player.currentPosition.asClock()} / ${player.duration.asClock()}"
            playing = player.isPlaying
            if (System.currentTimeMillis() - lastInteractionMs > OVERLAY_TIMEOUT_MS) {
                overlayVisible = false
            }
            delay(500)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun wake() {
        lastInteractionMs = System.currentTimeMillis()
        overlayVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val player = engine.exoPlayer
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        when {
                            // OK on the Up Next countdown = play now (§7.1 step 2)
                            viewModel.confirmAutoplay() -> true
                            // Finished/error panel up: its focused Button owns
                            // OK — swallowing it here made "Back" unreachable
                            state.ended || state.error != null -> false
                            else -> {
                                if (player.isPlaying) player.pause() else player.play()
                                wake(); true
                            }
                        }
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        wake(); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        player.seekTo(player.currentPosition + 30_000)
                        wake(); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        // ▲ = audio & subtitles, unless a panel/card owns the screen
                        if (autoplay == null && !state.ended && state.error == null) {
                            showTracks = true
                        }
                        wake(); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        // ▼ = try another server, when there is one to try
                        if (autoplay == null && !state.ended && state.error == null &&
                            state.canTryNext
                        ) {
                            showTryNext = true
                        }
                        wake(); true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false // Compose overlay owns all controls
                }
            },
            // update, not factory: the service engine can arrive after the
            // first composition and must still get the surface.
            update = { view -> view.player = engine.exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        if (overlayVisible && state.error == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xB0000000))
                    .padding(horizontal = 48.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(state.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    text = (if (playing) "▶  " else "⏸  ") + positionText +
                        "    (OK play/pause · ◀ -10s · ▶ +30s · ▲ audio & subtitles" +
                        (if (state.canTryNext) " · ▼ try another server" else "") + ")",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
        }

        if (state.switching) {
            // Quiet server-switch notice (elder rule: friendly, never raw errors)
            Text(
                text = "Trying another server…",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color(0xB0000000), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }

        if (showTryNext) {
            TryAnotherServerDialog(
                onConfirm = {
                    showTryNext = false
                    viewModel.tryNextStream()
                    wake()
                },
                onDismiss = { showTryNext = false; wake() },
            )
        }

        if (showTracks) {
            TracksDialog(
                menu = tracksMenu,
                onPick = {
                    engine.exoPlayer.applyTrackOption(it)
                    viewModel.rememberTrackPick(it) // DECISIONS #19
                },
                onSubtitlesOff = {
                    engine.exoPlayer.disableSubtitles()
                    viewModel.rememberSubtitlesOff()
                },
                onDismiss = { showTracks = false; wake() },
            )
        }

        // Up Next flow (§7.1) replaces the finished panel while it's working;
        // the panel stays the honest fallback for movies/series end/cancel.
        if (autoplay != null) {
            UpNextOverlay(autoplay)
        } else if (state.ended) {
            CenterPanel("Playback finished") {
                Button(onClick = onExit) { Text("Back") }
            }
        }

        state.error?.let { message ->
            // §6.1: no dead-end error states — every error offers actions.
            CenterPanel("⚠ $message") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.canTryNext) {
                        Button(onClick = { viewModel.tryNextStream() }) {
                            Text("Try another server")
                        }
                    }
                    Button(onClick = viewModel::retry) { Text("Retry") }
                    Button(onClick = onExit) { Text("Back to streams") }
                }
            }
        }
    }
}

/**
 * ▼ confirm for "Try another server": a real Dialog so D-pad focus is
 * trapped (§5.4) and an accidental ▼ press is a harmless Back/Cancel away.
 */
@Composable
private fun TryAnotherServerDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { confirmFocus.requestFocus() } }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            Text(
                text = "Not playing right?",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.focusRequester(confirmFocus),
                ) { Text("Try another server") }
                Button(onClick = onDismiss) { Text("Keep watching") }
            }
        }
    }
}

@Composable
private fun CenterPanel(message: String, actions: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .background(Color(0xD0101018), RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge, color = Color.White)
            actions()
        }
    }
}
