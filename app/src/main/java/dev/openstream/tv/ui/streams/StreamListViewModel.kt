package dev.openstream.tv.ui.streams

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.InstalledAddon
import dev.openstream.tv.addon.Stream
import dev.openstream.tv.addon.StreamRepository
import dev.openstream.tv.addon.Video
import dev.openstream.tv.addon.toPlayableSource
import dev.openstream.tv.autoplay.AutoplayController
import dev.openstream.tv.autoplay.AutoplayOriginHolder
import dev.openstream.tv.autoplay.AutoplayStateMachine
import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExternalOutcome
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.player.PlaybackRequest
import dev.openstream.tv.player.buildExternalLaunch
import dev.openstream.tv.player.interpretExternalResult
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stream list fan-out (§4.1.5): every stream-declaring addon is queried in
 * parallel; each group renders the moment its addon answers — never wait for
 * the slowest addon. Failures are visible chips (§4.1.8).
 *
 * Also hosts §6.2 external playback: "Play with…" (long-press) launches
 * VLC/MX/chooser for one stream, the activity result comes back here for the
 * progress round-trip, and a near-complete return runs the §7.1.6 best-effort
 * Up Next flow with this screen's own AutoplayController.
 */
@HiltViewModel
class StreamListViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
    private val autoplayOrigin: AutoplayOriginHolder,
    private val externalLauncher: ExternalPlayerPort,
    private val autoplay: AutoplayController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val type: String = checkNotNull(savedStateHandle["type"])
    val videoId: String = checkNotNull(savedStateHandle["videoId"])
    val title: String = savedStateHandle["title"] ?: ""

    /** Meta id for progress rows; movies arrive without one — video id IS the meta id. */
    private val metaId: String = savedStateHandle.get<String>("metaId")?.ifBlank { null } ?: videoId
    private val poster: String? = savedStateHandle.get<String>("poster")?.ifBlank { null }

    /** Progress key for this video (§8.4): opaque, addon-kind for now. */
    private val mediaRef = MediaRef.addon(videoId)

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
        /** Non-null = show "Resume from X / Start over" before playing. */
        val resumePositionMs: Long? = null,
        /** Installed external players (§6.2); empty = long-press does nothing extra. */
        val externalPlayers: List<ExternalPlayerPort.Choice> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** §7.1.6 Up Next card state after an external return; null = hidden. */
    val autoplayState: StateFlow<AutoplayStateMachine.State?> = autoplay.state

    /** Intent the screen must fire via its ActivityResult launcher. */
    val launchExternal: SharedFlow<Intent> get() = _launchExternal
    private val _launchExternal = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    /** Autoplay gave up (§7.1 step 4): replace this screen with the next episode's list. */
    data class OpenStreams(val type: String, val videoId: String, val title: String, val metaId: String, val poster: String?)
    val openStreams: SharedFlow<OpenStreams> get() = _openStreams
    private val _openStreams = MutableSharedFlow<OpenStreams>(extraBufferCapacity = 1)

    /** The external launch a result is pending for (player + which episode). */
    private data class PendingExternal(
        val choice: ExternalPlayerPort.Choice,
        val mediaRef: MediaRef,
        val title: String,
    )

    private var pendingExternal: PendingExternal? = null

    /**
     * The player picked for this screen's session. Outlives [pendingExternal]
     * (which clears on each result) so the §7.1.6 binge chain can relaunch
     * the same player for the next episode.
     */
    private var lastExternalChoice: ExternalPlayerPort.Choice? = null

    /**
     * Stage the stream for the internal player screen; returns false for
     * sources v1 can't play (the UI shows those as notes, so this is
     * belt-and-braces).
     */
    fun stage(addon: InstalledAddon, stream: Stream, startPositionMs: Long = 0): Boolean {
        val source = stream.toPlayableSource(title.ifBlank { stream.name ?: "Stream" })
            ?: return false
        currentPlayback.request = PlaybackRequest(
            source = source.copy(startPositionMs = startPositionMs),
            mediaRef = mediaRef,
            metaId = metaId,
            metaType = type,
            poster = poster,
        )
        // Autoplay's tier-1/2 ranking context (§7.1) — which addon, which stream
        autoplayOrigin.origin = StreamCascade.CurrentStream(addon.manifestUrl, stream)
        return true
    }

    /**
     * Build the §6.2 external intent and remember what we launched, so the
     * activity result can be attributed. Null for sources v1 can't play.
     */
    fun externalIntent(
        addon: InstalledAddon,
        stream: Stream,
        choice: ExternalPlayerPort.Choice,
        startPositionMs: Long = 0,
    ): Intent? {
        val playTitle = title.ifBlank { stream.name ?: "Stream" }
        val source = stream.toPlayableSource(playTitle)?.copy(startPositionMs = startPositionMs)
            ?: return null
        pendingExternal = PendingExternal(choice, mediaRef, playTitle)
        lastExternalChoice = choice
        autoplayOrigin.origin = StreamCascade.CurrentStream(addon.manifestUrl, stream)
        return externalLauncher.intentFor(
            buildExternalLaunch(choice.player, choice.packageName, source)
        )
    }

    /** The external activity actually started — an autoplay attempt succeeded (§7.1). */
    fun onExternalLaunched() = autoplay.onPlaybackReady()

    /** ActivityNotFound etc. — during autoplay this is the §7.1 step 5 fallthrough. */
    fun onExternalLaunchFailed() {
        pendingExternal = null
        autoplay.onPlaybackError(viewModelScope)
    }

    /**
     * The §6.2 resume round-trip: map whatever the external player reported
     * back into watch progress; a (near-)complete watch behaves like our own
     * PlayerEvent.Ended — progress cleared, Up Next offered (§7.1.6).
     */
    fun onExternalResult(resultCode: Int, data: Intent?) {
        val pending = pendingExternal ?: return
        pendingExternal = null
        val outcome = interpretExternalResult(
            pending.choice.player, resultCode, externalLauncher.resultExtras(data)
        )
        when (outcome) {
            is ExternalOutcome.Progress -> progressRepository.saveAsync(
                WatchProgress(
                    ref = pending.mediaRef,
                    metaId = metaId,
                    metaType = type,
                    title = pending.title,
                    poster = poster,
                    positionMs = outcome.positionMs,
                    durationMs = outcome.durationMs,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            is ExternalOutcome.Finished -> {
                progressRepository.clearAsync(pending.mediaRef)
                autoplay.onPlaybackEnded(
                    scope = viewModelScope,
                    metaType = type,
                    metaId = metaId,
                    mediaRef = pending.mediaRef,
                    origin = autoplayOrigin.origin,
                )
            }
            // Generic players / cancelled launches tell us nothing — leave
            // stored progress alone rather than guessing.
            is ExternalOutcome.Unknown -> Unit
        }
    }

    /** OK press while the Up Next card is visible. True = consumed. */
    fun confirmAutoplay(): Boolean = autoplay.onConfirm(viewModelScope)

    /** Back press. True = autoplay consumed it (stay on this screen). */
    fun backPressed(): Boolean = autoplay.onBack(viewModelScope)

    /** Autoplay picked a candidate → binge continues in the SAME external player. */
    private fun launchNextExternally(next: Video, candidate: StreamCascade.Candidate) {
        // The chain only runs after an external launch, so a previous choice
        // always exists — reuse the player the user picked for this session.
        val choice = lastExternalChoice ?: return
        val nextTitle = episodeTitle(next)
        val source = candidate.stream.toPlayableSource(nextTitle)
        if (source == null) {
            // Ranked candidates are playable by construction; belt-and-braces.
            autoplay.onPlaybackError(viewModelScope)
            return
        }
        pendingExternal = PendingExternal(choice, MediaRef.addon(next.id), nextTitle)
        autoplayOrigin.origin = StreamCascade.CurrentStream(candidate.addonUrl, candidate.stream)
        _launchExternal.tryEmit(
            externalLauncher.intentFor(
                buildExternalLaunch(choice.player, choice.packageName, source)
            )
        )
    }

    private fun episodeTitle(next: Video): String {
        // Same shape PlayerViewModel uses for internal autoplay titles
        val se = if (next.season != null && next.episode != null) "S${next.season}E${next.episode}" else null
        return listOfNotNull(se, next.displayTitle.takeIf { it.isNotBlank() }).joinToString(" · ")
            .ifBlank { title }
    }

    init {
        _uiState.update { it.copy(externalPlayers = externalLauncher.installedPlayers()) }
        viewModelScope.launch {
            // Collect, don't read once: this ViewModel survives on the back
            // stack while the player advances progress (found in Phase 2 gate).
            progressRepository.observeResumePosition(mediaRef).collect { positionMs ->
                _uiState.update { it.copy(resumePositionMs = positionMs) }
            }
        }
        viewModelScope.launch {
            autoplay.commands.collect { command ->
                when (command) {
                    is AutoplayController.Command.Play ->
                        launchNextExternally(command.next, command.candidate)
                    is AutoplayController.Command.OpenStreamList -> _openStreams.tryEmit(
                        OpenStreams(type, command.next.id, episodeTitle(command.next), metaId, poster)
                    )
                }
            }
        }
        viewModelScope.launch {
            val addons = streamRepository.streamAddons(type, videoId)
            // update{}, not value=: must not clobber the parallel resume-position load
            _uiState.update {
                it.copy(initializing = false, groups = addons.map { a -> GroupState.Loading(a) })
            }
            addons.forEach { addon ->
                launch {
                    val newState = streamRepository.fetch(addon, type, videoId).fold(
                        onSuccess = { GroupState.Loaded(addon, it) },
                        onFailure = { GroupState.Failed(addon, it.toChipMessage()) },
                    )
                    // Atomic: parallel addon completions must not clobber
                    // each other's group updates
                    _uiState.update { state ->
                        state.copy(groups = state.groups.map {
                            if (it.addon.manifestUrl == addon.manifestUrl) newState else it
                        })
                    }
                }
            }
        }
    }

    override fun onCleared() {
        autoplay.stop()
    }
}
