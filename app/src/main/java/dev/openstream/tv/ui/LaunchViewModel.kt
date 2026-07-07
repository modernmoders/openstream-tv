package dev.openstream.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.data.SetupConfig
import dev.openstream.tv.data.ViewPrefs
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take

/**
 * Decides where the app opens: a fresh install (nothing installed yet, and
 * this build knows a setup site) starts on the Welcome/Connect screen;
 * everyone else starts Home.
 */
@HiltViewModel
class LaunchViewModel @Inject constructor(
    repository: AddonRepository,
    config: SetupConfig,
    viewPrefs: ViewPrefs,
) : ViewModel() {

    /**
     * null while the Room read is in flight (skip a Home flash — same trick
     * as HomeViewModel). take(1): the decision is made ONCE per process —
     * installing addons mid-Connect must not yank the navigation graph out
     * from under the flow.
     */
    val startOnWelcome: StateFlow<Boolean?> = repository.observeInstalled()
        .map { installed -> config.isConfigured && installed.isEmpty() }
        .take(1)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Read here (not by each screen) because AppNavHost itself needs it for
     * the easy-mode navigation shape (owner 2026-07-06 round 10): movies
     * route through a proper Info screen instead of straight to streams, and
     * Back from the player pops through the stream list instead of stranding
     * the viewer there. Defaults false (safe: expert behavior is a superset,
     * never hides a screen a technical user relies on) until the real
     * DataStore value arrives.
     */
    val expertMode: StateFlow<Boolean> = viewPrefs.expertMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
