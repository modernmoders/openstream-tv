package dev.openstream.tv.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonClient
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.AddonRequestException
import dev.openstream.tv.addon.AddonUrls
import dev.openstream.tv.addon.Manifest
import dev.openstream.tv.addon.ProfileInstaller
import dev.openstream.tv.addon.ProfilePlan
import dev.openstream.tv.addon.RemoteEntryServer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
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
    private val remoteEntryServer: RemoteEntryServer,
    private val profileInstaller: ProfileInstaller,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Fetching : UiState
        /** Manifest fetched and valid — waiting for the user to confirm. */
        data class Preview(val manifestUrl: String, val manifest: Manifest) : UiState
        /** A setup link resolved — waiting to confirm installing the lot. */
        data class ProfilePreview(val plan: ProfilePlan) : UiState {
            val profileName: String get() = plan.profileName
            val entries: List<ProfilePlan.Entry> get() = plan.entries
        }
        data object Installing : UiState
        /**
         * One install round done. The screen STAYS here showing [summary] so
         * the user can paste the next addon without a round-trip through the
         * addon list (owner request 2026-07-05 round 6); Back leaves.
         */
        data class Installed(val summary: String) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    /** `http://<lan-ip>:<port>` to type into a browser, or null if unavailable. */
    private val _remoteEntryUrl = MutableStateFlow<String?>(null)
    val remoteEntryUrl: StateFlow<String?> = _remoteEntryUrl

    private var remoteEntryStarted = false

    /**
     * Serve the browser entry page while the screen is showing. The server is
     * a convenience input path only — submissions land in the same
     * [fetchPreview] → confirm flow as the on-screen keyboard (§4.1.1).
     */
    fun startRemoteEntry() {
        if (remoteEntryStarted) return
        remoteEntryStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val port = remoteEntryServer.start(viewModelScope, ::onRemoteSubmit)
            if (!remoteEntryStarted) { // stopped while we were binding
                remoteEntryServer.stop()
                return@launch
            }
            val host = RemoteEntryServer.lanAddress()
            // Off-network or all ports taken: just don't offer the option.
            if (port != null && host != null) _remoteEntryUrl.value = "http://$host:$port"
        }
    }

    fun stopRemoteEntry() {
        remoteEntryStarted = false
        remoteEntryServer.stop()
        _remoteEntryUrl.value = null
    }

    /** Called on the server's IO thread. Internal so tests can drive it directly. */
    internal fun onRemoteSubmit(rawUrl: String): RemoteEntryServer.Outcome {
        val trimmed = rawUrl.trim()
        val plausible = AddonUrls.normalizeManifestUrl(trimmed) != null ||
            trimmed.startsWith("https://") || trimmed.startsWith("http://")
        if (!plausible) {
            return RemoteEntryServer.Outcome.Rejected(
                "That doesn't look like a link — paste an addon URL " +
                    "(ends in manifest.json) or a setup link."
            )
        }
        fetchPreview(trimmed) // setup links resolve on the TV, like any input
        return RemoteEntryServer.Outcome.Accepted
    }

    override fun onCleared() {
        stopRemoteEntry()
    }

    fun fetchPreview(rawUrl: String) {
        val manifestUrl = AddonUrls.normalizeManifestUrl(rawUrl)
        if (manifestUrl != null) {
            _state.value = UiState.Fetching
            viewModelScope.launch {
                client.fetchManifest(manifestUrl)
                    .onSuccess { _state.value = UiState.Preview(manifestUrl, it) }
                    .onFailure { _state.value = UiState.Error(it.toUserMessage()) }
            }
            return
        }
        // Not a manifest URL — maybe a setup link (DECISIONS #14).
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            _state.value = UiState.Error(
                "That doesn't look like a link — paste an addon URL " +
                    "(ends in manifest.json) or a setup link"
            )
            return
        }
        _state.value = UiState.Fetching
        viewModelScope.launch {
            profileInstaller.plan(trimmed)
                .onSuccess { _state.value = UiState.ProfilePreview(it) }
                .onFailure { _state.value = UiState.Error(it.toProfileUserMessage()) }
        }
    }

    /** Install every good entry from the previewed setup link, in order. */
    fun confirmInstallProfile() {
        val preview = _state.value as? UiState.ProfilePreview ?: return
        _state.value = UiState.Installing
        viewModelScope.launch {
            val ok = profileInstaller.install(preview.plan)
            _state.value = if (ok > 0) {
                UiState.Installed(if (ok == 1) "1 addon installed" else "$ok addons installed")
            } else {
                UiState.Error("Couldn't install any addons from that setup link")
            }
        }
    }

    fun confirmInstall() {
        val preview = _state.value as? UiState.Preview ?: return
        _state.value = UiState.Installing
        viewModelScope.launch {
            repository.install(preview.manifestUrl)
                .onSuccess {
                    _state.value = UiState.Installed("${preview.manifest.name} installed")
                }
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

    /** Same mapping, reworded for the setup-link path (INVALID_MANIFEST there
     *  means "responded, but not with a profile", not a bad manifest). */
    private fun Throwable.toProfileUserMessage(): String =
        if ((this as? AddonRequestException)?.reason == AddonRequestException.Reason.INVALID_MANIFEST) {
            "That link answered, but it isn't an addon or a setup link"
        } else {
            toUserMessage()
        }
}
