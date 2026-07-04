package dev.openstream.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.openstream.tv.ui.theme.MutedText
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 5_000L

/**
 * Internal player (§6.1): Media3 PlayerView (SurfaceView) with a Compose
 * overlay for all controls. D-pad: any key wakes the overlay; CENTER
 * play/pauses; LEFT/RIGHT seek ±10s/±30s; overlay hides after 5s.
 */
@Composable
fun PlayerScreen(
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.hasSource) {
        if (!state.hasSource) onExit() // process death restored this route
    }

    var overlayVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var positionText by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    // Overlay auto-hide + position ticker (only while visible).
    LaunchedEffect(overlayVisible, lastInteractionMs) {
        while (overlayVisible) {
            val player = viewModel.engine.exoPlayer
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
                val player = viewModel.engine.exoPlayer
                when (event.key.nativeKeyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        wake(); true
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
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        wake(); true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.engine.exoPlayer
                    useController = false // Compose overlay owns all controls
                }
            },
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
                        "    (OK play/pause · ◀ -10s · ▶ +30s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
        }

        if (state.ended) {
            // Autoplay hooks in here in Phase 3; for now an honest end state.
            CenterPanel("Playback finished") {
                Button(onClick = onExit) { Text("Back") }
            }
        }

        state.error?.let { message ->
            // §6.1: no dead-end error states — every error offers actions.
            CenterPanel("⚠ $message") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::retry) { Text("Retry") }
                    Button(onClick = onExit) { Text("Back to streams") }
                }
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
                .background(Color(0xD0101018))
                .padding(32.dp),
        ) {
            Text(message, style = MaterialTheme.typography.titleLarge, color = Color.White)
            actions()
        }
    }
}

private fun Long.asClock(): String {
    if (this <= 0) return "0:00"
    val totalSeconds = this / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
