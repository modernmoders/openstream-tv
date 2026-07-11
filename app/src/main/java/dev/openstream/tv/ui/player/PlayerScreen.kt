package dev.openstream.tv.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import dev.openstream.tv.ui.components.LoadingAnimation
import dev.openstream.tv.ui.components.SurfacePill
import dev.openstream.tv.ui.components.UpNextOverlay
import dev.openstream.tv.ui.components.asClock
import dev.openstream.tv.ui.theme.Accent
import dev.openstream.tv.ui.theme.MutedText
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 5_000L

/** How long a mid-playback rebuffer must persist before the small ring shows —
 *  keeps ordinary sub-frame stalls invisible (no spinner flashes, owner
 *  2026-07-09). */
private const val REBUFFER_RING_DELAY_MS = 400L

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
    // Loading/test phase driver: the looping spinner shows while the stream
    // buffers (and while the debrid services serve their "resolving" clips),
    // clearing when the player reaches READY. Seeded from the CURRENT state, not
    // a fixed default: the ViewModel calls play() before this listener attaches,
    // so a stream that opened fast would otherwise never leave the spinner.
    var playbackState by remember(engine) { mutableIntStateOf(engine.exoPlayer.playbackState) }

    val outerFocus = remember { FocusRequester() }
    val scrubFocus = remember { FocusRequester() }
    // DOWN from the scrub bar lands here — "Try a different stream" is the
    // failure escape people reach for, so it owns the middle default (owner
    // 2026-07-08: DOWN used to land on "Play in another app").
    val tryStreamFocus = remember { FocusRequester() }

    var tracksMenu by remember(engine) { mutableStateOf(engine.exoPlayer.currentTracks.toTrackMenu()) }
    var showTracks by remember { mutableStateOf(false) }
    var showPlayerPicker by remember { mutableStateOf(false) }
    var showLearnMore by remember { mutableStateOf(false) }
    DisposableEffect(engine) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksMenu = tracks.toTrackMenu()
            }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
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

    // Position ticker + auto-hide (only while the bar is up). Paused video
    // keeps its bar: hiding the controls on a paused frame left no clue the
    // video was paused at all — auto-hide is for WATCHING, not for pausing.
    LaunchedEffect(overlayVisible, lastInteractionMs) {
        while (overlayVisible) {
            val p = engine.exoPlayer
            positionMs = p.currentPosition
            durationMs = p.duration
            playing = p.isPlaying
            if (playing && System.currentTimeMillis() - lastInteractionMs > OVERLAY_TIMEOUT_MS) {
                overlayVisible = false
            }
            delay(300)
        }
    }

    // --- d-pad scrubbing (Scrubbing.kt): move a preview target instantly,
    // commit ONE real seek after a quiet period. ---
    var scrubTargetMs by remember(engine) { mutableStateOf<Long?>(null) }
    var scrubStreak by remember { mutableIntStateOf(0) }
    var lastScrubPressMs by remember { mutableLongStateOf(0L) }
    fun scrubBy(direction: Int) {
        val now = System.currentTimeMillis()
        scrubStreak = if (now - lastScrubPressMs < Scrubbing.STREAK_WINDOW_MS) scrubStreak + 1 else 1
        lastScrubPressMs = now
        scrubTargetMs = Scrubbing.nextTarget(
            currentTargetMs = scrubTargetMs,
            positionMs = engine.exoPlayer.currentPosition,
            durationMs = engine.exoPlayer.duration,
            direction = direction,
            streak = scrubStreak,
        )
        wake()
    }
    fun commitScrub() {
        scrubTargetMs?.let { engine.exoPlayer.seekTo(it) }
        scrubTargetMs = null
    }
    LaunchedEffect(scrubTargetMs, lastScrubPressMs) {
        if (scrubTargetMs == null) return@LaunchedEffect
        delay(Scrubbing.COMMIT_DELAY_MS)
        commitScrub()
    }
    // Has this video reached READY at least once? Reset when a new media item
    // prepares (ExoPlayer passes through IDLE), so the next stream / next
    // episode still gets its own load spinner.
    var startedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState) {
        when (playbackState) {
            Player.STATE_IDLE -> startedOnce = false
            Player.STATE_READY -> startedOnce = true
        }
    }
    // Mid-playback rebuffer (a committed seek, a network stall): a small
    // non-blocking ring — no scrim, no focus change, keys keep working. Only
    // after the stall persists, so brief hiccups stay invisible.
    var showRebuffer by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState, startedOnce) {
        if (playbackState == Player.STATE_BUFFERING && startedOnce) {
            delay(REBUFFER_RING_DELAY_MS)
            showRebuffer = true
        } else {
            showRebuffer = false
        }
    }
    // The load/test phase: spinner (and, if there's saved progress, the resume
    // prompt) covers the black/debrid-placeholder until the first real frame
    // paints. While a resume prompt is pending, playback is held paused by the
    // ViewModel, so `loading` stays true until the viewer answers.
    //
    // ONLY the initial load. A mid-playback re-buffer — every seek causes one —
    // must not throw a blocking scrim over the video: it swallowed the keys so
    // held scrubbing was impossible, and it flashed the spinner on each skipped
    // section (owner 2026-07-09).
    val loading = state.error == null && !state.ended && autoplay == null &&
        (state.resumePromptMs != null || (!startedOnce && playbackState != Player.STATE_READY))

    // Focus follows the bar: on it when shown, back to the full-screen catcher
    // when hidden (so the next key wakes it rather than seeking blindly). While
    // loading the resume prompt (or nothing) owns focus, so don't grab the
    // hidden scrub bar out from under it. The bar now animates in, so its node
    // may not be attached on the first frame — probe a few frames (same
    // pattern as the alpha.39 Home focus restore).
    LaunchedEffect(overlayVisible, loading) {
        when {
            loading -> Unit
            overlayVisible -> repeat(10) {
                if (runCatching { scrubFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                withFrameNanos { }
            }
            else -> runCatching { outerFocus.requestFocus() }
        }
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
                // Load/test phase: the resume prompt's buttons (or nothing) own
                // the keys — never wake a hidden bar or blind-seek here. BACK
                // still falls through to BackHandler to leave the player.
                if (loading) return@onPreviewKeyEvent false
                // Up Next countdown: OK plays now (§7.1 step 2).
                if ((code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) &&
                    viewModel.confirmAutoplay()
                ) return@onPreviewKeyEvent true
                // Panels (finished/error/Up Next) own their own buttons.
                if (state.ended || state.error != null || autoplay != null) {
                    return@onPreviewKeyEvent false
                }
                // Anime intro/credits: while the Skip button is up, OK jumps
                // past the window (takes priority over waking/pausing) — a
                // one-press skip whether or not the control bar is showing.
                if (state.skipSegment != null &&
                    (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        code == AndroidKeyEvent.KEYCODE_ENTER ||
                        code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    viewModel.skipCurrentSegment()
                    return@onPreviewKeyEvent true
                }
                // BACK closes the control bar first; a SECOND back leaves the
                // player (owner 2026-07-10). Panels/loading already returned
                // above, so this only ever dismisses the normal control UI.
                if (code == AndroidKeyEvent.KEYCODE_BACK && overlayVisible) {
                    overlayVisible = false
                    return@onPreviewKeyEvent true
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

        if (loading) {
            // Near-opaque scrim so the paused resume-point frame and the debrid
            // "resolving" clip stay hidden behind the spinner.
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xF0000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    LoadingAnimation()
                    state.resumePromptMs?.let { resumeMs ->
                        ResumePrompt(
                            resumeMs = resumeMs,
                            onResume = viewModel::resumeFromSaved,
                            onStartOver = viewModel::startFromBeginning,
                        )
                    }
                }
            }
        }

        // Slide+fade keeps the wake/sleep feeling intentional rather than a
        // pop. On boxes with animations off it degrades to today's instant
        // show/hide — never worse, sometimes silkier.
        AnimatedVisibility(
            visible = overlayVisible && !loading && state.error == null && !state.ended && autoplay == null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                modifier = Modifier
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
                // Wrapping the scrub bar redirects its DOWN to "Try a different
                // stream" so that button owns the default landing spot. UP has
                // nothing above it, so instead of trapping focus it ESCAPES the
                // whole control UI: dismiss the bar and hand focus back to the
                // video (owner 2026-07-10).
                Box(
                    modifier = Modifier
                        .focusProperties { down = tryStreamFocus }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                            ) {
                                overlayVisible = false
                                true
                            } else {
                                false
                            }
                        },
                ) {
                    ScrubBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        playing = playing,
                        scrubTargetMs = scrubTargetMs,
                        onScrub = ::scrubBy,
                        onTogglePlay = {
                            // OK mid-scrub commits the pending jump right away;
                            // otherwise it's plain play/pause.
                            if (scrubTargetMs != null) {
                                commitScrub()
                            } else if (engine.exoPlayer.isPlaying) {
                                engine.exoPlayer.pause()
                            } else {
                                engine.exoPlayer.play()
                            }
                            wake()
                        },
                        focusRequester = scrubFocus,
                    )
                }
                // Left→right: normal controls ([⏮ ⏭] · Audio & subtitles), then
                // a bordered "Having trouble?" group with the three fix-it
                // escapes + Learn more (owner 2026-07-08). Bottom-aligned so the
                // grouped buttons sit level with the plain ones despite the
                // group's caption.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Prev/Next episode jumps — icon-only, tucked next to each
                    // other in a single slot at the far left. ⏮ needs a known
                    // neighbour; ⏭ shows for EVERY episode of a series (owner
                    // 2026-07-09) even before the episode list resolves — it
                    // resolves on demand, then opens the next episode or ends
                    // the video. Movies get neither.
                    if (state.previousEpisode != null || state.isSeries) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (state.previousEpisode != null) {
                                SurfacePill("⏮", onClick = { viewModel.goToPreviousEpisode(); wake() })
                            }
                            if (state.isSeries) {
                                SurfacePill("⏭", onClick = { viewModel.goToNextEpisode(); wake() })
                            }
                        }
                    }
                    SurfacePill("Audio & subtitles", onClick = { showTracks = true; wake() })
                    TroubleGroup {
                        // Always shown (owner report): walks to the next
                        // candidate if one remains, else opens the full stream
                        // list — never a dead button. Holds the DOWN default.
                        SurfacePill(
                            "Try a different stream",
                            onClick = { viewModel.tryAnotherStream(); wake() },
                            modifier = Modifier.focusRequester(tryStreamFocus),
                        )
                        if (viewModel.externalPlayers.isNotEmpty()) {
                            SurfacePill("Play in another app", onClick = { onPlayInAnotherApp(); wake() })
                        }
                        // Software-decoder toggle for a blocky/scrambled picture.
                        // Shows its ON/OFF state (owner 2026-07-08) and flips it,
                        // reloading this video to apply (decoder is fixed at
                        // engine build). `selected` tints it when ON.
                        SurfacePill(
                            "Software video: " + if (state.softwareDecoderOn) "ON" else "OFF",
                            onClick = { viewModel.toggleSoftwareDecoder() },
                            selected = state.softwareDecoderOn,
                        )
                        SurfacePill("Learn more", onClick = { showLearnMore = true; wake() })
                    }
                }
            }
        }

        // Anime intro/credits skip: floats bottom-right during the window,
        // above the control bar, shown even when the bar is asleep. Not
        // focusable — OK is intercepted globally (above) so there's no TV focus
        // to juggle. Hidden while a panel is up.
        if (state.skipSegment != null && state.error == null && !state.ended && autoplay == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 140.dp),
            ) {
                SkipHint(state.skipSegment!!.type.label)
            }
        }

        // A stall that persists mid-playback: a small quiet ring, no scrim —
        // keys keep working and the picture stays visible underneath.
        if (showRebuffer && !loading && state.error == null && !state.ended && autoplay == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingAnimation(text = "")
            }
        }

        AnimatedVisibility(
            visible = state.switching,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(
                text = "Trying another stream…",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
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

        if (showLearnMore) {
            LearnMoreDialog(
                hasExternalPlayers = viewModel.externalPlayers.isNotEmpty(),
                onDismiss = { showLearnMore = false; wake() },
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
 * ◀▶ move an instant preview target with accelerating steps (Scrubbing.kt) —
 * the real seek commits after a quiet beat — and OK play/pauses (or commits a
 * pending jump). While scrubbing, the bar shows the TARGET position plus a
 * "+2:30"-style delta chip so a held key reads as controlled travel.
 */
@Composable
private fun ScrubBar(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    scrubTargetMs: Long?,
    onScrub: (direction: Int) -> Unit,
    onTogglePlay: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val shownMs = scrubTargetMs ?: positionMs
    val progress = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
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
                        onScrub(-1); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        onScrub(1); true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        onTogglePlay(); true
                    }
                    else -> false
                }
            },
    ) {
        Text(if (playing) "⏸" else "▶", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(shownMs.asClock(), style = MaterialTheme.typography.bodyMedium, color = Color.White)
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
        scrubTargetMs?.let { target ->
            val delta = target - positionMs
            Text(
                text = (if (delta >= 0) "+" else "−") + kotlin.math.abs(delta).asClock(),
                style = MaterialTheme.typography.bodyMedium,
                color = Accent,
                modifier = Modifier
                    .background(Color(0xE0181822), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Text(durationMs.asClock(), style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}

/**
 * The "Skip Intro"/"Skip Credits" hint (AniSkip). A styled pill, not a
 * focusable — OK is intercepted globally to fire the skip, so no focus tug on
 * the video. The [OK] chip tells the viewer which button does it.
 */
@Composable
private fun SkipHint(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .background(Color(0xF0181822), RoundedCornerShape(28.dp))
            .border(2.dp, Accent, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(
            "OK",
            style = MaterialTheme.typography.labelLarge,
            color = Accent,
            modifier = Modifier
                .border(1.dp, Accent, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * The "Having trouble?" group (owner 2026-07-08): a captioned ring around the
 * fix-it escapes so an older viewer sees them as one "something's wrong" cluster
 * rather than four loose buttons.
 */
@Composable
private fun TroubleGroup(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Having trouble?",
            style = MaterialTheme.typography.labelMedium,
            color = MutedText,
            modifier = Modifier.padding(start = 10.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(1.5.dp, Accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(10.dp),
            content = content,
        )
    }
}

/** Plain-language help for the "Having trouble?" buttons (owner 2026-07-08). */
@Composable
private fun LearnMoreDialog(hasExternalPlayers: Boolean, onDismiss: () -> Unit) {
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { okFocus.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .background(Color(0xF0181822), RoundedCornerShape(16.dp))
                .padding(28.dp),
        ) {
            Text("If something's not right", style = MaterialTheme.typography.titleLarge, color = Color.White)
            HelpLine("Try a different stream", "Loads this show from another source. Best first thing to try when a video won't play or keeps buffering.")
            if (hasExternalPlayers) {
                HelpLine("Play in another app", "Hands the video to VLC or MX Player. Try this when the picture plays but there's no sound, or the audio is the wrong language.")
            }
            HelpLine("Software video (ON/OFF)", "The app now picks this automatically for videos this TV can't show cleanly. If the picture still looks blocky or scrambled, turn it ON — the video reloads right where you are. Turn OFF to go back to the faster hardware video.")
            Button(onClick = onDismiss, modifier = Modifier.focusRequester(okFocus)) { Text("Got it") }
        }
    }
}

@Composable
private fun HelpLine(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth(0.9f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Accent)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White)
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

/**
 * "Resume from X / Start from the beginning" shown over the loading spinner
 * while the stream is tested (owner 2026-07-08). Same content survives whatever
 * stream/link is picked because progress is keyed by the video, not the URL
 * (MediaRef, §8.4). Resume holds initial focus so a single OK continues — the
 * common case for someone getting back to a show they were partway through.
 */
@Composable
private fun ResumePrompt(
    resumeMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { resumeFocus.requestFocus() } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "You've watched part of this",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onResume, modifier = Modifier.focusRequester(resumeFocus)) {
                Text("Resume from ${resumeMs.asClock()}")
            }
            Button(onClick = onStartOver) { Text("Start from the beginning") }
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
