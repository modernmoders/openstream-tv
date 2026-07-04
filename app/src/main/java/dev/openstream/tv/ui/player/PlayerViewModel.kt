package dev.openstream.tv.ui.player

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExoPlayerEngine
import dev.openstream.tv.player.PlaybackService
import dev.openstream.tv.player.PlayerEvent
import dev.openstream.tv.player.PlayerHolder
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
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

    private val request = currentPlayback.request

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
                launch {
                    engine.events.collect { event ->
                        when (event) {
                            is PlayerEvent.Ended -> {
                                // Finished = no longer resumable; Phase 3
                                // autoplay hands the NEXT episode to
                                // Continue Watching.
                                req.mediaRef?.let { progressRepository.clearAsync(it) }
                                _uiState.value = _uiState.value.copy(ended = true)
                            }
                            is PlayerEvent.Error ->
                                _uiState.value = _uiState.value.copy(error = event.message)
                        }
                    }
                }
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
        // Exit position — the one users actually resume from.
        engine.value?.let { persistProgress(it) }
        // Back from the player means stop (TV UX): tear the service down,
        // which releases the session and the engine.
        context.stopService(Intent(context, PlaybackService::class.java))
    }
}
