package dev.openstream.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.data.DEFAULT_POSTER_COLUMNS
import dev.openstream.tv.data.PLAYER_INTERNAL
import dev.openstream.tv.data.PlaybackPrefs
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.player.ExternalPlayer
import dev.openstream.tv.player.ExternalPlayerPort
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the settings home: poster density + the "Always use" player. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val viewPrefs: ViewPrefs,
    private val playbackPrefs: PlaybackPrefs,
    externalPlayers: ExternalPlayerPort,
) : ViewModel() {

    val posterColumns: StateFlow<Int> = viewPrefs.posterColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_POSTER_COLUMNS)

    val playerPref: StateFlow<String> = playbackPrefs.preferredPlayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PLAYER_INTERNAL)

    /**
     * Concrete players installed right now (§6.2: only show what exists).
     * GENERIC is excluded — "always ask the system chooser" is just Ask.
     */
    val installedPlayers: List<ExternalPlayerPort.Choice> =
        externalPlayers.installedPlayers().filter { it.player != ExternalPlayer.GENERIC }

    fun setPosterColumns(columns: Int) {
        viewModelScope.launch { viewPrefs.setPosterColumns(columns) }
    }

    fun setPlayerPref(value: String) {
        viewModelScope.launch { playbackPrefs.setPreferredPlayer(value) }
    }
}
