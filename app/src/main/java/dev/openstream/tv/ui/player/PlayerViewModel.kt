package dev.openstream.tv.ui.player

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openstream.tv.addon.Video
import dev.openstream.tv.addon.WatchTrackingPing
import dev.openstream.tv.addon.toPlayableSource
import dev.openstream.tv.autoplay.AutoplayController
import dev.openstream.tv.autoplay.AutoplayGateway
import dev.openstream.tv.autoplay.AutoplayOriginHolder
import dev.openstream.tv.autoplay.AutoplayStateMachine
import dev.openstream.tv.autoplay.NextEpisode
import dev.openstream.tv.autoplay.StreamCascade
import dev.openstream.tv.data.PlaybackPrefs
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.diagnostics.DiagnosticsSink
import dev.openstream.tv.data.SUBTITLES_OFF
import dev.openstream.tv.domain.MediaRef
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.player.CurrentPlayback
import dev.openstream.tv.player.ExoPlayerEngine
import dev.openstream.tv.player.ExternalPlayerPort
import dev.openstream.tv.player.PlaybackRequest
import dev.openstream.tv.player.PlaybackService
import dev.openstream.tv.player.PlayerEvent
import dev.openstream.tv.player.PlayerHolder
import dev.openstream.tv.player.StreamAlternatives
import dev.openstream.tv.player.buildExternalLaunch
import dev.openstream.tv.player.TrackKind
import dev.openstream.tv.player.TrackOption
import dev.openstream.tv.player.applyPreferredLanguages
import dev.openstream.tv.player.rememberedLanguage
import dev.openstream.tv.player.skip.AutoSkipAction
import dev.openstream.tv.player.skip.SkipSegment
import dev.openstream.tv.player.skip.SkipTimesRepository
import dev.openstream.tv.player.skip.SkipType
import dev.openstream.tv.player.skip.absoluteEpisodeNumber
import dev.openstream.tv.player.skip.activeSegmentAt
import dev.openstream.tv.player.skip.autoSkipActionFor
import dev.openstream.tv.ui.sound.UiSounds
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

/** How often to check whether playback has entered/left an AniSkip window.
 *  Windows are tens of seconds long, so sub-second precision isn't needed. */
private const val SKIP_POLL_INTERVAL_MS = 500L

/** Consecutive broken streams auto-skipped before giving up with the panel. */
private const val MAX_ERROR_SKIPS = 3

/** Auto-advance on credits: quiet grace before the countdown even appears,
 *  then the countdown length (owner 2026-07-12: 10s later, count from 8).
 *  The countdown total is internal — the Up next card's ring drains against it. */
