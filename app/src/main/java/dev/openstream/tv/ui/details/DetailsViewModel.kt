package dev.openstream.tv.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.MetaRepository
import dev.openstream.tv.addon.Video
import dev.openstream.tv.addon.absoluteEpisodeNumbers
import dev.openstream.tv.autoplay.NextEpisode
import dev.openstream.tv.data.EpisodeNumbering
import dev.openstream.tv.data.ProgressRepository
import dev.openstream.tv.data.SeriesWatchRepository
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.domain.ContentType
import dev.openstream.tv.domain.WatchProgress
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val metaRepository: MetaRepository,
    private val viewPrefs: ViewPrefs,
    progressRepository: ProgressRepository,
    private val seriesWatchRepository: SeriesWatchRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val type: String = checkNotNull(savedStateHandle["type"])
    val id: String = checkNotNull(savedStateHandle["id"])

    /**
     * Per-episode watch progress, keyed by video id, for the ✓/progress-bar on
     * each episode row. A hot flow off the DB so backing out of the player
     * updates the marks live (this screen survives on the back stack). Keyed by
     * externalId, which for an episode is its video id (§8.4).
     */
    val episodeProgress: StateFlow<Map<String, WatchProgress>> =
        progressRepository.observeProgressByExternalId()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    data class UiState(
        val loading: Boolean = true,
        val meta: MetaItem? = null,
        val error: String? = null,
        /** Season numbers in display order; specials (season 0) go last. */
        val seasons: List<Int> = emptyList(),
        val selectedSeason: Int? = null,
        val episodesOfSeason: List<Video> = emptyList(),
        /** Viewer's numbering choice, read once when the meta loads. */
        val numbering: EpisodeNumbering = EpisodeNumbering.SEASONAL,
        /** video id -> absolute episode number, for ABSOLUTE numbering. */
        val absoluteNumbers: Map<String, Int> = emptyMap(),
        /**
         * Episode to jump to on entry when the viewer has history for this
         * series (owner 2026-07-08: "open Naruto → land on the episode I
         * stopped on"). [selectedSeason] is already set to its season; the
         * screen focuses + scrolls to it. Null = no history, start at the top.
         */
        val resumeVideoId: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            // The setting is read once at load: it changes rarely and a fresh
            // Details VM is created on every navigation, so re-entry picks up
            // any change made in Settings meanwhile.
            val numbering = viewPrefs.episodeNumbering.first()
            // Snapshot of watch history (not the live flow): the resume target
            // is decided once, at open, so browsing seasons later never yanks
            // the selection around.
            val progress = progressRepository.observeProgressByExternalId().first()
            metaRepository.resolveMeta(type, id).fold(
                onSuccess = { meta ->
                    // Feed the browse tiles' "N of M episodes" display: this
                    // is the one place the full episode list is guaranteed to
                    // pass through (Round 17 series-watch cache).
                    if (meta.videos.isNotEmpty()) {
                        seriesWatchRepository.recordEpisodeCount(type, id, meta.videos.size)
                    }
                    val seasons = meta.videos
                        .mapNotNull { it.season }
                        .distinct()
                        // Season 0 = specials: shown after the real seasons.
                        .sortedWith(compareBy({ it == 0 }, { it }))
                    val resume = resumeTarget(meta, progress)
                    // Open on the resume episode's season when there is one.
                    val season = resume?.season ?: seasons.firstOrNull()
                    _uiState.value = UiState(
                        loading = false,
                        meta = meta,
                        seasons = seasons,
                        selectedSeason = season,
                        episodesOfSeason = episodesFor(meta, season),
                        numbering = numbering,
                        absoluteNumbers = absoluteEpisodeNumbers(meta.videos),
                        resumeVideoId = resume?.id,
                    )
                },
                onFailure = { e ->
                    _uiState.value = UiState(loading = false, error = e.toChipMessage())
                },
            )
        }
    }

    fun selectSeason(season: Int) {
        val meta = _uiState.value.meta ?: return
        _uiState.value = _uiState.value.copy(
            selectedSeason = season,
            episodesOfSeason = episodesFor(meta, season),
        )
    }

    /**
     * The episode to land on given watch history: the most recently watched
     * episode of THIS series, or — if that one's finished — the next episode
     * (so "continue" lands on what's actually up next, not a re-watch). Null
     * when nothing here has been watched yet.
     */
    private fun resumeTarget(meta: MetaItem, progress: Map<String, WatchProgress>): Video? {
        val lastWatched = meta.videos
            .filter { progress.containsKey(it.id) }
            .maxByOrNull { progress.getValue(it.id).updatedAt }
            ?: return null
        val done = ProgressRepository.isWatched(progress.getValue(lastWatched.id))
        return if (done) NextEpisode.nextAfter(meta.videos, lastWatched.id) ?: lastWatched
        else lastWatched
    }

    private fun episodesFor(meta: MetaItem, season: Int?): List<Video> =
        when {
            season != null ->
                meta.videos.filter { it.season == season }.sortedBy { it.episode ?: 0 }
            // Channels/series without season numbers: keep the addon's order.
            meta.videos.isNotEmpty() -> meta.videos
            else -> emptyList()
        }

    /** Movies have one implicit video whose id equals the meta id (spec). */
    val isSingleVideo: Boolean
        get() = _uiState.value.meta?.let {
            it.videos.isEmpty() && ContentType.from(it.type) == ContentType.MOVIE
        } ?: false
}
