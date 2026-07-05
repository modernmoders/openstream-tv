package dev.openstream.tv.ui.streams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamRepository
import dev.openstream.tv.addon.toPlayableSource
import dev.openstream.tv.autoplay.AutoplayOriginHolder
import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.PlaybackRequest
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stream list fan-out (§4.1.5): every stream-declaring addon is queried in
 * parallel; each group renders the moment its addon answers — never wait for
 * the slowest addon. Failures are visible chips (§4.1.8).
 */
@HiltViewModel
class StreamListViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
    private val autoplayOrigin: AutoplayOriginHolder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val type: String = checkNotNull(savedStateHandle["type"])
    val videoId: String = checkNotNull(savedStateHandle["videoId"])
    val title: String = savedStateHandle["title"] ?: ""

    /** Meta id for progress rows; movies arrive without one — video id IS the meta id. */
    private val metaId: String = savedStateHandle.get<String>("metaId")?.ifBlank { null } ?: videoId
    private val poster: String? = savedStateHandle.get<String>("poster")?.ifBlank { null }

    /** Progress key for this video (§8.4): opaque, addon-kind for now. */
    private val mediaRef = MediaRef.addon(videoId)

    sealed interface GroupState {
        val addon: InstalledAddon

        data class Loading(override val addon: InstalledAddon) : GroupState
        data class Loaded(override val addon: InstalledAddon, val streams: List<Stream>) : GroupState
        data class Failed(override val addon: InstalledAddon, val message: String) : GroupState
    }

    data class UiState(
        val initializing: Boolean = true,
        /** One group per addon, user's addon order (§4.1.7). */
        val groups: List<GroupState> = emptyList(),
        /** Non-null = show "Resume from X / Start over" before playing. */
        val resumePositionMs: Long? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /**
     * Stage the stream for the player screen; returns false for sources v1
     * can't play (the UI shows those as notes, so this is belt-and-braces).
     */
    fun stage(addon: InstalledAddon, stream: Stream, startPositionMs: Long = 0): Boolean {
        val source = stream.toPlayableSource(title.ifBlank { stream.name ?: "Stream" })
            ?: return false
        currentPlayback.request = PlaybackRequest(
            source = source.copy(startPositionMs = startPositionMs),
            mediaRef = mediaRef,
            metaId = metaId,
            metaType = type,
            poster = poster,
        )
        // Autoplay's tier-1/2 ranking context (§7.1) — which addon, which stream
        autoplayOrigin.origin = StreamCascade.CurrentStream(addon.manifestUrl, stream)
        return true
    }

    init {
        viewModelScope.launch {
            // Collect, don't read once: this ViewModel survives on the back
            // stack while the player advances progress (found in Phase 2 gate).
            progressRepository.observeResumePosition(mediaRef).collect { positionMs ->
                _uiState.update { it.copy(resumePositionMs = positionMs) }
            }
        }
        viewModelScope.launch {
            val addons = streamRepository.streamAddons(type, videoId)
            // update{}, not value=: must not clobber the parallel resume-position load
            _uiState.update {
                it.copy(initializing = false, groups = addons.map { a -> GroupState.Loading(a) })
            }
            addons.forEach { addon ->
                launch {
                    val newState = streamRepository.fetch(addon, type, videoId).fold(
                        onSuccess = { GroupState.Loaded(addon, it) },
                        onFailure = { GroupState.Failed(addon, it.toChipMessage()) },
                    )
                    // Atomic: parallel addon completions must not clobber
                    // each other's group updates
                    _uiState.update { state ->
                        state.copy(groups = state.groups.map {
                            if (it.addon.manifestUrl == addon.manifestUrl) newState else it
                        })
                    }
                }
            }
        }
    }
}
