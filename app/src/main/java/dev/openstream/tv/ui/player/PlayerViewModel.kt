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
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExoPlayerEngine
import dev.openstream.tv.player.PlaybackRequest
import dev.openstream.tv.player.PlaybackService
import dev.openstream.tv.player.PlayerEvent
import dev.openstream.tv.player.PlayerHolder
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

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
    private val autoplay: AutoplayController,
    private val autoplayOrigin: AutoplayOriginHolder,
    playerHolder: PlayerHolder,
) : ViewModel() {

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

    init {
        val req = request
        if (req == null) {
            _uiState.value = UiState(hasSource = false)
        } else {
            _uiState.value = UiState(title = req.source.title)
            context.startService(Intent(context, PlaybackService::class.java))
            viewModelScope.launch {
                val engine = this@PlayerViewModel.engine.filterNotNull().first()
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
                is PlayerEvent.Ready -> autoplay.onPlaybackReady()
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
                is PlayerEvent.Error ->
                    // While autoplay is attempting, an open failure is its
                    // fallthrough signal (§7.1 step 5), not an error panel.
                    if (!autoplay.onPlaybackError(viewModelScope)) {
                        _uiState.value = _uiState.value.copy(error = event.message)
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
        _uiState.value = _uiState.value.copy(title = source.title, ended = false, error = null)
        engine.play(source)
    }

    private fun episodeTitle(next: Video): String {
        val se = if (next.season != null && next.episode != null) "S${next.season}E${next.episode}" else null
        return listOfNotNull(se, next.displayTitle.takeIf { it.isNotBlank() }).joinToString(" · ")
            .ifBlank { request?.source?.title.orEmpty() }
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

    fun retry() {
        val req = request ?: return
        val engine = engine.value ?: return
        _uiState.value = _uiState.value.copy(error = null, ended = false)
        engine.play(req.source)
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
