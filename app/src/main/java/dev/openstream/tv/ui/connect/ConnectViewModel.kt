package dev.openstream.tv.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRequestException
import dev.openstream.tv.addon.NameLookupOutcome
import dev.openstream.tv.addon.ProfileInstaller
import dev.openstream.tv.addon.ProfilePlan
import dev.openstream.tv.addon.SetupNameLookup
import dev.openstream.tv.data.SetupConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One-step setup (owner directive 2026-07-06): the person types their name,
 * everything else — lookup, profile fetch, install — happens back here.
 * Nobody ever sees a URL. Doubles as the first-launch Welcome Guide.
 *
 * name → Busy(looking) → Ready("Hi Adam!") → confirm → Busy(setting up)
 * → Done. Ambiguous names ("myles") detour through WhichOne.
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
        /** Everything resolved; waiting for one OK on "Finish setup". */
        data class Ready(val displayName: String, val plan: ProfilePlan) : UiState
        data class Done(val displayName: String, val count: Int) : UiState
    }

    /** For the screen's greeting ("Welcome to SavoyStreams!"). */
    val brand: String = config.brand

    private val _state = MutableStateFlow<UiState>(UiState.AskName())
    val state: StateFlow<UiState> = _state

    fun submitName(raw: String) {
        val name = raw.trim()
        if (name.isEmpty()) {
            _state.value = UiState.AskName("Type your first name — that's all we need.")
            return
        }
        resolve { lookup.byName(name) }
    }

    /** A pick from the [UiState.WhichOne] list. */
    fun choose(fullName: String) = resolve { lookup.byFullName(fullName) }

    fun startOver() {
        _state.value = UiState.AskName()
    }

    fun confirm() {
        val ready = _state.value as? UiState.Ready ?: return
        _state.value = UiState.Busy("Setting everything up — almost there…")
        viewModelScope.launch {
            val ok = installer.install(ready.plan)
            _state.value = if (ok > 0) {
                UiState.Done(ready.displayName, ok)
            } else {
                UiState.AskName(
                    "Hmm, nothing wanted to install just now. " +
                        "Give it a minute and try again."
                )
            }
        }
    }

    private fun resolve(call: suspend () -> Result<NameLookupOutcome>) {
        _state.value = UiState.Busy("Looking you up…")
        viewModelScope.launch {
            call().fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is NameLookupOutcome.Found -> prepare(outcome)
                        is NameLookupOutcome.Ambiguous -> _state.value = UiState.WhichOne(outcome.choices)
                        is NameLookupOutcome.NoMatch -> _state.value = UiState.AskName(outcome.message)
                    }
                },
                onFailure = { _state.value = UiState.AskName(it.toFriendlyMessage()) },
            )
        }
    }

    private suspend fun prepare(found: NameLookupOutcome.Found) {
        _state.value = UiState.Busy("Found you! Getting your things ready…")
        installer.plan(found.profileUrl).fold(
            onSuccess = { plan ->
                _state.value = UiState.Ready(found.fullName.ifBlank { plan.profileName }, plan)
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
