package dev.openstream.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.AddonRepository
import dev.openstream.tv.data.DEFAULT_POSTER_COLUMNS
import dev.openstream.tv.data.EpisodeNumbering
import dev.openstream.tv.data.PLAYER_INTERNAL
import dev.openstream.tv.data.PlaybackPrefs
import dev.openstream.tv.data.ProfileSyncPrefs
import dev.openstream.tv.data.SetupConfig
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.player.ExternalPlayer
import dev.openstream.tv.player.ExternalPlayerPort
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for the settings home: poster density + the "Always use" player. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val viewPrefs: ViewPrefs,
    private val playbackPrefs: PlaybackPrefs,
    private val addonRepository: AddonRepository,
    private val profileSyncPrefs: ProfileSyncPrefs,
    externalPlayers: ExternalPlayerPort,
    setupConfig: SetupConfig,
) : ViewModel() {

    /** "Connect this TV" only exists when this build knows a setup site. */
    val setupConfigured: Boolean = setupConfig.isConfigured
    val brand: String = setupConfig.brand

    /**
     * Whose profile this box is running — so a household with several boxes can
     * tell which account a TV is on without re-running setup (owner 2026-07-10).
     * Blank until the box is connected to a setup link.
     */
    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName

    init {
        viewModelScope.launch { _profileName.value = profileSyncPrefs.get()?.profileName.orEmpty() }
    }

    val posterColumns: StateFlow<Int> = viewPrefs.posterColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_POSTER_COLUMNS)

    /** Expert mode (owner directive 2026-07-06): technical tools stay
     *  invisible until whoever looks after the box flips this. */
    val expertMode: StateFlow<Boolean> = viewPrefs.expertMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Seasonal vs absolute episode numbers (owner request: anime numbering). */
    val episodeNumbering: StateFlow<EpisodeNumbering> = viewPrefs.episodeNumbering
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpisodeNumbering.SEASONAL)

    val playerPref: StateFlow<String> = playbackPrefs.preferredPlayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PLAYER_INTERNAL)

    val autoPlayFirstStream: StateFlow<Boolean> = playbackPrefs.autoPlayFirstStream
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Prefer software video decoding (owner 2026-07-08: fixes blocky/glitchy
     *  picture on some shows). Default OFF — the player's "Having trouble?"
     *  panel flips it on for a glitchy stream without a Settings trip. */
    val preferSoftwareDecoder: StateFlow<Boolean> = playbackPrefs.preferSoftwareDecoder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Anime intro/credits skip button (owner 2026-07-08, AniSkip). Default on;
     *  only ever appears on anime the community has timed. */
    val skipIntrosEnabled: StateFlow<Boolean> = playbackPrefs.skipIntrosEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Auto-skip anime intros, no button press (Round-15 #4). Default OFF. */
    val autoSkipIntros: StateFlow<Boolean> = playbackPrefs.autoSkipIntros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Credits → 5s countdown → next episode (Round-15 #4). Default ON. */
    val autoSkipCredits: StateFlow<Boolean> = playbackPrefs.autoSkipCredits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Subtle focus/select sounds (owner round 10). Default on. */
    val uiSounds: StateFlow<Boolean> = viewPrefs.uiSounds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    fun setAutoPlayFirstStream(enabled: Boolean) {
        viewModelScope.launch { playbackPrefs.setAutoPlayFirstStream(enabled) }
    }

    fun setPreferSoftwareDecoder(enabled: Boolean) {
        viewModelScope.launch { playbackPrefs.setPreferSoftwareDecoder(enabled) }
    }

    fun setSkipIntrosEnabled(enabled: Boolean) {
        viewModelScope.launch { playbackPrefs.setSkipIntrosEnabled(enabled) }
    }

    fun setAutoSkipIntros(enabled: Boolean) {
        viewModelScope.launch { playbackPrefs.setAutoSkipIntros(enabled) }
    }

    fun setAutoSkipCredits(enabled: Boolean) {
        viewModelScope.launch { playbackPrefs.setAutoSkipCredits(enabled) }
    }

    fun setExpertMode(enabled: Boolean) {
        viewModelScope.launch { viewPrefs.setExpertMode(enabled) }
    }

    fun setEpisodeNumbering(mode: EpisodeNumbering) {
        viewModelScope.launch { viewPrefs.setEpisodeNumbering(mode) }
    }

    fun setUiSounds(enabled: Boolean) {
        viewModelScope.launch { viewPrefs.setUiSounds(enabled) }
    }

    /**
     * "Reset this TV" (owner request): forget every installed addon and the
     * saved setup link, so this box goes back to the fresh-install "What's
     * your name?" screen — the same state [dev.openstream.tv.ui.LaunchViewModel]
     * shows a box that has never been set up. Deliberately narrow: poster
     * density, player choice, and other personal preferences are NOT touched —
     * those aren't part of "who is this box", and clearing them would be a
     * surprise nobody asked for.
     */
    fun resetTv(onDone: () -> Unit) {
        viewModelScope.launch {
            addonRepository.uninstallAll()
            profileSyncPrefs.clear()
            onDone()
        }
    }
}
