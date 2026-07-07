package dev.openstream.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.UpNextOverlay
import dev.openstream.tv.ui.components.asClock
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.MutedText
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L

/**
 * Internal player (§6.1): Media3 PlayerView (SurfaceView) under a Compose
 * control bar (owner UX 2026-07-06). While playing, the screen is clean; any
 * key "wakes" the controls and lands focus on the scrub bar. On the scrub bar,
 * ◀▶ rewind/fast-forward and OK play/pauses; below it sit clearly-labelled
 * buttons — Audio & subtitles, Try a different stream, Play in another app —
 * so an older viewer always has an obvious, jargon-free way out when a stream
 * won't play or plays wrong. The bar auto-hides after 5s.
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
        viewModel.openStreams.collect { onOpenStreams(it.type, it.videoId, it.title, it.metaId, it.poster) }
    }
    // Remote BACK: during the Up Next countdown, cancel it and stay; otherwise
    // leave the player the SAME way the on-screen exits do (onExit) — in easy
    // mode that pops THROUGH the stream list back to Details/episode selection
    // instead of landing on the raw stream list. Previously this only fired
    // for the countdown, so a normal back press hit the nav default (a single
    // pop onto Streams) — owner report: back showed streams, not the movie.
    BackHandler {
        if (autoplay.isCancellable()) viewModel.backPressed() else onExit()
    }

    val engine = engineOrNull
    if (engine == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    // Going Home (app backgrounded) pauses playback (owner request): a TV media
    // service deliberately keeps ExoPlayer alive in the background, but viewers
    // expect the Home button to pause, not keep playing. ON_STOP fires when the
    // app leaves the screen; in-app navigation between our own screens doesn't
    // trigger it, so this pauses only on a real background (Home/app switch).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var overlayVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }

    val outerFocus = remember { FocusRequester() }
    val scrubFocus = remember { FocusRequester() }

    var tracksMenu by remember(engine) { mutableStateOf(engine.exoPlayer.currentTracks.toTrackMenu()) }
    var showTracks by remember { mutableStateOf(false) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    DisposableEffect(engine) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksMenu = tracks.toTrackMenu()
            }
        }
        engine.exoPlayer.addListener(listener)
        onDispose { engine.exoPlayer.removeListener(listener) }
    }

    // Fire an external player with the current stream (VLC/MX). Our engine is
    // paused inside externalIntentForCurrent so the two don't both play.
    val externalResult = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* returned from the other app; our player is paused and still here */ }
    fun playInAnotherApp(choice: dev.openstream.tv.player.ExternalPlayerPort.Choice) {
        viewModel.externalIntentForCurrent(choice)?.let { runCatching { externalResult.launch(it) } }
    }
    fun onPlayInAnotherApp() {
        val players = viewModel.externalPlayers
        when {
            players.isEmpty() -> Unit
            players.size == 1 -> playInAnotherApp(players.first())
            else -> showPlayerPicker = true
        }
    }

    fun wake() {
        lastInteractionMs = System.currentTimeMillis()
        overlayVisible = true
    }

    // Position ticker + auto-hide (only while the bar is up).
    LaunchedEffect(overlayVisible, lastInteractionMs) {
        while (overlayVisible) {
            val p = engine.exoPlayer
            positionMs = p.currentPosition
            durationMs = p.duration
            playing = p.isPlaying
            if (System.currentTimeMillis() - lastInteractionMs > OVERLAY_TIMEOUT_MS) overlayVisible = false
            delay(300)
        }
    }
    // Focus follows the bar: on it when shown, back to the full-screen catcher
    // when hidden (so the next key wakes it rather than seeking blindly).
    LaunchedEffect(overlayVisible) {
        runCatching { if (overlayVisible) scrubFocus.requestFocus() else outerFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(outerFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.key.nativeKeyCode
                // Up Next countdown: OK plays now (§7.1 step 2).
                if ((code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) &&
                    viewModel.confirmAutoplay()
                ) return@onPreviewKeyEvent true
                // Panels (finished/error/Up Next) own their own buttons.
                if (state.ended || state.error != null || autoplay != null) {
                    return@onPreviewKeyEvent false
                }
                if (!overlayVisible) {
                    // Controls asleep: any key but Back wakes them and is
                    // swallowed, so the same press doesn't also seek/pause.
                    if (code == AndroidKeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                    wake()
                    return@onPreviewKeyEvent true
                }
                // Bar is up: keep it alive; let the focused control act.
                lastInteractionMs = System.currentTimeMillis()
                false
            },
    ) {
        AndroidView(
            factory = { context -> PlayerView(context).apply { useController = false } },
            update = { view -> view.player = engine.exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        if (overlayVisible && state.error == null && !state.ended && autoplay == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xC0000000))
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ScrubBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    playing = playing,
                    onSeek = { delta ->
                        val target = (engine.exoPlayer.currentPosition + delta)
                            .coerceIn(0, engine.exoPlayer.duration.coerceAtLeast(0))
                        engine.exoPlayer.seekTo(target)
                        wake()
                    },
                    onTogglePlay = {
                        if (engine.exoPlayer.isPlaying) engine.exoPlayer.pause() else engine.exoPlayer.play()
                        wake()
                    },
                    focusRequester = scrubFocus,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Episode jumps (owner round 12): shown only for a series
                    // with a neighbour in that direction, so a movie or the
                    // first/last episode never gets a dead button.
                    if (state.previousEpisode != null) {
                        SurfacePill("⏮ Previous episode", onClick = { viewModel.goToPreviousEpisode(); wake() })
                    }
                    SurfacePill("Audio & subtitles", onClick = { showTracks = true; wake() })
                    if (viewModel.externalPlayers.isNotEmpty()) {
                        SurfacePill("Play in another app", onClick = { onPlayInAnotherApp(); wake() })
                    }
                    // Always shown (owner report): walks to the next candidate
                    // if one remains, else opens the full stream list for this
                    // video — never a dead/hidden button.
                    SurfacePill("Try a different stream", onClick = { viewModel.tryAnotherStream(); wake() })
                    if (state.nextEpisode != null) {
                        SurfacePill("Next episode ⏭", onClick = { viewModel.goToNextEpisode(); wake() })
                    }
                }
            }
        }

        if (state.switching) {
            Text(
                text = "Trying another stream…",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
                    .background(Color(0xB0000000), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            )
        }

        if (showTracks) {
            TracksDialog(
                menu = tracksMenu,
                onPick = {
                    engine.exoPlayer.applyTrackOption(it)
                    viewModel.rememberTrackPick(it)
                },
                onSubtitlesOff = {
                    engine.exoPlayer.disableSubtitles()
                    viewModel.rememberSubtitlesOff()
                },
                onDismiss = { showTracks = false; wake() },
            )
        }

        if (showPlayerPicker) {
            AnotherAppDialog(
                players = viewModel.externalPlayers,
                onPick = { showPlayerPicker = false; playInAnotherApp(it) },
                onDismiss = { showPlayerPicker = false; wake() },
            )
        }

        if (autoplay != null) {
            UpNextOverlay(autoplay)
        } else if (state.ended) {
            CenterPanel("All done") {
                Button(onClick = onExit) { Text("Back") }
            }
        }

        state.error?.let { message ->
            // §6.1: no dead-end errors — always an obvious, plain-word way out.
            // "Try a different stream" is ALWAYS present (owner report: hiding
            // it when the ranked cascade was exhausted left DOWN landing on
            // the unrelated "Play in another app" instead) and holds initial
            // focus — deterministic, not a 2D nearest-neighbor guess. Placed
            // last before Back (owner: wants it rightmost for visibility).
            val tryAnotherFocus = remember { FocusRequester() }
            LaunchedEffect(message) { runCatching { tryAnotherFocus.requestFocus() } }
            CenterPanel("Hmm, that one won't play.\n$message") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (viewModel.externalPlayers.isNotEmpty()) {
                        Button(onClick = ::onPlayInAnotherApp) { Text("Play in another app") }
                    }
                    Button(onClick = viewModel::retry) { Text("Try again") }
                    Button(
                        onClick = { viewModel.tryAnotherStream() },
                        modifier = Modifier.focusRequester(tryAnotherFocus),
                    ) { Text("Try a different stream") }
                    Button(onClick = onExit) { Text("Back") }
                }
            }
        }
    }
}

/**
 * The scrub bar: a focusable progress track with play state + times. On focus,
 * ◀▶ seek ±10s and OK play/pauses; an accent ring shows it holds the cursor.
 */
@Composable
private fun ScrubBar(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        onSeek(-SEEK_STEP_MS); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        onSeek(SEEK_STEP_MS); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        onTogglePlay(); true
                    }
                    else -> false
                }
            },
    ) {
        Text(if (playing) "⏸" else "▶", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(positionMs.asClock(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = if (focused) 0.30f else 0.18f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Accent),
            )
        }
        Text(durationMs.asClock(), style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}

/** Pick which other app to hand the stream to, when more than one is installed. */
@Composable
private fun AnotherAppDialog(
    players: List<dev.openstream.tv.player.ExternalPlayerPort.Choice>,
    onPick: (dev.openstream.tv.player.ExternalPlayerPort.Choice) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text(
                text = "Play in another app",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            players.forEachIndexed { index, choice ->
                SurfacePill(
                    label = choice.player.label,
                    onClick = { onPick(choice) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
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