private const val AUTO_ADVANCE_GRACE_MS = 10_000L
internal const val AUTO_ADVANCE_COUNTDOWN_SECONDS = 8

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val currentPlayback: CurrentPlayback,
    private val progressRepository: ProgressRepository,
    private val autoplay: AutoplayController,
    private val autoplayOrigin: AutoplayOriginHolder,
    private val autoplayGateway: AutoplayGateway,
    private val playbackPrefs: PlaybackPrefs,
    private val alternatives: StreamAlternatives,
    private val externalLauncher: ExternalPlayerPort,
    private val skipTimes: SkipTimesRepository,
    private val watchTrackingPing: WatchTrackingPing,
    playerHolder: PlayerHolder,
    private val uiSounds: UiSounds,
    private val savedState: SavedStateHandle,
    private val diagnostics: DiagnosticsSink = DiagnosticsSink.NONE,
) : ViewModel() {

    /** Installed external players (VLC/MX) for the "Play in another app"
     *  escape — the fix for streams that play wrong in ExoPlayer (no audio,
     *  no video, codec gaps on the 32-bit boxes). Empty = the button hides. */
    val externalPlayers: List<ExternalPlayerPort.Choice> = externalLauncher.installedPlayers()
        .filter { it.packageName != null } // a real app to hand off to

    /**
     * The service-owned engine (§6.1); null until [PlaybackService] is up.
     * The screen binds its PlayerView the moment this becomes non-null.
     */
    val engine: StateFlow<ExoPlayerEngine?> = playerHolder.engine

    data class UiState(
        val title: String = "",
        /** Null = nothing to play (process death) — screen must pop back. */
        val hasSource: Boolean = true,
        /**
         * Set when [hasSource] is false but we know WHAT was playing (saved
         * across process death): the screen re-opens that video through the
         * stream flow — fresh stream, resume prompt — instead of dumping the
         * viewer out of the video (Round 14: "exit the app… stays where you
         * were"). Null = nothing to restore, pop as before.
         */
        val restore: OpenStreams? = null,
        val ended: Boolean = false,
        val error: String? = null,
        /** More streams to walk — shows the "Try another server" affordances. */
        val canTryNext: Boolean = false,
        /** Friendly banner while a server switch spins up (elder rule: no raw errors). */
        val switching: Boolean = false,
        /** The episode the ⏮ button jumps to; null = at the first episode (or a movie). */
        val previousEpisode: EpisodeTarget? = null,
        /** The episode the ⏭ button jumps to; null = at the last episode, or the
         *  episode list hasn't resolved yet (⏭ still shows — see [isSeries]). */
        val nextEpisode: EpisodeTarget? = null,
        /** Series (not a movie): ⏭ is shown for EVERY episode, resolved or not
         *  (owner 2026-07-09) — pressing it resolves on demand, then either opens
         *  the next episode or ends the video. Never a dead button. */
        val isSeries: Boolean = false,
        /** Active anime intro/credits window — the "Skip Intro/Credits" button;
         *  null when not inside one (AniSkip, owner 2026-07-08). */
        val skipSegment: SkipSegment? = null,
        /** Seconds left on the "Next episode in N…" auto-advance countdown
         *  (auto-skip credits, owner Round-15); null = no countdown running. */
        val nextEpisodeCountdown: Int? = null,
        /** Whether the CURRENT playback is using software decoding — the
         *  "Having trouble?" toggle reflects this ON/OFF (owner 2026-07-08). */
        val softwareDecoderOn: Boolean = false,
        /**
         * Non-null = there is saved progress and we haven't decided where to
         * start yet: the player shows a "Resume from X / Start from the
         * beginning" prompt over the loading animation while the stream is
         * tested, and holds playback paused until answered (owner 2026-07-08).
         * Cleared once answered — never shown twice in a session.
         */
        val resumePromptMs: Long? = null,
    )

    /** A neighbouring episode the player's prev/next buttons can open.
     *  [episode]/[name]/[thumbnail] feed the "Up next" card (Round 17). */
    data class EpisodeTarget(
        val videoId: String,
        val title: String,
        val episode: Int? = null,
        val name: String? = null,
        val thumbnail: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** Up Next card state; null = card hidden (§7.1). */
    val autoplayState: StateFlow<AutoplayStateMachine.State?> = autoplay.state

    /** Where to land when autoplay gives up: the next episode's stream list. */
    data class OpenStreams(val type: String, val videoId: String, val title: String, val metaId: String, val poster: String?)
    val openStreams: SharedFlow<OpenStreams> get() = _openStreams
    private val _openStreams = MutableSharedFlow<OpenStreams>(extraBufferCapacity = 1)

    /** var, not val: autoplay replaces the request when it advances episodes. */
    private var request = currentPlayback.request

    /** Auto-play-first-stream is also "auto-skip a broken stream" (owner
     *  request 2026-07-06) — read once; a mid-playback settings change is fine
     *  to pick up next playback. */
    private var autoAdvanceOnError = false

    /** Consecutive error-driven server switches; capped so a dead video id
     *  can't cycle every server forever. A successful Ready resets it. */
    private var errorSkips = 0

    /** Series episode list (binge order source) for the ⏮/⏭ buttons; empty
     *  for movies or until the meta resolves. */
    private var episodeVideos: List<Video> = emptyList()

    /** AniSkip windows for the current episode; empty for non-anime or when the
     *  feature is off. Polled against playback position to raise the button. */
    private var skipSegments: List<SkipSegment> = emptyList()
    private var skipEnabled = false

    /** Auto-skip toggles (owner Round-15 #4), read once at playback start. */
    private var autoSkipIntros = false
    private var autoSkipCredits = false

    /** The countdown job + the windows already auto-handled for THIS video —
     *  cancelling a countdown must not respawn it on the next position poll,
     *  and a re-entered intro window must not re-auto-seek (you'd never be
     *  able to scrub back into an opening). */
    private var autoAdvanceJob: Job? = null
    private var autoHandledSegments = mutableSetOf<SkipSegment>()

    init {
        // No UI ticks over playing video (owner round 10 "subtle" — a held
        // seek would rattle constantly). Lifecycle-matched to this ViewModel.
        uiSounds.suppressed = true
        val req = request
        if (req == null) {
            // Process death mid-playback: CurrentPlayback (a process-scoped
            // holder) is gone, but SavedStateHandle survives. If we stashed
            // what was playing, hand the screen a restore target — it re-opens
            // the video through the stream flow (fresh link, resume prompt).
            val videoId = savedState.get<String>(KEY_RESTORE_VIDEO)
            val restore = if (videoId != null) {
                OpenStreams(
                    type = savedState.get<String>(KEY_RESTORE_TYPE).orEmpty(),
                    videoId = videoId,
                    title = savedState.get<String>(KEY_RESTORE_TITLE).orEmpty(),
                    metaId = savedState.get<String>(KEY_RESTORE_META).orEmpty(),
                    poster = savedState.get<String>(KEY_RESTORE_POSTER),
                )
            } else {
                null
            }
            _uiState.value = UiState(hasSource = false, restore = restore)
        } else {
            req.mediaRef?.let { ref ->
                savedState[KEY_RESTORE_VIDEO] = ref.externalId
                savedState[KEY_RESTORE_TYPE] = req.metaType
                savedState[KEY_RESTORE_TITLE] = req.source.title
                savedState[KEY_RESTORE_META] = req.metaId
                savedState[KEY_RESTORE_POSTER] = req.poster
            }
            // A staged start position means there IS saved progress: ask where
            // to start (over the loading animation) instead of silently jumping.
            val resumeAt = req.source.startPositionMs.takeIf { it > 0 }
            _uiState.value = UiState(
                title = req.source.title,
                canTryNext = alternatives.hasNext(),
                resumePromptMs = resumeAt,
            )
            context.startService(Intent(context, PlaybackService::class.java))
            resolveEpisodeNav(req)
            viewModelScope.launch {
                autoAdvanceOnError = playbackPrefs.autoPlayFirstStream.first()
                skipEnabled = playbackPrefs.skipIntrosEnabled.first()
                autoSkipIntros = playbackPrefs.autoSkipIntros.first()
                autoSkipCredits = playbackPrefs.autoSkipCredits.first()
                val engine = this@PlayerViewModel.engine.filterNotNull().first()
                launch {
                    // The engine decides software-vs-hardware PER STREAM now
                    // (auto for codecs the box mangles) — the "Software video"
                    // toggle mirrors what this playback actually uses, not a
                    // stored setting.
                    engine.usingSoftwareDecoder.collect { on ->
                        _uiState.value = _uiState.value.copy(softwareDecoderOn = on)
                    }
                }
                // Remembered languages (DECISIONS #19) go on BEFORE play so the
                // first track selection already honors them. Parameters stick
                // to the player instance, so autoplay episode swaps keep them.
                // Default the audio track to English when the household hasn't
                // picked a language yet (owner 2026-07-08: a dual-audio anime
                // opened in Italian because it was first in the file). This is
                // a PREFERENCE, not a filter — a foreign-only title still plays,
                // and a saved pick (DECISIONS #19) always wins over this default.
                val languages = playbackPrefs.languages.first()
                engine.exoPlayer.applyPreferredLanguages(languages.audio ?: "en", languages.subtitle)
                engine.play(req.source)
                pingWatchTracking()
                // Resume prompt pending → buffer/test the stream but hold it
                // paused (no surprise audio) until the viewer picks resume or
                // start-over. play() prepared at the saved position already, so
                // "resume" is just letting it go; "start over" seeks to 0 first.
                if (_uiState.value.resumePromptMs != null) engine.exoPlayer.playWhenReady = false
                launch { collectPlayerEvents(engine) }
                launch { collectAutoplayCommands(engine) }
                launch {
                    // Crash/kill loses at most this interval of progress.
                    while (true) {
                        delay(PROGRESS_SAVE_INTERVAL_MS)
                        persistProgress(engine)
                    }
                }
                launch {
                    // Raise/lower the Skip button as playback crosses an
                    // AniSkip window. Cheap no-op until segments are loaded.
                    while (true) {
                        delay(SKIP_POLL_INTERVAL_MS)
                        updateSkipSegment(engine)
                    }
                }
            }
        }
    }

    private suspend fun collectPlayerEvents(engine: ExoPlayerEngine) {
        engine.events.collect { event ->
            val req = request ?: return@collect
            when (event) {
                is PlayerEvent.Ready -> {
                    errorSkips = 0
                    _uiState.value = _uiState.value.copy(switching = false)
                    autoplay.onPlaybackReady()
                }
                is PlayerEvent.Ended -> {
                    // Finished: record it as watched (position == duration) so
                    // the Details episode list can show a ✓ and Continue
                    // Watching still drops it (isResumable's 95% upper bound).
                    // Persist BEFORE flipping `ended` — persistProgress no-ops
                    // once ended, and it's the same key so it just overwrites.
                    markWatched(engine, req)
                    _uiState.value = _uiState.value.copy(ended = true)
                    autoplay.onPlaybackEnded(
                        scope = viewModelScope,
                        metaType = req.metaType,
                        metaId = req.metaId,
                        mediaRef = req.mediaRef,
                        origin = autoplayOrigin.origin,
                    )
                }
                is PlayerEvent.Error -> {
                    // Every playback failure lands in the log with its raw
                    // code/cause (§10 "log them") — the codec detail the
                    // 32-bit boxes' failures need, without a logcat hookup.
                    diagnostics.record(
                        "player",
                        "\"${_uiState.value.title}\": ${event.message}" +
                            event.detail.takeIf { it.isNotBlank() }?.let { " [$it]" }.orEmpty(),
                    )
                    when {
                        // While autoplay is attempting, an open failure is its
                        // fallthrough signal (§7.1 step 5), not an error panel.
                        autoplay.onPlaybackError(viewModelScope) -> Unit
                        // A decoder-class failure on a hardware session gets ONE
                        // same-stream retry in software (same position) before
                        // any stream-walking — the stream is usually fine, the
                        // box's decoder is what broke (MX-parity logic).
                        event.isDecodeError && retryCurrentInSoftware(engine) -> Unit
                        // Auto-skip a broken stream to the next server (owner
                        // request 2026-07-06) — quietly, like a human would.
                        autoAdvanceOnError && errorSkips < MAX_ERROR_SKIPS && tryNextStream() ->
                            errorSkips++
                        else -> _uiState.value =
                            _uiState.value.copy(error = event.message, switching = false)
                    }
                }
            }
        }
    }

    private suspend fun collectAutoplayCommands(engine: ExoPlayerEngine) {
        autoplay.commands.collect { command ->
            when (command) {
                is AutoplayController.Command.Play -> playNext(engine, command.next, command.candidate)
                is AutoplayController.Command.OpenStreamList -> {
                    val req = request ?: return@collect
                    _openStreams.tryEmit(
                        OpenStreams(req.metaType, command.next.id, episodeTitle(command.next), req.metaId, req.poster)
                    )
                }
            }
        }
    }

    private fun playNext(engine: ExoPlayerEngine, next: Video, candidate: StreamCascade.Candidate) {
        val req = request ?: return
        val source = candidate.stream.toPlayableSource(episodeTitle(next))
        if (source == null) {
            // Ranked candidates are playable by construction; belt-and-braces.
            autoplay.onPlaybackError(viewModelScope)
            return
        }
        val newRequest = PlaybackRequest(
            source = source,
            mediaRef = MediaRef.addon(next.id),
            metaId = req.metaId,
            metaType = req.metaType,
            poster = req.poster,
        )
        request = newRequest
        currentPlayback.request = newRequest
        autoplayOrigin.origin = StreamCascade.CurrentStream(candidate.addonUrl, candidate.stream)
        // The alternatives list belonged to the finished episode; the new
        // one's list is unknown until its own stream screen loads it.
        alternatives.clear()
        _uiState.value = _uiState.value.copy(
            title = source.title, ended = false, error = null, canTryNext = false,
        )
        engine.play(source)
        pingWatchTracking() // new episode → a fresh Trakt check-in
        updateEpisodeNav() // autoplay advanced an episode — refresh ⏮/⏭ targets
        loadSkipSegments() // …and its intro/credits windows
    }

    /**
     * Resolve the series' episode list once so the player's ⏮/⏭ buttons know
     * their neighbours. Silent no-op for movies or an unresolvable meta — the
     * buttons simply stay hidden, exactly as autoplay stands down (§7.1).
     */
    private fun resolveEpisodeNav(req: PlaybackRequest) {
        val series = req.metaType == "series"
        _uiState.value = _uiState.value.copy(isSeries = series)
        if (!series) return
        val ref = req.mediaRef ?: return
        if (ref.sourceKind != MediaRef.KIND_ADDON) return
        viewModelScope.launch {
            val meta = autoplayGateway.resolveMeta(req.metaType, req.metaId) ?: return@launch
            episodeVideos = meta.videos
            updateEpisodeNav()
            loadSkipSegments()
        }
    }

    /**
     * Fetch the AniSkip intro/credits windows for the current episode. Series
     * only, feature-gated. The episode number comes from the resolved Video
     * (reliable) and falls back to the trailing id number. Any miss just leaves
     * no segments — no button, never an error.
     */
    private fun loadSkipSegments() {
        val req = request ?: return
        skipSegments = emptyList()
        autoHandledSegments = mutableSetOf() // fresh episode, fresh auto-skips
        cancelAutoAdvance()
        _uiState.value = _uiState.value.copy(skipSegment = null)
        if (!skipEnabled || req.metaType != "series") return
        val videoId = req.mediaRef?.externalId ?: return
        val video = episodeVideos.firstOrNull { it.id == videoId }
        val episode = video?.episode ?: episodeNumberFromVideoId(videoId) ?: return
        // Season + absolute position feed the IMDb→MAL bridge; both are
        // best-effort (null just narrows how much the resolver can map).
        val season = video?.season ?: seasonNumberFromVideoId(videoId)
        val absolute = season?.let { absoluteEpisodeNumber(episodeVideos, it, episode) }
        viewModelScope.launch {
            val duration = engine.value?.exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
            skipSegments = skipTimes.segmentsFor(req.metaId, season, episode, absolute, duration)
        }
    }

    /** Episode number from an id like "tt123:1:6" or "kitsu:99:6" (needs ≥3
     *  parts so a bare "kitsu:99" is never mistaken for episode 99). */
    private fun episodeNumberFromVideoId(id: String): Int? {
        val parts = id.split(':')
        return if (parts.size >= 3) parts.last().toIntOrNull() else null
    }

    /** Season from an IMDb-style id "tt123:1:6" ONLY — a kitsu:/mal: id's
     *  middle part is the catalog id, not a season, so it must never match. */
    private fun seasonNumberFromVideoId(id: String): Int? {
        val parts = id.split(':')
        return if (parts.size >= 3 && parts[0].startsWith("tt")) {
            parts[parts.size - 2].toIntOrNull()
        } else {
            null
        }
    }

    /** Main-thread position poll → the active skip window (or null). Also the
     *  trigger for the auto-skip actions (owner Round-15 #4). */
    private fun updateSkipSegment(engine: ExoPlayerEngine) {
        if (skipSegments.isEmpty()) return
        if (_uiState.value.ended) return
        val active = activeSegmentAt(engine.exoPlayer.currentPosition, skipSegments)
        if (active != _uiState.value.skipSegment) {
            _uiState.value = _uiState.value.copy(skipSegment = active)
            // Leaving a credits window (scrub, or the trim ending it) takes
            // its countdown with it.
            if (active?.type != SkipType.CREDITS) cancelAutoAdvance()
            if (active != null && autoHandledSegments.add(active)) {
                when (autoSkipActionFor(active.type, autoSkipIntros, autoSkipCredits)) {
                    AutoSkipAction.SEEK_PAST -> {
                        engine.exoPlayer.seekTo(active.endMs)
                        _uiState.value = _uiState.value.copy(skipSegment = null)
                    }
                    AutoSkipAction.COUNTDOWN_TO_NEXT -> startNextEpisodeCountdown()
                    AutoSkipAction.NONE -> Unit
                }
            }
        }
    }

    /** Auto-advance (owner 2026-07-12 timing): let the ending BREATHE for 10
     *  seconds first, then "Next episode in 8…" one second per tick, then
     *  advance. BACK cancels ([cancelNextEpisodeCountdown]); the window stays
     *  auto-handled so the countdown doesn't respawn inside the credits. */
    private fun startNextEpisodeCountdown() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = viewModelScope.launch {
            delay(AUTO_ADVANCE_GRACE_MS)
            for (remaining in AUTO_ADVANCE_COUNTDOWN_SECONDS downTo 1) {
                _uiState.value = _uiState.value.copy(nextEpisodeCountdown = remaining)
                delay(1_000)
            }
            _uiState.value = _uiState.value.copy(nextEpisodeCountdown = null)
            advanceToNextEpisode()
        }
    }

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        if (_uiState.value.nextEpisodeCountdown != null) {
            _uiState.value = _uiState.value.copy(nextEpisodeCountdown = null)
        }
    }

    /** BACK during the countdown: keep watching the credits. */
    fun cancelNextEpisodeCountdown(): Boolean {
        if (_uiState.value.nextEpisodeCountdown == null) return false
        cancelAutoAdvance()
        return true
    }

    /** Credits exit (button or countdown): stamp the episode watched — the
     *  credits sit past the 95% line anyway, this just makes the ✓ and Trakt
     *  state unambiguous — then ride the existing ⏭ path. */
    private fun advanceToNextEpisode() {
        engine.value?.let { eng -> request?.let { req -> markWatched(eng, req) } }
        _uiState.value = _uiState.value.copy(skipSegment = null)
        goToNextEpisode()
    }

    /** Resume prompt → "Resume": drop the prompt and let the (already prepared
     *  at the saved position) playback go. */
    fun resumeFromSaved() {
        if (_uiState.value.resumePromptMs == null) return
        _uiState.value = _uiState.value.copy(resumePromptMs = null)
        engine.value?.exoPlayer?.playWhenReady = true
    }

    /** Resume prompt → "Start from the beginning": seek to 0, drop the prompt,
     *  play. The seek re-buffers, so the loading animation carries over until
     *  the fresh position renders. */
    fun startFromBeginning() {
        if (_uiState.value.resumePromptMs == null) return
        _uiState.value = _uiState.value.copy(resumePromptMs = null)
        engine.value?.exoPlayer?.let {
            it.seekTo(0)
            it.playWhenReady = true
        }
    }

    /** OK on the Skip button. Intro: jump past the window. Credits: this is
     *  the "Next Episode" button now (owner Round-15 #3) — mark watched and
     *  advance instead of seeking into the last seconds of the episode. */
    fun skipCurrentSegment() {
        val segment = _uiState.value.skipSegment ?: return
        cancelAutoAdvance()
        when (segment.type) {
            SkipType.INTRO -> {
                engine.value?.exoPlayer?.seekTo(segment.endMs)
                _uiState.value = _uiState.value.copy(skipSegment = null)
            }
            SkipType.CREDITS -> advanceToNextEpisode()
        }
    }

    private fun updateEpisodeNav() {
        val currentId = request?.mediaRef?.externalId ?: return
        if (episodeVideos.isEmpty()) return
        val prev = NextEpisode.previousBefore(episodeVideos, currentId)
        val next = NextEpisode.nextAfter(episodeVideos, currentId)
        _uiState.value = _uiState.value.copy(
            previousEpisode = prev?.let { it.toEpisodeTarget() },
            nextEpisode = next?.let { it.toEpisodeTarget() },
        )
    }

    /**
     * ⏭ : skip straight to the next episode — no Up Next countdown, no stream
     * list detour: [openEpisode] hands off to the next episode's stream list,
     * which auto-picks the best (cached, hardware-decodable) stream.
     *
     * The button shows for EVERY episode of a series (owner 2026-07-09), so the
     * neighbour may not be resolved yet when it's pressed. Resolve on demand,
     * then open it — or, when this really is the last episode, just end the
     * current video (the natural ended flow) rather than doing nothing.
     */
    fun goToNextEpisode() {
        _uiState.value.nextEpisode?.let { openEpisode(it); return }
        if (!_uiState.value.isSeries) return
        val req = request ?: return
        viewModelScope.launch {
            if (episodeVideos.isEmpty()) {
                autoplayGateway.resolveMeta(req.metaType, req.metaId)?.let { meta ->
                    episodeVideos = meta.videos
                    updateEpisodeNav()
                }
            }
            _uiState.value.nextEpisode?.let { openEpisode(it) } ?: endCurrentVideo()
        }
    }

    /**
     * Tell subtitle-declaring addons (AIOMetadata) that this video started, so
     * their "Watch Tracking → Trakt Check-in" fires. Guarded per video id: a
     * "Try a different stream" swap re-plays the SAME episode and must not
     * check in twice (owner 2026-07-09).
     */
    private var pingedVideoId: String? = null

    private fun pingWatchTracking() {
        val req = request ?: return
        val videoId = req.mediaRef?.externalId ?: return
        if (pingedVideoId == videoId) return
        pingedVideoId = videoId
        watchTrackingPing.playbackStarted(req.metaType, videoId)
    }

    /** No next episode: run the natural end-of-video flow (the ended panel). */
    private fun endCurrentVideo() {
        val player = engine.value?.exoPlayer ?: return
        val duration = player.duration
        if (duration > 0) player.seekTo(duration)
    }

    fun goToPreviousEpisode() = openEpisode(_uiState.value.previousEpisode)

    private fun openEpisode(target: EpisodeTarget?) {
        val req = request ?: return
        target ?: return
        _openStreams.tryEmit(OpenStreams(req.metaType, target.videoId, target.title, req.metaId, req.poster))
    }

    /**
     * Walk to the next stream for the SAME video (owner request 2026-07-06):
     * the "Try another server" button, and the quiet auto-skip on errors.
     * Playback continues from where the broken stream left off. False when
     * the list is exhausted — the caller falls back to the error panel.
     */
    fun tryNextStream(preferDifferent: Boolean = false): Boolean {
        val req = request ?: return false
        val engine = engine.value ?: return false
        // The stream being abandoned — the manual button skips its same-
        // release siblings first (a glitched encode's twin glitches too);
        // the quiet error auto-skip keeps the plain ranked order.
        val abandoned = if (preferDifferent) autoplayOrigin.origin?.stream else null
        while (true) {
            val alt = alternatives.advance(preferDifferentFrom = abandoned) ?: return false
            val source = alt.stream.toPlayableSource(req.source.title) ?: continue
            // Mid-play death keeps the position; an open failure has none, so
            // the original start (e.g. the auto-resume point) carries over.
            val position = engine.exoPlayer.currentPosition.takeIf { it > 0 }
                ?: req.source.startPositionMs
            val newRequest = req.copy(source = source.copy(startPositionMs = position))
            request = newRequest
            currentPlayback.request = newRequest
            autoplayOrigin.origin = StreamCascade.CurrentStream(alt.addonUrl, alt.stream)
            _uiState.value = _uiState.value.copy(
                error = null, ended = false,
                switching = true, canTryNext = alternatives.hasNext(),
            )
            engine.play(newRequest.source)
            // Broke DURING the resume prompt (first link failed to test): keep
            // the replacement held paused so the prompt still governs where the
            // working stream starts, instead of blaring audio behind the prompt.
            if (_uiState.value.resumePromptMs != null) engine.exoPlayer.playWhenReady = false
            return true
        }
    }

    private fun episodeTitle(next: Video): String {
        val se = if (next.season != null && next.episode != null) "Season ${next.season} · Episode ${next.episode}" else null
        return listOfNotNull(se, next.displayTitle.takeIf { it.isNotBlank() }).joinToString(" · ")
            .ifBlank { request?.source?.title.orEmpty() }
    }

    private fun Video.toEpisodeTarget() = EpisodeTarget(
        videoId = id,
        title = episodeTitle(this),
        episode = episode,
        name = displayTitle.takeIf { it.isNotBlank() && it != id },
        thumbnail = thumbnail,
    )

    /**
     * Remember a track pick as the preferred language (DECISIONS #19).
     * Tag-less tracks change nothing — a stored preference survives them.
     */
    fun rememberTrackPick(option: TrackOption) {
        val language = rememberedLanguage(option) ?: return
        viewModelScope.launch {
            when (option.kind) {
                TrackKind.AUDIO -> playbackPrefs.setAudioLanguage(language)
                TrackKind.SUBTITLE -> playbackPrefs.setSubtitleLanguage(language)
            }
        }
    }

    /** Subtitles Off is itself a preference: stay off next playback. */
    fun rememberSubtitlesOff() {
        viewModelScope.launch { playbackPrefs.setSubtitleLanguage(SUBTITLES_OFF) }
    }

    /** OK press while the Up Next card is visible. True = consumed. */
    fun confirmAutoplay(): Boolean = autoplay.onConfirm(viewModelScope)

    /** Back press. True = autoplay consumed it (stay on the player screen). */
    fun backPressed(): Boolean = autoplay.onBack(viewModelScope)

    /**
     * Stamp the finished episode/movie as watched (position == duration) so a
     * ✓ survives in Details. If the duration never became known (rare — Ended
     * without a prepared timeline) fall back to clearing, the old behavior, so
     * a durationless row can't wedge Continue Watching. Main-thread only.
     */
    private fun markWatched(engine: ExoPlayerEngine, req: PlaybackRequest) {
        val ref = req.mediaRef ?: return
        val duration = engine.exoPlayer.duration
        if (duration <= 0) {
            progressRepository.clearAsync(ref)
            return
        }
        progressRepository.saveAsync(
            WatchProgress(
                ref = ref,
                metaId = req.metaId,
                metaType = req.metaType,
                title = req.source.title,
                poster = req.poster,
                positionMs = duration,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Snapshot the player position into the progress table. Main-thread only
     * (ExoPlayer contract); the DB write itself hops to IO inside the repo.
     */
    private fun persistProgress(engine: ExoPlayerEngine) {
        val req = request ?: return
        val ref = req.mediaRef ?: return
        if (_uiState.value.ended) return
        val duration = engine.exoPlayer.duration
        val position = engine.exoPlayer.currentPosition
        // duration is TIME_UNSET until prepared; skip until it's real.
        if (duration <= 0 || position <= 0) return
        progressRepository.saveAsync(
            WatchProgress(
                ref = ref,
                metaId = req.metaId,
                metaType = req.metaType,
                title = req.source.title,
                poster = req.poster,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * The player's one "get me out of this stream" action (owner report:
     * hiding the button when the ranked cascade is exhausted left focus
     * landing on the unrelated "Play in another app" instead). Walks to the
     * next candidate if any remain (fast, same position); otherwise opens
     * the full stream list for THIS video so there's always somewhere useful
     * to go, never a dead/hidden button.
     */
    fun tryAnotherStream() {
        if (!tryNextStream(preferDifferent = true)) openCurrentStreamList()
    }

    private fun openCurrentStreamList() {
        val req = request ?: return
        val videoId = req.mediaRef?.externalId ?: return
        _openStreams.tryEmit(OpenStreams(req.metaType, videoId, req.source.title, req.metaId, req.poster))
    }

    /** The one stream URL already retried in software — a decode retry that
     *  fails again must fall through to try-another-stream, not loop. */
    private var softwareRetriedUrl: String? = null

    /**
     * Decode-class failure: replay the SAME stream from the same position with
     * software decoders. Once per stream URL; false = let the caller fall
     * through to the auto-skip / error panel.
     */
    private fun retryCurrentInSoftware(engine: ExoPlayerEngine): Boolean {
        val req = request ?: return false
        if (engine.usingSoftwareDecoder.value) return false // already software
        if (softwareRetriedUrl == req.source.url) return false
        softwareRetriedUrl = req.source.url
        diagnostics.record("player", "decode failure — retrying \"${req.source.title}\" with software decoders")
        engine.setSoftwareOverride(true)
        replayCurrent(engine)
        return true
    }

    /** Re-open the current source in place, keeping the playback position. */
    private fun replayCurrent(engine: ExoPlayerEngine) {
        val req = request ?: return
        val position = engine.exoPlayer.currentPosition.takeIf { it > 0 }
            ?: req.source.startPositionMs
        val newRequest = req.copy(source = req.source.copy(startPositionMs = position))
        request = newRequest
        currentPlayback.request = newRequest
        _uiState.value = _uiState.value.copy(error = null, ended = false, switching = true)
        engine.play(newRequest.source)
        // Mid-resume-prompt: keep the reloaded stream held paused so the
        // prompt still governs where playback starts.
        if (_uiState.value.resumePromptMs != null) engine.exoPlayer.playWhenReady = false
    }

    /**
     * "Having trouble?" software-decoder toggle — now applies IN PLACE: set the
     * session override and replay this stream at the current position (the
     * engine's decoder selector is consulted per playback, alpha.40). The
     * box-level setting is still persisted so the choice outlives the session,
     * matching the old semantics.
     */
    fun toggleSoftwareDecoder() {
        val engine = engine.value ?: return
        val turnOn = !engine.usingSoftwareDecoder.value
        engine.setSoftwareOverride(turnOn)
        replayCurrent(engine)
        viewModelScope.launch { playbackPrefs.setPreferSoftwareDecoder(turnOn) }
    }

    fun retry() {
        val req = request ?: return
        val engine = engine.value ?: return
        _uiState.value = _uiState.value.copy(error = null, ended = false)
        engine.play(req.source)
    }

    /**
     * Hand the CURRENT stream to VLC/MX at the current position ("Play in
     * another app"). We pause our own engine first so two players don't fight
     * over the audio. Null if nothing is playing.
     */
    fun externalIntentForCurrent(choice: ExternalPlayerPort.Choice): Intent? {
        val req = request ?: return null
        val position = engine.value?.exoPlayer?.currentPosition?.takeIf { it > 0 }
            ?: req.source.startPositionMs
        engine.value?.exoPlayer?.pause()
        val source = req.source.copy(startPositionMs = position)
        return externalLauncher.intentFor(
            buildExternalLaunch(choice.player, choice.packageName, source)
        )
    }

    override fun onCleared() {
        uiSounds.suppressed = false
        autoplay.stop()
        // Exit position — the one users actually resume from.
        engine.value?.let { persistProgress(it) }
        // Back from the player means stop (TV UX): tear the service down,
        // which releases the session and the engine.
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    private companion object {
        // SavedStateHandle keys for the process-death restore target.
        const val KEY_RESTORE_VIDEO = "restore.videoId"
        const val KEY_RESTORE_TYPE = "restore.type"
        const val KEY_RESTORE_TITLE = "restore.title"
        const val KEY_RESTORE_META = "restore.metaId"
        const val KEY_RESTORE_POSTER = "restore.poster"
    }
}
