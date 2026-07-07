package dev.openstream.tv.ui.player

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.addon.Video
import dev.openstream.tv.addon.toPlayableSource
import dev.openstream.tv.autoplay.AutoplayController
import dev.openstream.tv.autoplay.AutoplayOriginHolder
import dev.openstream.tv.autoplay.AutoplayStateMachine
import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.data.PlaybackPrefs
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.diagnostics.DiagnosticsSink
import dev.openstream.tv.data.SUBTITLES_OFF
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExoPlayerEngine
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.player.PlaybackRequest
import dev.openstream.tv.player.PlaybackService
import dev.openstream.tv.player.PlayerEvent
import dev.openstream.tv.player.PlayerHolder
import dev.openstream.tv.player.StreamAlternatives
import dev.openstream.tv.player.buildExternalLaunch
import dev.openstream.tv.player.TrackKind
import dev.openstream.tv.player.TrackOption
import dev.openstream.tv.player.applyPreferredLanguages
import dev.openstream.tv.player.rememberedLanguage
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

/** Consecutive broken streams auto-skipped before giving up with the panel. */
private const val MAX_ERROR_SKIPS = 3

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
    private val autoplay: AutoplayController,
    private val autoplayOrigin: AutoplayOriginHolder,
    private val playbackPrefs: PlaybackPrefs,
    private val alternatives: StreamAlternatives,
    private val externalLauncher: ExternalPlayerPort,
    playerHolder: PlayerHolder,
    private val diagnostics: DiagnosticsSink = DiagnosticsSink.NONE,
) : ViewModel() {

    /** Installed external players (VLC/MX) for the "Play in another app"
     *  escape — the fix for streams that play wrong in ExoPlayer (no audio,
     *  no video, codec gaps on the 32-bit boxes). Empty = the button hides. */
    val externalPlayers: List<ExternalPlayerPort.Choice> = externalLauncher.installedPlayers()
        .filter { it.packageName != null } // a real app to hand off to

    /**
     * The service-owned engine (§6.1); null until [PlaybackService] is up.
     * The screen binds its PlayerView the moment this becomes non-null.
     */
    val engine: StateFlow<ExoPlayerEngine?> = playerHolder.engine

    data class UiState(
        val title: String = "",
        /** Null = nothing to play (process death) — screen must pop back. */
        val hasSource: Boolean = true,
        val ended: Boolean = false,
        val error: String? = null,
        /** More streams to walk — shows the "Try another server" affordances. */
        val canTryNext: Boolean = false,
        /** Friendly banner while a server switch spins up (elder rule: no raw errors). */
        val switching: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** Up Next card state; null = card hidden (§7.1). */
    val autoplayState: StateFlow<AutoplayStateMachine.State?> = autoplay.state

    /** Where to land when autoplay gives up: the next episode's stream list. */
    data class OpenStreams(val type: String, val videoId: String, val title: String, val metaId: String, val poster: String?)
    val openStreams: SharedFlow<OpenStreams> get() = _openStreams
    private val _openStreams = MutableSharedFlow<OpenStreams>(extraBufferCapacity = 1)

    /** var, not val: autoplay replaces the request when it advances episodes. */
    private var request = currentPlayback.request

    /** Auto-play-first-stream is also "auto-skip a broken stream" (owner
     *  request 2026-07-06) — read once; a mid-playback settings change is fine
     *  to pick up next playback. */
    private var autoAdvanceOnError = false

    /** Consecutive error-driven server switches; capped so a dead video id
     *  can't cycle every server forever. A successful Ready resets it. */
    private var errorSkips = 0

    init {
        val req = request
        if (req == null) {
            _uiState.value = UiState(hasSource = false)
        } else {
            _uiState.value = UiState(title = req.source.title, canTryNext = alternatives.hasNext())
            context.startService(Intent(context, PlaybackService::class.java))
            viewModelScope.launch {
                autoAdvanceOnError = playbackPrefs.autoPlayFirstStream.first()
                val engine = this@PlayerViewModel.engine.filterNotNull().first()
                // Remembered languages (DECISIONS #19) go on BEFORE play so the
                // first track selection already honors them. Parameters stick
                // to the player instance, so autoplay episode swaps keep them.
                val languages = playbackPrefs.languages.first()
                engine.exoPlayer.applyPreferredLanguages(languages.audio, languages.subtitle)
                engine.play(req.source)
                launch { collectPlayerEvents(engine) }
                launch { collectAutoplayCommands(engine) }
                launch {
                    // Crash/kill loses at most this interval of progress.
                    while (true) {
                        delay(PROGRESS_SAVE_INTERVAL_MS)
                        persistProgress(engine)
                    }
                }
            }
        }
    }

    private suspend fun collectPlayerEvents(engine: ExoPlayerEngine) {
        engine.events.collect { event ->
            val req = request ?: return@collect
            when (event) {
                is PlayerEvent.Ready -> {
                    errorSkips = 0
                    _uiState.value = _uiState.value.copy(switching = false)
                    autoplay.onPlaybackReady()
                }
                is PlayerEvent.Ended -> {
                    // Finished = no longer resumable
                    req.mediaRef?.let { progressRepository.clearAsync(it) }
                    _uiState.value = _uiState.value.copy(ended = true)
                    autoplay.onPlaybackEnded(
                        scope = viewModelScope,
                        metaType = req.metaType,
                        metaId = req.metaId,
                        mediaRef = req.mediaRef,
                        origin = autoplayOrigin.origin,
                    )
                }
                is PlayerEvent.Error -> {
                    // Every playback failure lands in the log with its raw
                    // code/cause (§10 "log them") — the codec detail the
                    // 32-bit boxes' failures need, without a logcat hookup.
                    diagnostics.record(
                        "player",
                        "\"${_uiState.value.title}\": ${event.message}" +
                            event.detail.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty(),
                    )
                    when {
                        // While autoplay is attempting, an open failure is its
                        // fallthrough signal (§7.1 step 5), not an error panel.
                        autoplay.onPlaybackError(viewModelScope) -> Unit
                        // Auto-skip a broken stream to the next server (owner
                        // request 2026-07-06) — quietly, like a human would.
                        autoAdvanceOnError && errorSkips < MAX_ERROR_SKIPS && tryNextStream() ->
                            errorSkips++
                        else -> _uiState.value =
                            _uiState.value.copy(error = event.message, switching = false)
                    }
                }
            }
        }
    }

    private suspend fun collectAutoplayCommands(engine: ExoPlayerEngine) {
        autoplay.commands.collect { command ->
            when (command) {
                is AutoplayController.Command.Play -> playNext(engine, command.next, command.candidate)
                is AutoplayController.Command.OpenStreamList -> {
                    val req = request ?: return@collect
                    _openStreams.tryEmit(
                        OpenStreams(req.metaType, command.next.id, episodeTitle(command.next), req.metaId, req.poster)
                    )
                }
            }
        }
    }

    private fun playNext(engine: ExoPlayerEngine, next: Video, candidate: StreamCascade.Candidate) {
        val req = request ?: return
        val source = candidate.stream.toPlayableSource(episodeTitle(next))
        if (source == null) {
            // Ranked candidates are playable by construction; belt-and-braces.
            autoplay.onPlaybackError(viewModelScope)
            return
        }
        val newRequest = PlaybackRequest(
            source = source,
            mediaRef = MediaRef.addon(next.id),
            metaId = req.metaId,
            metaType = req.metaType,
            poster = req.poster,
        )
        request = newRequest
        currentPlayback.request = newRequest
        autoplayOrigin.origin = StreamCascade.CurrentStream(candidate.addonUrl, candidate.stream)
        // The alternatives list belonged to the finished episode; the new
        // one's list is unknown until its own stream screen loads it.
        alternatives.clear()
        _uiState.value = _uiState.value.copy(
            title = source.title, ended = false, error = null, canTryNext = false,
        )
        engine.play(source)
    }

    /**
     * Walk to the next stream for the SAME video (owner request 2026-07-06):
     * the "Try another server" button, and the quiet auto-skip on errors.
     * Playback continues from where the broken stream left off. False when
     * the list is exhausted — the caller falls back to the error panel.
     */
    fun tryNextStream(): Boolean {
        val req = request ?: return false
        val engine = engine.value ?: return false
        while (true) {
            val alt = alternatives.advance() ?: return false
            val source = alt.stream.toPlayableSource(req.source.title) ?: continue
            // Mid-play death keeps the position; an open failure has none, so
            // the original start (e.g. the auto-resume point) carries over.
            val position = engine.exoPlayer.currentPosition.takeIf { it > 0 }
                ?: req.source.startPositionMs
            val newRequest = req.copy(source = source.copy(startPositionMs = position))
            request = newRequest
            currentPlayback.request = newRequest
            autoplayOrigin.origin = StreamCascade.CurrentStream(alt.addonUrl, alt.stream)
            _uiState.value = _uiState.value.copy(
                error = null, ended = false,
                switching = true, canTryNext = alternatives.hasNext(),
            )
            engine.play(newRequest.source)
            return true
        }
    }

    private fun episodeTitle(next: Video): String {
        val se = if (next.season != null && next.episode != null) "Season ${next.season} · Episode ${next.episode}" else null
        return listOfNotNull(se, next.displayTitle.takeIf { it.isNotBlank() }).joinToString(" · ")
            .ifBlank { request?.source?.title.orEmpty() }
    }

    /**
     * Remember a track pick as the preferred language (DECISIONS #19).
     * Tag-less tracks change nothing — a stored preference survives them.
     */
    fun rememberTrackPick(option: TrackOption) {
        val language = rememberedLanguage(option) ?: return
        viewModelScope.launch {
            when (option.kind) {
                TrackKind.AUDIO -> playbackPrefs.setAudioLanguage(language)
                TrackKind.SUBTITLE -> playbackPrefs.setSubtitleLanguage(language)
            }
        }
    }

    /** Subtitles Off is itself a preference: stay off next playback. */
    fun rememberSubtitlesOff() {
        viewModelScope.launch { playbackPrefs.setSubtitleLanguage(SUBTITLES_OFF) }
    }

    /** OK press while the Up Next card is visible. True = consumed. */
    fun confirmAutoplay(): Boolean = autoplay.onConfirm(viewModelScope)

    /** Back press. True = autoplay consumed it (stay on the player screen). */
    fun backPressed(): Boolean = autoplay.onBack(viewModelScope)

    /**
     * Snapshot the player position into the progress table. Main-thread only
     * (ExoPlayer contract); the DB write itself hops to IO inside the repo.
     */
    private fun persistProgress(engine: ExoPlayerEngine) {
        val req = request ?: return
        val ref = req.mediaRef ?: return
        if (_uiState.value.ended) return
        val duration = engine.exoPlayer.duration
        val position = engine.exoPlayer.currentPosition
        // duration is TIME_UNSET until prepared; skip until it's real.
        if (duration <= 0 || position <= 0) return
        progressRepository.saveAsync(
            WatchProgress(
                ref = ref,
                metaId = req.metaId,
                metaType = req.metaType,
                title = req.source.title,
                poster = req.poster,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * The player's one "get me out of this stream" action (owner report:
     * hiding the button when the ranked cascade is exhausted left focus
     * landing on the unrelated "Play in another app" instead). Walks to the
     * next candidate if any remain (fast, same position); otherwise opens
     * the full stream list for THIS video so there's always somewhere useful
     * to go, never a dead/hidden button.
     */
    fun tryAnotherStream() {
        if (!tryNextStream()) openCurrentStreamList()
    }

    private fun openCurrentStreamList() {
        val req = request ?: return
        val videoId = req.mediaRef?.externalId ?: return
        _openStreams.tryEmit(OpenStreams(req.metaType, videoId, req.source.title, req.metaId, req.poster))
    }

    fun retry() {
        val req = request ?: return
        val engine = engine.value ?: return
        _uiState.value = _uiState.value.copy(error = null, ended = false)
        engine.play(req.source)
    }

    /**
     * Hand the CURRENT stream to VLC/MX at the current position ("Play in
     * another app"). We pause our own engine first so two players don't fight
     * over the audio. Null if nothing is playing.
     */
    fun externalIntentForCurrent(choice: ExternalPlayerPort.Choice): Intent? {
        val req = request ?: return null
        val position = engine.value?.exoPlayer?.currentPosition?.takeIf { it > 0 }
            ?: req.source.startPositionMs
        engine.value?.exoPlayer?.pause()
        val source = req.source.copy(startPositionMs = position)
        return externalLauncher.intentFor(
            buildExternalLaunch(choice.player, choice.packageName, source)
        )
    }

    override fun onCleared() {
        autoplay.stop()
        // Exit position — the one users actually resume from.
        engine.value?.let { persistProgress(it) }
        // Back from the player means stop (TV UX): tear the service down,
        // which releases the session and the engine.
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
