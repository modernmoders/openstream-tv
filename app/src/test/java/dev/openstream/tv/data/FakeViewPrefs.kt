package dev.openstream.tv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory ViewPrefs for JVM tests — same contract, no DataStore. */
class FakeViewPrefs : ViewPrefs {
    private val state = MutableStateFlow(DiscoverViewPrefs())

    override val discover: Flow<DiscoverViewPrefs> = state

    override suspend fun setDiscoverColumns(columns: Int) {
        state.update { it.copy(columns = columns) }
    }

    override suspend fun setDiscoverSort(sort: DiscoverSortMode) {
        state.update { it.copy(sort = sort) }
    }
}
