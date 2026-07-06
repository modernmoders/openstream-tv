package dev.openstream.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.data.SetupConfig
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
}
