package dev.openstream.tv.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonClient
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.AddonRequestException
import dev.openstream.tv.addon.AddonUrls
import dev.openstream.tv.addon.Manifest
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Install flow per MASTER_PLAN §4.1.1: fetch the manifest first and show the
 * user what they're installing (name, description, types, resources,
 * catalogs); persist only on explicit confirmation.
 */
@HiltViewModel
class AddAddonViewModel @Inject constructor(
    private val client: AddonClient,
    private val repository: AddonRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Fetching : UiState
        /** Manifest fetched and valid — waiting for the user to confirm. */
        data class Preview(val manifestUrl: String, val manifest: Manifest) : UiState
        data object Installing : UiState
        /** Terminal: the screen should navigate back. */
        data object Installed : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun fetchPreview(rawUrl: String) {
        val manifestUrl = AddonUrls.normalizeManifestUrl(rawUrl)
        if (manifestUrl == null) {
            _state.value = UiState.Error(
                "That doesn't look like an addon URL — it should end in manifest.json"
            )
            return
        }
        _state.value = UiState.Fetching
        viewModelScope.launch {
            client.fetchManifest(manifestUrl)
                .onSuccess { _state.value = UiState.Preview(manifestUrl, it) }
                .onFailure { _state.value = UiState.Error(it.toUserMessage()) }
        }
    }

    fun confirmInstall() {
        val preview = _state.value as? UiState.Preview ?: return
        _state.value = UiState.Installing
        viewModelScope.launch {
            repository.install(preview.manifestUrl)
                .onSuccess { _state.value = UiState.Installed }
                .onFailure { _state.value = UiState.Error(it.toUserMessage()) }
        }
    }

    fun dismissPreview() {
        _state.value = UiState.Idle
    }

    /** Plain-language errors (MASTER_PLAN §4.1.8) — no stack traces on a TV. */
    private fun Throwable.toUserMessage(): String = when ((this as? AddonRequestException)?.reason) {
        AddonRequestException.Reason.NETWORK ->
            "Couldn't reach the addon — check the URL and your connection"
        AddonRequestException.Reason.HTTP_STATUS ->
            "The addon server answered with an error ($message)"
        AddonRequestException.Reason.BAD_JSON ->
            "The addon sent a response this app couldn't read"
        AddonRequestException.Reason.INVALID_MANIFEST ->
            "That URL responded, but not with a valid addon manifest"
        AddonRequestException.Reason.INVALID_URL ->
            "That doesn't look like an addon URL — it should end in manifest.json"
        null -> "Something went wrong: $message"
    }
}
