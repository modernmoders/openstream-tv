package dev.openstream.tv.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRequestException
import dev.openstream.tv.addon.NameLookupOutcome
import dev.openstream.tv.addon.ProfileInstaller
import dev.openstream.tv.addon.SetupNameLookup
import dev.openstream.tv.data.SetupConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One-step setup (owner directive 2026-07-06, simplified 2026-07-06 round 2):
 * the person types their name and EVERYTHING else happens on its own — lookup,
 * profile fetch, install — with no confirm/accept screen in the way. Nobody
 * ever sees a URL.
 *
 *   name → Working… → Done ("You're all set") → (fades to Home)
 *
 * Ambiguous names ("myles") are the only detour: we ask which one, then the
 * same automatic path runs.
 */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val lookup: SetupNameLookup,
    private val installer: ProfileInstaller,
    config: SetupConfig,
) : ViewModel() {

    sealed interface UiState {
        /** Waiting for a name; [error] is a friendly retry hint, never jargon. */
        data class AskName(val error: String? = null) : UiState
        /** Working — [message] tells the person what's happening in their words. */
        data class Busy(val message: String) : UiState
        /** Several people share that name — ask which one. */
        data class WhichOne(val choices: List<String>) : UiState
        /** Everything installed; the screen shows this briefly, then fades Home. */
        data class Done(val displayName: String, val count: Int) : UiState
    }

    /** For the screen's greeting ("Welcome to SavoyStreams"). */
    val brand: String = config.brand

    private val _state = MutableStateFlow<UiState>(UiState.AskName())
    val state: StateFlow<UiState> = _state

    fun submitName(raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) {
            _state.value = UiState.AskName("Type your first name to get started.")
            return
        }
        resolve { lookup.byName(name) }
    }

    /** A pick from the [UiState.WhichOne] list. */
    fun choose(fullName: String) = resolve { lookup.byFullName(fullName) }

    fun startOver() {
        _state.value = UiState.AskName()
    }

    private fun resolve(call: suspend () -> Result<NameLookupOutcome>) {
        _state.value = UiState.Busy("One moment…")
        viewModelScope.launch {
            call().fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is NameLookupOutcome.Found -> setUp(outcome)
                        is NameLookupOutcome.Ambiguous -> _state.value = UiState.WhichOne(outcome.choices)
                        is NameLookupOutcome.NoMatch -> _state.value = UiState.AskName(outcome.message)
                    }
                },
                onFailure = { _state.value = UiState.AskName(it.toFriendlyMessage()) },
            )
        }
    }

    /** No accept screen: as soon as we know who you are, set the TV up. */
    private suspend fun setUp(found: NameLookupOutcome.Found) {
        _state.value = UiState.Busy("Setting up your shows…")
        installer.plan(found.profileUrl).fold(
            onSuccess = { plan ->
                val installed = installer.install(plan)
                _state.value = if (installed > 0) {
                    UiState.Done(found.fullName.ifBlank { plan.profileName }, installed)
                } else {
                    UiState.AskName(
                        "Almost — nothing wanted to load just now. " +
                            "Give it a minute and try again."
                    )
                }
            },
            onFailure = { _state.value = UiState.AskName(it.toFriendlyMessage()) },
        )
    }

    /** Plain words only (§4.1.8) — whatever went wrong, say what to DO. */
    private fun Throwable.toFriendlyMessage(): String =
        when ((this as? AddonRequestException)?.reason) {
            AddonRequestException.Reason.NETWORK ->
                "Can't reach the internet right now — check the connection and try again."
            AddonRequestException.Reason.BAD_JSON, AddonRequestException.Reason.INVALID_MANIFEST ->
                "The setup service answered strangely — tell whoever set this up, then try again."
            AddonRequestException.Reason.HTTP_STATUS ->
                "The setup service is having a moment — try again in a minute."
            else -> "Something unexpected happened — please try again."
        }
}
