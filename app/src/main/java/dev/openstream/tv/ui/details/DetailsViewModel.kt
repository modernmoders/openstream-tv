package dev.openstream.tv.ui.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.openstream.tv.addon.MetaItem
import dev.openstream.tv.addon.MetaRepository
import dev.openstream.tv.addon.Video
import dev.openstream.tv.addon.absoluteEpisodeNumbers
import dev.openstream.tv.data.EpisodeNumbering
import dev.openstream.tv.data.ViewPrefs
import dev.openstream.tv.domain.ContentType
import dev.openstream.tv.ui.components.toChipMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val metaRepository: MetaRepository,
    private val viewPrefs: ViewPrefs,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val type: String = checkNotNull(savedStateHandle["type"])
    val id: String = checkNotNull(savedStateHandle["id"])

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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        viewModelScope.launch {
            // The setting is read once at load: it changes rarely and a fresh
            // Details VM is created on every navigation, so re-entry picks up
            // any change made in Settings meanwhile.
            val numbering = viewPrefs.episodeNumbering.first()
            metaRepository.resolveMeta(type, id).fold(
                onSuccess = { meta ->
                    val seasons = meta.videos
                        .mapNotNull { it.season }
                        .distinct()
                        // Season 0 = specials: shown after the real seasons.
                        .sortedWith(compareBy({ it == 0 }, { it }))
                    val first = seasons.firstOrNull()
                    _uiState.value = UiState(
                        loading = false,
                        meta = meta,
                        seasons = seasons,
                        selectedSeason = first,
                        episodesOfSeason = episodesFor(meta, first),
                        numbering = numbering,
                        absoluteNumbers = absoluteEpisodeNumbers(meta.videos),
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
