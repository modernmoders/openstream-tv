package dev.openstream.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory ViewPrefs for JVM tests — same contract, no DataStore. */
class FakeViewPrefs : ViewPrefs {
    private val state = MutableStateFlow(DiscoverViewPrefs())
    private val columnsState = MutableStateFlow(DEFAULT_POSTER_COLUMNS)
    private val expertState = MutableStateFlow(false)
    private val numberingState = MutableStateFlow(EpisodeNumbering.SEASONAL)
    private val uiSoundsState = MutableStateFlow(true)

    override val discover: Flow<DiscoverViewPrefs> = state
    override val posterColumns: Flow<Int> = columnsState
    override val expertMode: Flow<Boolean> = expertState
    override val episodeNumbering: Flow<EpisodeNumbering> = numberingState
    override val uiSounds: Flow<Boolean> = uiSoundsState

    override suspend fun setDiscoverColumns(columns: Int) {
        state.update { it.copy(columns = columns) }
    }

    override suspend fun setDiscoverSort(sort: DiscoverSortMode) {
        state.update { it.copy(sort = sort) }
    }

    override suspend fun setDiscoverHideWatched(enabled: Boolean) {
        state.update { it.copy(hideWatched = enabled) }
    }

    override suspend fun setPosterColumns(columns: Int) {
        columnsState.value = columns.coerceIn(MIN_POSTER_COLUMNS, MAX_POSTER_COLUMNS)
    }

    override suspend fun setExpertMode(enabled: Boolean) {
        expertState.value = enabled
    }

    override suspend fun setEpisodeNumbering(mode: EpisodeNumbering) {
        numberingState.value = mode
    }

    override suspend fun setUiSounds(enabled: Boolean) {
        uiSoundsState.value = enabled
    }
}
