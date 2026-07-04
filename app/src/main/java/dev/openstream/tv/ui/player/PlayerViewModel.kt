package dev.openstream.tv.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExoPlayerEngine
import dev.openstream.tv.player.PlayerEvent
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    currentPlayback: CurrentPlayback,
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

    private val source = currentPlayback.source

    init {
        if (source == null) {
            _uiState.value = UiState(hasSource = false)
        } else {
            _uiState.value = UiState(title = source.title)
            engine.play(source)
            viewModelScope.launch {
                engine.events.collect { event ->
                    when (event) {
                        is PlayerEvent.Ended ->
                            _uiState.value = _uiState.value.copy(ended = true)
                        is PlayerEvent.Error ->
                            _uiState.value = _uiState.value.copy(error = event.message)
                    }
                }
            }
        }
    }

    fun retry() {
        val s = source ?: return
        _uiState.value = _uiState.value.copy(error = null, ended = false)
        engine.play(s)
    }

    override fun onCleared() {
        engine.release()
    }
}
