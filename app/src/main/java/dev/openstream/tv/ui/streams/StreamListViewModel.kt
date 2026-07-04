package dev.openstream.tv.ui.streams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamRepository
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Stream list fan-out (§4.1.5): every stream-declaring addon is queried in
 * parallel; each group renders the moment its addon answers — never wait for
 * the slowest addon. Failures are visible chips (§4.1.8).
 */
@HiltViewModel
class StreamListViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val type: String = checkNotNull(savedStateHandle["type"])
    val videoId: String = checkNotNull(savedStateHandle["videoId"])
    val title: String = savedStateHandle["title"] ?: ""

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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            val addons = streamRepository.streamAddons(type, videoId)
            _uiState.value = UiState(
                initializing = false,
                groups = addons.map { GroupState.Loading(it) },
            )
            addons.forEach { addon ->
                launch {
                    val newState = streamRepository.fetch(addon, type, videoId).fold(
                        onSuccess = { GroupState.Loaded(addon, it) },
                        onFailure = { GroupState.Failed(addon, it.toChipMessage()) },
                    )
                    _uiState.value = _uiState.value.copy(
                        groups = _uiState.value.groups.map {
                            if (it.addon.manifestUrl == addon.manifestUrl) newState else it
                        },
                    )
                }
            }
        }
    }
}
