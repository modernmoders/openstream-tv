package dev.openstream.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory ViewPrefs for JVM tests — same contract, no DataStore. */
class FakeViewPrefs : ViewPrefs {
    private val state = MutableStateFlow(DiscoverViewPrefs())
    private val columnsState = MutableStateFlow(DEFAULT_POSTER_COLUMNS)

    override val discover: Flow<DiscoverViewPrefs> = state
    override val posterColumns: Flow<Int> = columnsState

    override suspend fun setDiscoverColumns(columns: Int) {
        state.update { it.copy(columns = columns) }
    }

    override suspend fun setDiscoverSort(sort: DiscoverSortMode) {
        state.update { it.copy(sort = sort) }
    }

    override suspend fun setPosterColumns(columns: Int) {
        columnsState.value = columns.coerceIn(MIN_POSTER_COLUMNS, MAX_POSTER_COLUMNS)
    }
}
