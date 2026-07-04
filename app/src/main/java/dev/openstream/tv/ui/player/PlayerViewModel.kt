package dev.openstream.tv.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExoPlayerEngine
import dev.openstream.tv.player.PlayerEvent
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    /** One engine per playback session; released with the ViewModel. */
    val engine = ExoPlayerEngine(context)

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
            engine.play(req.source)
            viewModelScope.launch {
                engine.events.collect { event ->
                    when (event) {
                        is PlayerEvent.Ended -> {
                            // Finished = no longer resumable; Phase 3 autoplay
                            // will hand the NEXT episode to Continue Watching.
                            req.mediaRef?.let { progressRepository.clearAsync(it) }
                            _uiState.value = _uiState.value.copy(ended = true)
                        }
                        is PlayerEvent.Error ->
                            _uiState.value = _uiState.value.copy(error = event.message)
                    }
                }
            }
            viewModelScope.launch {
                // Crash/kill loses at most this interval of progress.
                while (true) {
                    delay(PROGRESS_SAVE_INTERVAL_MS)
                    persistProgress()
                }
            }
        }
    }

    /**
     * Snapshot the player position into the progress table. Main-thread only
     * (ExoPlayer contract); the DB write itself hops to IO inside the repo.
     */
    private fun persistProgress() {
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
        _uiState.value = _uiState.value.copy(error = null, ended = false)
        engine.play(req.source)
    }

    override fun onCleared() {
        persistProgress() // exit position — the one users actually resume from
        engine.release()
    }
}
