package dev.openstream.tv.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.addon.InstalledAddon
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AddonManagerViewModel @Inject constructor(
    private val repository: AddonRepository,
) : ViewModel() {

    /** Installed addons in user order; the screen recomposes on every change. */
    val addons: StateFlow<List<InstalledAddon>> = repository.observeInstalled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun uninstall(manifestUrl: String) {
        viewModelScope.launch { repository.uninstall(manifestUrl) }
    }

    fun setEnabled(manifestUrl: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(manifestUrl, enabled) }
    }

    /** Move an addon one position up (-1) or down (+1) in the user's order. */
    fun move(manifestUrl: String, delta: Int) {
        val current = addons.value.map { it.manifestUrl }
        val from = current.indexOf(manifestUrl)
        val to = from + delta
        if (from == -1 || to !in current.indices) return
        val reordered = current.toMutableList().apply {
            removeAt(from)
            add(to, manifestUrl)
        }
        viewModelScope.launch { repository.reorder(reordered) }
    }
}
